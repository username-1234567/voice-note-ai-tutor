package com.suitanglu.app.ui.screens.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.suitanglu.app.ui.screens.recording.PendingMarkerStore
import java.io.File
import java.util.Locale

/** 应用内相机（CameraX）：系统相机中断录音时的备用方案 */
@Composable
fun InAppCameraScreen(
    hasCameraPermission: Boolean,
    onRequestPermissions: () -> Unit,
    onCaptured: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }
    var capturing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // 变焦挂在 Camera 实例上，绑定成功后才拿得到；范围由设备决定（通常 1×–8×）
    var camera by remember { mutableStateOf<Camera?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            onRequestPermissions()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                        val providerFuture = ProcessCameraProvider.getInstance(ctx)
                        providerFuture.addListener({
                            try {
                                val provider = providerFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                provider.unbindAll()
                                val bound = provider.bindToLifecycle(
                                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture,
                                )
                                camera = bound
                                runCatching {
                                    bound.cameraInfo.zoomState.value?.let { state ->
                                        // 有些设备 minZoomRatio < 1（超广角），但预览裁切不一致，
                                        // 统一从 1× 起，用户不会看到"缩到比肉眼还小"的怪画面
                                        maxZoom = state.maxZoomRatio.coerceAtLeast(1f)
                                        zoom = state.zoomRatio.coerceIn(1f, maxZoom)
                                    }
                                }
                            } catch (e: Exception) {
                                error = "相机启动失败：${e.message}"
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                // 手势层单独铺在预览之上：PreviewView 会自己消费触摸事件，
                // 直接给它加 pointerInput 收不到捏合
                Box(
                    Modifier
                        .matchParentSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, gestureZoom, _ ->
                                val target = camera ?: return@detectTransformGestures
                                val next = (zoom * gestureZoom)
                                    .coerceIn(1f, maxZoom.coerceAtLeast(1f))
                                if (next != zoom) {
                                    zoom = next
                                    runCatching { target.cameraControl.setZoomRatio(next) }
                                }
                            }
                        },
                )

                // 倍率常显：捏合时手指盖住画面中央，读数放在右上角不会挡
                Text(
                    String.format(Locale.US, "%.1f×", zoom),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            } else {
                Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("需要相机权限")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onRequestPermissions) { Text("授权") }
                }
            }
            if (capturing) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = {
                    if (capturing) return@Button
                    capturing = true
                    val dir = File(context.cacheDir, "photos").apply { mkdirs() }
                    val file = File(dir, "CAM_${System.currentTimeMillis()}.jpg")
                    val options = ImageCapture.OutputFileOptions.Builder(file).build()
                    imageCapture.takePicture(
                        options,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                capturing = false
                                PendingMarkerStore.capturedPath = file.absolutePath
                                onCaptured()
                            }

                            override fun onError(exception: ImageCaptureException) {
                                capturing = false
                                error = "拍照失败：${exception.message}"
                            }
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Filled.Camera, contentDescription = null)
                Spacer(Modifier.padding(start = 8.dp))
                Text("拍摄")
            }
            TextButton(onClick = onCancel) { Text("取消") }
        }
    }
}
