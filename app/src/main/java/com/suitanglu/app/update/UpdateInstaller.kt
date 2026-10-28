package com.suitanglu.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

sealed class InstallResult {
    /** 系统安装器已拉起 */
    data object Started : InstallResult()

    /** 缺「安装未知应用」权限，已跳转到授权页 */
    data object NeedPermission : InstallResult()

    data class Failed(val message: String) : InstallResult()
}

/**
 * APK 安装的统一入口。
 *
 * 原来这段逻辑内联在 SettingsViewModel 里，直接 startActivity，没有检查
 * 「安装未知应用」权限：Android 8 起没这个权限时 startActivity 要么抛
 * ActivityNotFoundException，要么被系统静默拦下，用户看到的就是"点了没反应"。
 */
object UpdateInstaller {

    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
        } else {
            true
        }

    fun openPermissionSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun install(context: Context, file: File): InstallResult {
        if (!file.exists() || file.length() == 0L) {
            return InstallResult.Failed("安装包不存在或为空，请重新下载")
        }
        if (!canInstall(context)) {
            openPermissionSettings(context)
            return InstallResult.NeedPermission
        }
        return runCatching {
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            InstallResult.Started
        }.getOrElse { InstallResult.Failed("无法拉起安装器：${it.message}") }
    }
}
