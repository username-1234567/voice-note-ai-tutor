package com.suitanglu.app.ui.screens.recording

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.suitanglu.app.data.CameraMode
import com.suitanglu.app.service.RecordingBus
import com.suitanglu.app.service.RecordingService
import com.suitanglu.app.ui.theme.BrandAccent
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    hasCameraPermission: Boolean,
    onRequestPermissions: () -> Unit,
    onFinished: (Long) -> Unit,
    onOpenInAppCamera: () -> Unit,
) {
    val vm: RecordingViewModel = viewModel()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val state by vm.recordingState.collectAsState()
    val startError by RecordingBus.startError.collectAsState()
    val pending by vm.pendingImages.collectAsState()
    val markerCount by vm.markerCount.collectAsState()
    // 必须订阅：不订阅时 cameraMode 永远是默认值，设置的“应用内相机”不会生效
    val general by vm.generalConfig.collectAsState()

    // saveable：系统在相机/裁剪期间重建 Activity 时，弹层与未保存文字不丢
    var showMarkerSheet by rememberSaveable { mutableStateOf(false) }
    var markerText by rememberSaveable { mutableStateOf("") }
    var showStopConfirm by remember { mutableStateOf(false) }
    var croppingSource by rememberSaveable { mutableStateOf<Uri?>(null) }
    // saveable：进程被杀恢复后为 true，不再自动重启录音（避免恢复出“一坏一好”两条录音）
    var attemptedStart by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { RecordingBus.clearStartError() }
    }

    val cropLauncher = rememberLauncherForActivityResult(UCropContract()) { uri ->
        uri?.let { vm.addCroppedImage(it) }
    }
    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val src = croppingSource
        if (ok && src != null) {
            cropLauncher.launch(src to newCropUri(context))
        }
        croppingSource = null
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            // 相册图复制到缓存再裁剪
            val dest = File(context.cacheDir, "photos/PICK_${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs() }
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { input.copyTo(it) }
                }
            }
            if (dest.exists() && dest.length() > 0) {
                cropLauncher.launch(existingFileUri(context, dest) to newCropUri(context))
            }
        }
    }

    // 应用内相机返回的图片 → 裁剪
    LaunchedEffect(Unit) {
        PendingMarkerStore.capturedPath?.let { path ->
            PendingMarkerStore.capturedPath = null
            cropLauncher.launch(existingFileUri(context, File(path)) to newCropUri(context))
        }
    }

    val s = state
    if (s == null) {
        // 首次进入：启动前台录音服务；等待服务就绪时显示启动中。
        // attemptedStart（saveable）防止停止录音瞬间（bus 清空、页面尚未退出）重复触发启动；
        // 进程被杀恢复的页面同样不再自动开新录音。
        LaunchedEffect(Unit) {
            if (!attemptedStart) {
                attemptedStart = true
                RecordingService.start(context)
            }
        }
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val err = startError
            when {
                err != null -> {
                    Icon(
                        Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        err,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "请检查录音权限，或确认其他应用是否正在占用麦克风。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                attemptedStart -> {
                    Text("录音已结束", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "这段录音已停止，可返回首页查看标记与 AI 讲解。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("正在启动录音…", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "若长时间无响应，请检查录音权限与其他应用是否占用麦克风。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = { onFinished(0) }) { Text("返回") }
        }
        return
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(s.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        AnimatedContent(
            targetState = Triple(s.interrupted, s.paused, s.interruptReason),
            label = "status",
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
        ) { (interrupted, paused, reason) ->
            Text(
                when {
                    interrupted -> "已被打断（${reason ?: "未知原因"}），音频焦点恢复后自动继续"
                    paused -> "已暂停"
                    else -> "正在录音"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (interrupted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(28.dp))

        RecordingRing(
            elapsedMs = s.elapsedMs,
            paused = s.paused,
            interrupted = s.interrupted,
            modifier = Modifier.size(260.dp),
        )

        Spacer(Modifier.height(24.dp))
        Text(
            vm.aiStatusSummary(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            // 次要操作：暂停/继续，低饱和实心
            FilledIconButton(
                onClick = { vm.pauseOrResume() },
                modifier = Modifier.size(60.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Icon(
                    if (s.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = if (s.paused) "继续" else "暂停",
                    modifier = Modifier.size(28.dp),
                )
            }
            // 危险操作：停止。用描边而非大红实心，避免抢走「拍照并标记」的主角位；
            // 点击后仍有确认弹窗，误触不会直接结束录音。
            OutlinedIconButton(
                onClick = { showStopConfirm = true },
                modifier = Modifier.size(60.dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
                colors = IconButtonDefaults.outlinedIconButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(
                    Icons.Filled.Stop,
                    contentDescription = "停止",
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                if (!hasCameraPermission && general.cameraMode != CameraMode.SYSTEM) {
                    onRequestPermissions()
                    return@Button
                }
                showMarkerSheet = true
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("拍照并标记（已 $markerCount 个）")
        }
    }

    // 停止确认
    if (showStopConfirm) {
        AlertDialog(
            onDismissRequest = { showStopConfirm = false },
            title = { Text("停止录音") },
            text = { Text("停止后将按标记顺序自动发送 AI 讲解（需要网络）。确定停止吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showStopConfirm = false
                    val id = s.recordingId
                    vm.stop { }
                    onFinished(id)
                }) { Text("停止") }
            },
            dismissButton = { TextButton(onClick = { showStopConfirm = false }) { Text("继续录音") } },
        )
    }

    // 标记编辑底部弹层
    if (showMarkerSheet) {
        ModalBottomSheet(onDismissRequest = {
            showMarkerSheet = false
            markerText = "" // 未保存的文字备注不带入下一个标记
        }) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (general.cameraMode == CameraMode.IN_APP) {
                                showMarkerSheet = false
                                onOpenInAppCamera()
                            } else {
                                croppingSource = newPhotoUri(context)
                                takePictureLauncher.launch(croppingSource!!)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("拍照")
                    }
                    FilledTonalButton(
                        onClick = {
                            pickImageLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("从相册选")
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (pending.isNotEmpty()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.height(if (pending.size > 3) 240.dp else 120.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(pending) { file ->
                            Box {
                                AsyncImage(
                                    model = file, contentDescription = null,
                                    modifier = Modifier.fillMaxWidth().height(80.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                                )
                                IconButton(
                                    onClick = { vm.removePending(file) },
                                    modifier = Modifier.align(Alignment.TopEnd).size(24.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "移除", tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = markerText,
                    onValueChange = { markerText = it },
                    label = { Text("文字备注（可选，不会发送给 AI）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        vm.saveMarker(markerText) { ok ->
                            if (ok) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                markerText = ""
                                showMarkerSheet = false
                            }
                        }
                    },
                    enabled = pending.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(if (pending.isEmpty()) "请先拍照或选择图片" else "保存标记")
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    "提示：若系统相机导致录音中断，可在设置中切换为应用内相机。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

/** 圆环计时：60 秒循环进度弧 + 录音中呼吸外环；暂停静止变灰、被打断变红 */
@Composable
private fun RecordingRing(
    elapsedMs: Long,
    paused: Boolean,
    interrupted: Boolean,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val activeColor = if (interrupted) MaterialTheme.colorScheme.error else BrandAccent()
    val ringColor = if (paused) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else activeColor

    val breath = rememberInfiniteTransition(label = "breath")
    val breathAlpha by breath.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "breathAlpha",
    )
    val breathRadius by breath.animateFloat(
        initialValue = 1.03f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "breathRadius",
    )
    val showBreath = !paused && !interrupted
    val effAlpha = if (showBreath) breathAlpha else 0.12f
    val effRadius = if (showBreath) breathRadius else 1.06f

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val radius = (size.minDimension - stroke * 3) / 2f
            val center = Offset(size.width / 2, size.height / 2)
            // 呼吸外环
            drawCircle(
                color = activeColor.copy(alpha = effAlpha),
                radius = radius * effRadius,
                center = center,
                style = Stroke(stroke * 0.7f),
            )
            // 轨道
            drawCircle(
                color = trackColor,
                radius = radius,
                center = center,
                style = Stroke(stroke),
            )
            // 60 秒循环进度
            val sweep = (elapsedMs % 60_000) / 60_000f * 360f
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                formatTimer(elapsedMs),
                style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

private fun formatTimer(ms: Long): String {
    val total = ms / 1000
    return String.format("%02d:%02d:%02d", total / 3600, total % 3600 / 60, total % 60)
}
