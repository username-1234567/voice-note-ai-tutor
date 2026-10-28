package com.suitanglu.app.ui.screens.recording

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import com.suitanglu.app.data.db.Marker
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Bookmark
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.suitanglu.app.data.CameraMode
import com.suitanglu.app.ui.components.AppAlertDialog
import com.suitanglu.app.data.Formatters
import com.suitanglu.app.service.RecordingBus
import com.suitanglu.app.service.RecordingService
import com.suitanglu.app.ui.theme.BrandAccent
import com.suitanglu.app.ui.theme.tabularNumbers
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    // 录音途中回看已标记
    var showMarkerStrip by remember { mutableStateOf(false) }
    val markerStrip by produceState(emptyList<Pair<Marker, String?>>(), showMarkerStrip, markerCount) {
        if (showMarkerStrip) value = vm.loadMarkerStrip()
    }
    // 必须订阅：不订阅时 cameraMode 永远是默认值，设置的“应用内相机”不会生效
    val general by vm.generalConfig.collectAsState()

    // saveable：系统在相机/裁剪期间重建 Activity 时，弹层与未保存文字不丢
    var showMarkerSheet by rememberSaveable { mutableStateOf(false) }
    var markerText by rememberSaveable { mutableStateOf("") }
    var showTextOnlySheet by rememberSaveable { mutableStateOf(false) }
    var textOnlyText by rememberSaveable { mutableStateOf("") }
    var showStopConfirm by remember { mutableStateOf(false) }
    var croppingSource by rememberSaveable { mutableStateOf<Uri?>(null) }
    // 这次拍照是不是"长按快拍"：是则跳过裁剪，直接入库
    var quickCapture by rememberSaveable { mutableStateOf(false) }
    // saveable：进程被杀恢复后为 true，不再自动重启录音（避免恢复出“一坏一好”两条录音）
    var attemptedStart by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }

    DisposableEffect(Unit) {
        onDispose { RecordingBus.clearStartError() }
    }

    fun reportQuickSave(ok: Boolean) {
        if (!ok) {
            android.widget.Toast.makeText(context, "这一页没存上，再拍一次", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val cropLauncher = rememberLauncherForActivityResult(UCropContract()) { uri ->
        uri?.let { vm.addCroppedImage(it) }
    }
    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val src = croppingSource
        when {
            ok && src != null && quickCapture -> {
                quickCapture = false
                vm.saveQuickMarker(src) { reportQuickSave(it) }
            }
            ok && src != null -> cropLauncher.launch(src to newCropUri(context))
            else -> quickCapture = false
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

    // 应用内相机返回的图片 → 裁剪（快拍则直接入库）
    LaunchedEffect(Unit) {
        val path = PendingMarkerStore.capturedPath
        PendingMarkerStore.capturedPath = null
        if (path != null) {
            val uri = existingFileUri(context, File(path))
            if (quickCapture) {
                quickCapture = false
                vm.saveQuickMarker(uri) { reportQuickSave(it) }
            } else {
                cropLauncher.launch(uri to newCropUri(context))
            }
        } else if (quickCapture) {
            // 进了相机又退出来没拍：不清标志的话，下一次短按会被当成快拍
            quickCapture = false
        }
    }

    if (showMarkerStrip) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showMarkerStrip = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
                Text(
                    "这节课的标记（${markerStrip.size}）",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
                )
                androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth()) {
                    items(markerStrip.size) { i ->
                        val (m, path) = markerStrip[i]
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (path != null) {
                                coil.compose.AsyncImage(
                                    model = java.io.File(path),
                                    contentDescription = null,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                                )
                            } else {
                                Box(
                                    Modifier
                                        .size(56.dp)
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    com.suitanglu.app.data.Formatters.clockTime(m.offsetMs),
                                    style = MaterialTheme.typography.titleSmall.tabularNumbers(),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    m.userText?.takeIf { it.isNotBlank() } ?: "拍照标记",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val s = state
    // 这个判断必须在 if (s == null) 外面。
    //
    // 原先它写在里面：从首页横幅进入时录音已经在跑，s 非空，这个分支压根没被组合过，
    // attemptedStart 一直是 false。等用户点"结束"、bus 被清空，分支首次组合 → 触发一次 start；
    // 而那一刻服务正在收尾（busy=true、recorder 已置空），于是被记成 restartAfterStop，
    // 收尾一结束就自动开了一条新录音 —— 用户看到的就是"结束完又冒出来一条"。
    LaunchedEffect(Unit) {
        if (!attemptedStart) {
            attemptedStart = true
            if (RecordingBus.state.value == null) RecordingService.start(context)
        }
    }
    if (s == null) {
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

    // 长时间没声音时由 Service 推上来，这里就地提示一次
    val levelWarning by RecordingBus.levelWarning.collectAsState()
    LaunchedEffect(levelWarning) {
        levelWarning?.let {
            snackbar.showSnackbar(it)
            RecordingBus.clearLevelWarning()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 顶部：返回不打断录音（服务仍在跑，首页有横幅可回来）。
            // 副标题单独一行并与标题左缘对齐——两行文字夹一个返回箭头会显得箭头没对齐
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onFinished(s.recordingId) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回首页（继续录音）")
                }
                Text(
                    s.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                "${Formatters.timeOnly(s.startTime)} 开始 · 已录 ${Formatters.durationShort(s.elapsedMs)}",
                style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(start = 48.dp, bottom = 4.dp),
            )

            // 环与它下面那组信息作为一个整体居中：信息贴着环，留白集中到控制区上方，
            // 而不是让"讲到第几张"孤零零漂在屏幕中间
            Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SoundPrintRing(
                        elapsedMs = s.elapsedMs,
                        level = s.level,
                        paused = s.paused,
                        interrupted = s.interrupted,
                        modifier = Modifier.size(292.dp),
                    )
                    Spacer(Modifier.height(22.dp))
                    // 这一行是"这节课进行到哪"的答案：讲解进度 + 标记数
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "已标记 $markerCount 个",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = if (markerCount > 0) {
                                androidx.compose.ui.text.style.TextDecoration.Underline
                            } else {
                                null
                            },
                            modifier = if (markerCount > 0) {
                                Modifier.clickable { showMarkerStrip = true }
                            } else {
                                Modifier
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("·", color = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            vm.aiStatusSummary(),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    // 环的图例：不解释的话它就是一圈没来由的刻度
                    Text(
                        "最近 60 秒 · 每根条 1 秒，长度是那一秒的音量",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
            }

            // 打断原因写全：环中心只放得下"录音被打断"四个字
            if (s.interrupted) {
                Text(
                    "已被打断${s.interruptReason?.let { "（$it）" } ?: ""}，音频焦点恢复后自动继续",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
            }

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
                // 危险操作：淡红底 + 红描边。纯描边太轻（比暂停还弱），
                // 实心大红又会抢走「拍照并标记」的主角位，取中间一档
                IconButton(
                    onClick = { showStopConfirm = true },
                    modifier = Modifier
                        .size(60.dp)
                        .border(
                            1.5.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                            CircleShape,
                        )
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f), CircleShape),
                    colors = IconButtonDefaults.iconButtonColors(
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

            Spacer(Modifier.height(20.dp))
            // 秒表式「只标记」：不拍照，就记一个时间点。
            // 老师口头强调的重点、想回头再听一遍的地方，用这个比举手机拍一张更轻。
            OutlinedButton(
                onClick = { showTextOnlySheet = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(
                    Icons.Filled.Bookmark,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("只标记，不拍照", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(10.dp))
            // 自绘按钮而不是 Button：Material3 的 Button 只暴露 onClick，挂不下长按。
            // 短按 = 打开标记面板（可裁剪、可写字）；长按 = 直接进相机，拍完就入库。
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primary)
                    .combinedClickable(
                        onClick = {
                            if (!hasCameraPermission && general.cameraMode != CameraMode.SYSTEM) {
                                onRequestPermissions()
                            } else {
                                showMarkerSheet = true
                            }
                        },
                        onLongClick = {
                            if (!hasCameraPermission && general.cameraMode != CameraMode.SYSTEM) {
                                onRequestPermissions()
                            } else {
                                quickCapture = true
                                if (general.cameraMode == CameraMode.IN_APP) {
                                    onOpenInAppCamera()
                                } else {
                                    val src = newPhotoUri(context)
                                    croppingSource = src
                                    takePictureLauncher.launch(src)
                                }
                            }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.PhotoCamera,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (markerCount == 0) "拍照并标记" else "拍照并标记（已 $markerCount 个）",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        // 固定 56dp 高 + 系统大字体时会换行顶出按钮，直接单行省略
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                "长按直接拍，不裁剪，课后统一修",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
            Spacer(Modifier.height(10.dp))
        }
    }

    // 停止确认
    if (showStopConfirm) {
        AppAlertDialog(
            title = "停止录音",
            message = "录音会先保存。结束后到回放页点「发送全部」交给 AI 讲解，且无法继续录这一段。",
            confirmLabel = "停止录音",
            dismissLabel = "继续录",
            destructive = true,
            onConfirm = {
                showStopConfirm = false
                val id = s.recordingId
                vm.stop { }
                onFinished(id)
            },
            onDismissRequest = { showStopConfirm = false },
        )
    }

    // 秒表式「只标记」弹层：一个时间点 + 可选文字，不拍照
    if (showTextOnlySheet) {
        ModalBottomSheet(onDismissRequest = { showTextOnlySheet = false }) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                Text("只标记，不拍照", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 4.dp))
                Text(
                    "记下当前时间点，可带备注。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 14.dp),
                )
                OutlinedTextField(
                    value = textOnlyText,
                    onValueChange = { textOnlyText = it },
                    label = { Text("文字备注（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        vm.saveTextOnlyMarker(textOnlyText) { ok ->
                            if (ok) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                textOnlyText = ""
                                showTextOnlySheet = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Text("标记当前时间")
                }
            }
        }
    }

    // 标记编辑底部弹层
    if (showMarkerSheet) {
        ModalBottomSheet(onDismissRequest = {
            showMarkerSheet = false
            markerText = "" // 未保存的文字备注不带入下一个标记
            vm.discardPending() // 没保存的图片也一并丢掉
        }) {
            Column(
                Modifier
                    .padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
                    // 键盘弹出时整体上推，否则保存按钮会被推到屏幕外，得先上滑才看得见
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "添加标记",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Text(
                    "保存后加入讲解队列，录音结束后统一发送。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 14.dp),
                )
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
                        modifier = Modifier.height(if (pending.size > 3) 168.dp else 112.dp),
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
                    "系统相机中断录音时，可在设置里改用应用内相机。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

/** 一圈 60 根条 = 最近 60 秒 */
private const val RingSegments = 60

/**
 * 声纹环：每一秒一根条，条长 = 那一秒的音量峰值。
 *
 * 一根条同时承担两件事——走到哪了（已录的条是品牌绿，未录是浅灰短桩）、
 * 讲得多大声（条的长度）。比"进度弧 + 秒刻度"两套同心刻度叠在一起更干净，
 * 也让录音页有了别的录音 App 没有的东西：一眼看出哪几秒没人说话。
 */
@Composable
private fun SoundPrintRing(
    elapsedMs: Long,
    level: Float,
    paused: Boolean,
    interrupted: Boolean,
    modifier: Modifier = Modifier,
) {
    val levels = remember { mutableStateListOf<Float>().apply { repeat(RingSegments) { add(0f) } } }
    var slotSec by remember { mutableIntStateOf(-1) }
    var slotPeak by remember { mutableFloatStateOf(0f) }

    val elapsedSec = (elapsedMs / 1000).toInt()
    val current = elapsedSec % RingSegments

    // 秒数变化时先清掉 60 秒前留在同一槽位的旧值，再把这一秒内的峰值实时写回去
    LaunchedEffect(elapsedSec, level, paused, interrupted) {
        if (elapsedSec != slotSec) {
            slotSec = elapsedSec
            slotPeak = 0f
            levels[current] = 0f
        }
        if (!paused && !interrupted) {
            slotPeak = maxOf(slotPeak, level)
            levels[current] = slotPeak
        }
    }

    val snapshot = levels.toList()
    val activeColor = if (interrupted) MaterialTheme.colorScheme.error else BrandAccent()
    val pastColor = if (paused) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f) else activeColor
    val futureColor = MaterialTheme.colorScheme.outlineVariant
    val muted = paused || interrupted

    // 当前这根条跟随实时音量：用可中断的动画推进，而不是每 500ms 硬跳一格
    val vu = remember { Animatable(0f) }
    LaunchedEffect(level, muted) {
        vu.animateTo(
            targetValue = if (muted) 0f else level,
            animationSpec = tween(durationMillis = 260, easing = LinearOutSlowInEasing),
        )
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val baseRadius = size.minDimension / 2f - 34.dp.toPx()
            val cx = size.width / 2f
            val cy = size.height / 2f
            // 起点刻线（12 点方向）：没有它，这一圈刻度就是没有起止的装饰
            drawLine(
                color = futureColor,
                start = Offset(cx, cy - baseRadius + 4.dp.toPx()),
                end = Offset(cx, cy - baseRadius + 16.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            for (i in 0 until RingSegments) {
                val angle = Math.toRadians((i * 6 - 90).toDouble())
                val dx = cos(angle).toFloat()
                val dy = sin(angle).toFloat()
                val isCurrent = i == current
                val isPast = if (elapsedSec < RingSegments) i < current else !isCurrent

                val barLen: Float
                val barAlpha: Float
                val barWidth: Float
                val barColor: Color
                when {
                    isPast -> {
                        val lvl = snapshot[i]
                        barLen = 7f + lvl * 21f
                        barAlpha = 0.30f + lvl * 0.60f
                        barWidth = 3.2f
                        barColor = pastColor
                    }
                    isCurrent -> {
                        barLen = maxOf(10f, 7f + vu.value * 21f)
                        barAlpha = if (muted) 0.55f else 1f
                        barWidth = 3.6f
                        barColor = pastColor
                    }
                    else -> {
                        barLen = 8f
                        barAlpha = 0.85f
                        barWidth = 2.4f
                        barColor = futureColor
                    }
                }
                val startR = baseRadius
                val endR = baseRadius + barLen.dp.toPx()
                drawLine(
                    color = barColor.copy(alpha = barColor.alpha * barAlpha),
                    start = Offset(cx + dx * startR, cy + dy * startR),
                    end = Offset(cx + dx * endR, cy + dy * endR),
                    strokeWidth = barWidth.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                formatTimer(elapsedMs),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp).tabularNumbers(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val targetDot = if (paused) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                }
                // 颜色跟着状态渐变，不硬切
                val dotColor by animateColorAsState(
                    targetValue = targetDot,
                    animationSpec = tween(220),
                    label = "recDotColor",
                )
                RecordingDot(color = dotColor, animated = !paused)
                Spacer(Modifier.width(7.dp))
                val statusText = when {
                    interrupted -> "录音被打断"
                    paused -> "已暂停"
                    else -> "正在录音"
                }
                // 状态是低频变化，用交叉淡入而不是硬切；退出比进入短，符合"注意力在进入"
                AnimatedContent(
                    targetState = statusText,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                    label = "recStatus",
                ) { text ->
                    Text(
                        text,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (interrupted) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

/** 呼吸小圆点：录音中脉动，暂停/打断时静止 */
@Composable
private fun RecordingDot(color: Color, animated: Boolean) {
    val alpha = if (animated) {
        val pulse = rememberInfiniteTransition(label = "recDot")
        val a by pulse.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
            label = "recDotAlpha",
        )
        a
    } else {
        0.9f
    }
    Box(Modifier.size(6.dp).background(color.copy(alpha = alpha), CircleShape))
}

private fun formatTimer(ms: Long): String {
    val total = ms / 1000
    return String.format("%02d:%02d:%02d", total / 3600, total % 3600 / 60, total % 60)
}
