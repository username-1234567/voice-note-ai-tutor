package com.suitanglu.app.ui.screens.recording

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.FileProvider
import com.yalantis.ucrop.UCrop

/** 系统相机/相册 → uCrop 自由比例裁剪 */
class UCropContract : ActivityResultContract<Pair<Uri, Uri>, Uri?>() {

    override fun createIntent(context: Context, input: Pair<Uri, Uri>): Intent {
        val (source, dest) = input
        val options = UCrop.Options().apply {
            setFreeStyleCropEnabled(true)
            setToolbarTitle("裁剪图片")
            // 工具栏/状态栏用深色主题的 surface（#141821）、裁剪画布用 background（#0E1118）：
            // 取同一套主题真值，不再用孤立的纯黑；工具栏保持深底浅字
            setToolbarColor(0xFF141821.toInt())
            setToolbarWidgetColor(0xFFFFFFFF.toInt())
            // 实色状态栏：Android 15 强制 edge-to-edge 下透明色/相关调用会崩溃
            setStatusBarColor(0xFF141821.toInt())
            setRootViewBackgroundColor(0xFF0E1118.toInt())
            // 裁剪框角标/激活控件用主色真值（浅色 scheme 的 primary 靛蓝），替代旧品牌青绿
            setActiveControlsWidgetColor(0xFF3B5BA5.toInt())
        }
        return UCrop.of(source, dest).withOptions(options).getIntent(context)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == android.app.Activity.RESULT_OK) {
            intent?.let { UCrop.getOutput(it) }
        } else null
}

object PendingMarkerStore {
    @Volatile var capturedPath: String? = null
}

/** 创建临时拍照文件 Uri（FileProvider） */
fun newPhotoUri(context: Context): Uri {
    val dir = java.io.File(context.cacheDir, "photos").apply { mkdirs() }
    val file = java.io.File(dir, "IMG_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/** 为已存在的缓存图片生成 FileProvider Uri（统一 content://，避免 file:// 暴露） */
fun existingFileUri(context: Context, file: java.io.File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

fun newCropUri(context: Context): Uri {
    val dir = java.io.File(context.cacheDir, "crops").apply { mkdirs() }
    val file = java.io.File(dir, "CROP_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
