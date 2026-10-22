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
    const val CURRENT_VERSION = "1.4.0"
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
        .build()

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
            downloadClient().newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.code == 404) return@use UpdateCheckResult.UpToDate(UpdateConfig.CURRENT_VERSION)
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
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = body.byteStream().read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) onProgress((read * 100 / total).toInt())
                    }
                }
            }
            Result.success(target)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
