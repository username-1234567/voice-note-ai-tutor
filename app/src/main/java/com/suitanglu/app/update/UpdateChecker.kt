package com.suitanglu.app.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 应用内更新：访问 GitHub Releases（公开仓库，无需鉴权）。
 * 发布流程：推送代码 → 打 tag vX.Y.Z → CI 自动创建 Release 并上传 APK。
 */
object UpdateConfig {
    const val OWNER = "username-1234567"
    const val REPO = "voice-note-ai-tutor"

    @Volatile
    private var installedVersion: String? = null

    /**
     * 当前安装版本，运行期从 PackageManager 读取。
     *
     * 原来这里是硬编码的 `const val "1.5.0"`，和 build.gradle.kts 的 versionName
     * 是两处独立维护的：发版时改了 gradle 忘了改这里，App 会一直认为自己是旧版，
     * 于是每次启动都提示"有新版本"，而装完仍然是同一个版本——典型的活锁。
     * 读系统里真实的 versionName 就不存在不同步的可能。
     *
     * 读不到时退回 "0.0.0"：宁可多提示一次更新，也不要因为版本号缺失而永不提示。
     */
    val CURRENT_VERSION: String get() = installedVersion ?: "0.0.0"

    fun readInstalledVersion(context: Context): String = runCatching {
        val pm = context.packageManager
        val info = if (android.os.Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
        info.versionName
    }.getOrNull()?.takeIf { it.isNotBlank() }?.also { installedVersion = it } ?: CURRENT_VERSION
}

data class UpdateInfo(
    val tagName: String,
    val releaseName: String,
    val changelog: String,
    val apkName: String,
    val apkUrl: String,
    val apkSize: Long,
) {
    val version: String get() = tagName.removePrefix("v").removePrefix("V")
    fun isNewerThan(current: String): Boolean = compareVersions(version, current) > 0

    companion object {
        fun compareVersions(a: String, b: String): Int {
            val pa = a.split('.').map { it.trim().toIntOrNull() ?: 0 }
            val pb = b.split('.').map { it.trim().toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val x = pa.getOrElse(i) { 0 }
                val y = pb.getOrElse(i) { 0 }
                if (x != y) return x - y
            }
            return 0
        }
    }
}

sealed class UpdateCheckResult {
    data class UpToDate(val current: String) : UpdateCheckResult()
    data class Available(val info: UpdateInfo) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

class UpdateChecker(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    init {
        // 构造时读一次真实版本号，之后所有比较都基于它
        UpdateConfig.readInstalledVersion(context)
    }

    private fun downloadClient(): OkHttpClient = client.newBuilder()
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/${UpdateConfig.OWNER}/${UpdateConfig.REPO}/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()
            // 用普通 client 而不是 downloadClient：查询只是几百字节的 JSON，
            // 借用 5 分钟读超时的下载客户端，网络差时会卡到用户以为"点了没反应"
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.code == 404) {
                    // 404 有两种可能：仓库不存在/已改名，或还没有任何 Release。
                    // 原来一律当成"已是最新"，等于把配置错误彻底藏起来——
                    // 仓库改名后不会有任何迹象，检查静默失效也无人察觉。
                    return@use UpdateCheckResult.Error("未找到 Release（HTTP 404），请到 GitHub 确认仓库状态")
                }
                if (!resp.isSuccessful) {
                    return@use UpdateCheckResult.Error("查询失败 HTTP ${resp.code}: ${body.take(200)}")
                }
                val obj = json.parseToJsonElement(body).jsonObject
                val tagName = obj["tag_name"]?.jsonPrimitive?.content ?: ""
                val assets = obj["assets"]?.jsonArray.orEmpty()
                val apk = assets.mapNotNull { a ->
                    val o = a.jsonObject
                    val name = o["name"]?.jsonPrimitive?.content ?: ""
                    if (name.endsWith(".apk")) {
                        UpdateInfo(
                            tagName = tagName,
                            releaseName = obj["name"]?.jsonPrimitive?.content ?: tagName,
                            changelog = obj["body"]?.jsonPrimitive?.content.orEmpty(),
                            apkName = name,
                            apkUrl = o["browser_download_url"]?.jsonPrimitive?.content ?: "",
                            apkSize = o["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                        )
                    } else null
                }.firstOrNull()
                if (apk == null) {
                    UpdateCheckResult.Error("最新 Release 中未找到 APK 安装包")
                } else if (apk.isNewerThan(UpdateConfig.CURRENT_VERSION)) {
                    UpdateCheckResult.Available(apk)
                } else {
                    UpdateCheckResult.UpToDate(UpdateConfig.CURRENT_VERSION)
                }
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    /** 下载 APK（带 Authorization），返回本地文件 */
    suspend fun download(info: UpdateInfo, onProgress: (Int) -> Unit): Result<File> = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "updates").apply { mkdirs() }
            // 清理旧安装包
            dir.listFiles()?.forEach { it.delete() }
            val target = File(dir, info.apkName)

            val request = Request.Builder()
                .url(info.apkUrl)
                .header("Accept", "application/octet-stream")
                .get()
                .build()
            downloadClient().newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("下载失败 HTTP ${resp.code}")
                val body = resp.body ?: throw IllegalStateException("空响应")
                val total = body.contentLength()
                var read = 0L
                target.outputStream().buffered().use { out ->
                    val stream = body.byteStream()
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = stream.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) onProgress((read * 100 / total).toInt())
                    }
                }
                // 只认 isSuccessful 是不够的：连接中途断开时 OkHttp 可能已经返回了
                // 200 和部分字节，交给安装器只会得到一句"解析包时出现问题"，
                // 用户完全看不出是下载残缺。这里按声明的长度核对。
                if (read == 0L) throw IllegalStateException("下载内容为空")
                if (total > 0 && read != total) {
                    throw IllegalStateException("下载不完整（$read/$total 字节），请重试")
                }
            }
            Result.success(target)
        } catch (e: Exception) {
            // 失败就清掉半成品，避免残留文件被下次误当成"已下载"
            runCatching { File(context.filesDir, "updates/${info.apkName}").takeIf { it.exists() }?.delete() }
            Result.failure(e)
        }
    }
}
