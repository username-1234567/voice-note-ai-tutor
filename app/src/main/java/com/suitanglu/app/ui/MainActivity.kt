package com.suitanglu.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.suitanglu.app.AppGraph
import com.suitanglu.app.ui.nav.RecordNavHost
import com.suitanglu.app.ui.components.AppAlertDialog
import com.suitanglu.app.ui.theme.RecordTheme
import java.security.MessageDigest

class MainActivity : ComponentActivity() {

    private var grantedPermissions by mutableStateOf(setOf<String>())
    private var signatureBlocked by mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            grantedPermissions = result.filterValues { it }.keys
        }

    fun hasPermission(permission: String): Boolean =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /**
     * 运行时签名自检：签名与官方发布密钥不符（被篡改/二次打包）时阻断使用。
     * 这是防冒用的重要防线，但可被攻击者通过改包绕过——配合签名轮换使用。
     */
    private fun isSignatureValid(): Boolean = runCatching {
        val pm = packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }
        // 谱系/多签名者包的证书分布在不同 API 里（历史链、当前签名者），
        // 取并集校验：官方新钥出现在其中即放行；改包者只有自己的钥，被拦
        val certs = buildList {
            info.signingInfo?.signingCertificateHistory?.let { addAll(it) }
            info.signingInfo?.apkContentsSigners?.let { addAll(it) }
        }
        if (certs.isEmpty()) return true // 拿不到签名信息时不拦截，避免误伤
        val report = StringBuilder()
        val result = certs.any { sig ->
            val digest = MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
            val hex = digest.joinToString("") { "%02x".format(it) }
            report.append("cert=").append(hex).append('\n')
            hex == EXPECTED_SIGNING_SHA256
        }
        report.append("valid=").append(result).append('\n')
        runCatching {
            java.io.File(getExternalFilesDir(null), "sigcheck.txt").writeText(report.toString())
        }
        result
    }.getOrDefault(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppGraph.init(application)
        signatureBlocked = !isSignatureValid()
        grantedPermissions = buildSet {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filterTo(mutableSetOf()) { hasPermission(it) }

        setContent {
            RecordTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (signatureBlocked) {
                        AppAlertDialog(
                            title = "应用签名异常",
                            message = "当前安装包与官方发布签名不一致，可能已被篡改。" +
                                "为保护录音、照片与 AI 配置安全，应用已停止运行。" +
                                "请卸载后从官方 GitHub Releases 重新安装。",
                            confirmLabel = "前往官方下载",
                            destructive = true,
                            showIcon = true,
                            onConfirm = {
                                runCatching {
                                    startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(
                                                "https://github.com/username-1234567/voice-note-ai-tutor/releases/latest",
                                            ),
                                        ),
                                    )
                                }
                                finishAffinity()
                            },
                            dismissLabel = "退出",
                            onDismissRequest = { finishAffinity() },
                        )
                    } else {
                        RecordNavHost(
                            // 读 grantedPermissions 状态而不是一次性 checkSelfPermission，
                            // 否则授权回调后组合不会刷新，授权完仍无法开始录音/拍照
                            hasRecordPermission = grantedPermissions.contains(Manifest.permission.RECORD_AUDIO),
                            hasCameraPermission = grantedPermissions.contains(Manifest.permission.CAMERA),
                            hasNotificationPermission = Build.VERSION.SDK_INT < 33 ||
                                grantedPermissions.contains(Manifest.permission.POST_NOTIFICATIONS),
                            onRequestPermissions = {
                                val list = mutableListOf(
                                    Manifest.permission.RECORD_AUDIO,
                                    Manifest.permission.CAMERA,
                                )
                                if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
                                permissionLauncher.launch(list.toTypedArray())
                            },
                        )
                    }
                }
            }
        }
    }

    private companion object {
        /** v2 签名密钥（2026-10-11 轮换）证书 SHA-256 */
        const val EXPECTED_SIGNING_SHA256 =
            "4c7486f99e99c832d8d60cb5c25b1ee660b5c2b328752db1b791ac2398aa80b9"
    }
}
