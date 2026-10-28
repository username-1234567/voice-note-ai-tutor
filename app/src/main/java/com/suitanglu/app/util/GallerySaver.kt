package com.suitanglu.app.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * 把应用私有目录里的图片保存到系统相册。
 *
 * Android 10+ 用 MediaStore 写入，不需要存储权限；
 * Android 9 及以下要 WRITE_EXTERNAL_STORAGE，但本 App 最低 Android 10，所以只走 MediaStore。
 *
 * 返回 null 表示失败，调用方自行提示。
 */
object GallerySaver {

    fun save(context: Context, sourcePath: String): Boolean {
        val src = File(sourcePath)
        if (!src.exists()) return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(context, src)
            } else {
                saveLegacy(context, src)
            }
        }.getOrDefault(false)
    }

    private fun saveViaMediaStore(context: Context, src: File): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/随堂录",
            )
        }
        val uri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values,
        ) ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            } != null
        } catch (e: Exception) {
            // 写入失败就删掉这条半成品记录，避免相册里留一张 0 字节的图
            runCatching { resolver.delete(uri, null, null) }
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(context: Context, src: File): Boolean {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "随堂录",
        )
        if (!dir.exists()) dir.mkdirs()
        val dest = File(dir, "${System.currentTimeMillis()}.jpg")
        return runCatching { src.copyTo(dest, overwrite = true) }.isSuccess
    }
}
