package com.suitanglu.app.ui.screens.playback

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.suitanglu.app.data.Formatters
import com.suitanglu.app.data.ModelPricing
import com.suitanglu.app.data.db.Marker
import com.suitanglu.app.data.db.MarkerImage
import com.suitanglu.app.data.db.MarkerTurn
import com.suitanglu.app.data.db.PauseEvent
import com.suitanglu.app.data.db.Recording
import com.suitanglu.app.data.db.RecordingChat
import com.suitanglu.app.data.db.RecordingDigest
import com.suitanglu.app.ai.StreamingState
import com.suitanglu.app.ui.components.AppAlertDialog
import com.suitanglu.app.ui.screens.recording.UCropContract
import com.suitanglu.app.ui.screens.recording.existingFileUri
import com.suitanglu.app.ui.screens.recording.newCropUri
import com.suitanglu.app.ui.theme.MotionSpec
import com.suitanglu.app.ui.theme.pressFeedback
import com.suitanglu.app.ui.theme.recordColors
import com.suitanglu.app.ui.theme.rememberReducedMotion
import com.suitanglu.app.ui.theme.tabularNumbers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/** 快进/快退步长；配合 Replay10 / Forward10 图标，图标本身就是"10 秒" */
private const val SKIP_MS = 10_000L

/**
 * 标记卡在 LazyColumn 里的起始下标：
 * 0 = 播放器，1 = 发送条，2 = "这节课"卡片，3 = "标记"标题，之后才是每条标记。
 * 偏移量是固定的，改动列表结构时要同步这里。
 */
private const val MARKER_ITEM_OFFSET = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackScreen(recordingId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: PlaybackViewModel = viewModel(
        key = "playback_$recordingId",
        factory = viewModelFactory {
            initializer {
                PlaybackViewModel(context.applicationContext as android.app.Application, recordingId)
            }
        },
    )
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // 课后修图：在看图页点"裁剪" → UCrop → 回来替换这张图并重置讲解状态
    var editingImage by remember { mutableStateOf<MarkerImage?>(null) }
    var imageVersion by remember { mutableStateOf(0) }
    val cropLauncher = rememberLauncherForActivityResult(UCropContract()) { uri ->
        val target = editingImage
        editingImage = null
        if (uri != null && target != null) {
            vm.replaceMarkerImage(target, uri) {
                imageVersion++
                scope.launch { snackbar.showSnackbar("已替换原图，这条标记需要重新发送") }
            }
        }
    }
    val recording by vm.recording.collectAsState()
    val markers by vm.markers.collectAsState()
    val pauseEvents by vm.pauseEvents.collectAsState()
    val player by vm.player.collectAsState()
    val position by vm.position.collectAsState()
    val duration by vm.duration.collectAsState()
    val digest by vm.digest.collectAsState()
    val digestBusy by vm.digestBusy.collectAsState()
    val digestDraft by vm.digestDraft.collectAsState()
    val digestError by vm.digestError.collectAsState()
    val chats by vm.chats.collectAsState()
    val chatBusy by vm.chatBusy.collectAsState()
    val chatDraft by vm.chatDraft.collectAsState()
    val chatError by vm.chatError.collectAsState()
    val playing by vm.playing.collectAsState()
    val speed by vm.speed.collectAsState()
    val sendResult by vm.sendResult.collectAsState()
    val streaming by vm.streaming.collectAsState()
    val aiModel by vm.aiModel.collectAsState()
    val playerError by vm.playerError.collectAsState()
    val allTurns by vm.allTurns.collectAsState()
    val turnsByMarker = remember(allTurns) { allTurns.groupBy { it.markerId } }

    var expandedMarkerId by remember { mutableStateOf<Long?>(null) }
    // 只看待复习：被筛掉的标记仍然留在进度条上，只是不在列表里
    var onlyReview by rememberSaveable { mutableStateOf(false) }
    // 合并后的成员标记彻底隐藏：图片已挪到组首，组首展示全部；成员只留 mergedInto 指针不显示
    val visibleMarkers = (if (onlyReview) markers.filter { it.needsReview } else markers)
        .filter { it.mergedInto == null }
    // 合并发送：选中的多个标记共用一次请求、一条讲解
    var mergeMode by remember { mutableStateOf(false) }
    val mergeSel = remember { androidx.compose.runtime.mutableStateListOf<Long>() }
    // 非空表示正在编辑该标记的发送提示词
    var promptTarget by remember { mutableStateOf<Marker?>(null) }
    var mergePromptOpen by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // 播放器滚出屏幕后才出现迷你条：它只在"看不见控制"时才有存在价值
    val showMiniPlayer by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 140
        }
    }
    // 顶栏只关心"是否离开顶端"，1dp 就开始变，让滚动和顶栏的分隔感立刻成立
    val scrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 2
        }
    }
    // 播放器区的首次出场：等录音数据到位再播。挂在 Unit 上会让动画在 loading 阶段就跑完，
    // 真正出现时已经是"结束态"，等于白做。滚回顶部也不重播，所以状态放在 LazyColumn 外面。
    var playerEntered by remember { mutableStateOf(false) }
    LaunchedEffect(recording != null) {
        if (recording != null) playerEntered = true
    }
    val playerAlpha by animateFloatAsState(
        targetValue = if (playerEntered) 1f else 0f,
        animationSpec = tween(240, delayMillis = MotionSpec.FOLLOW_MS, easing = FastOutSlowInEasing),
        label = "playerAlpha",
    )
    val playerShift by animateFloatAsState(
        targetValue = if (playerEntered) 0f else 10f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "playerShift",
    )

    LaunchedEffect(sendResult) {
        sendResult?.let {
            if (it.isNotBlank()) snackbar.showSnackbar(it)
            vm.consumeSendResult()
        }
    }

    if (mergePromptOpen) {
        PromptDialog(
            title = "合并发送（${mergeSel.size} 个标记）",
            hint = "这些标记的图片会放进同一次请求、共用一条讲解。留空则用设置页里的全局提示词。",
            initial = "",
            onDismiss = { mergePromptOpen = false },
            onConfirm = { prompt ->
                vm.sendMerged(mergeSel.toList(), prompt)
                mergePromptOpen = false
                mergeMode = false
                mergeSel.clear()
            },
        )
    }

    promptTarget?.let { target ->
        PromptDialog(
            title = "发送提示词",
            hint = "只对这一个标记生效，会替换全局提示词；清空则恢复默认。",
            initial = target.customPrompt.orEmpty(),
            onDismiss = { promptTarget = null },
            onConfirm = { prompt ->
                vm.saveCustomPrompt(target.id, prompt)
                promptTarget = null
            },
        )
    }

    Scaffold(
        // 追问输入框在卡片里：键盘弹起时整页缩到键盘上方，
        // 否则输入框和迷你播放条都会被埋在输入法下面
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (mergeMode) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "已选 ${mergeSel.size} 个",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { mergeMode = false; mergeSel.clear() }) {
                            Text("取消")
                        }
                        Button(
                            onClick = { mergePromptOpen = true },
                            enabled = mergeSel.size >= 2,
                        ) {
                            Text("合并发送")
                        }
                    }
                }
            }
        },
        topBar = {
            PlaybackTopBar(
                title = recording?.title ?: "录音回放",
                subtitle = recording?.let { rec ->
                    "${Formatters.dateTime(rec.startTime)} · ${Formatters.durationShort(rec.durationMs)}"
                },
                scrolled = scrolled,
                onBack = onBack,
            )
        },
    ) { padding ->
        val rec = recording
        Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            // 迷你条是浮层，用底部留白给它让位，避免遮住最后一条标记
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                // 迷你播放条出现时多留一条身位，最后一条标记不会被它压住
                bottom = if (showMiniPlayer) 88.dp else 32.dp,
            ),
        ) {
            if (rec == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                return@LazyColumn
            }

            // 播放器
            item {
                Column(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 2.dp)
                        // 首次进场的淡入上移：写在 LazyColumn 外面算值，滚回来不重播
                        .graphicsLayer {
                            alpha = playerAlpha
                            translationY = playerShift.dp.toPx()
                        },
                ) {
                    if (rec.status == Recording.STATUS_AUDIO_DELETED) {
                        Text(
                            "音频已删除",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            "${Formatters.dateTime(rec.startTime)} · ${Formatters.duration(rec.durationMs)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (playerError != null) {
                        Text(
                            "音频无法播放：$playerError",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            "${Formatters.dateTime(rec.startTime)} · ${Formatters.duration(rec.durationMs)}\n标记与 AI 讲解仍可查看。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (player == null) {
                        Text("音频文件不存在或正在准备…", color = MaterialTheme.colorScheme.error)
                    } else {
                        MarkerProgressBar(
                            durationMs = duration.takeIf { it > 0 } ?: rec.durationMs,
                            positionMs = position,
                            markers = markers,
                            pauseEvents = pauseEvents,
                            onSeek = { vm.seekTo(it) },
                            onMarkerClick = { m ->
                                vm.jumpToMarker(m)
                                expandedMarkerId = m.id
                                // 从进度条点选时退出筛选：否则被筛掉的那条算不出滚动位置
                                onlyReview = false
                                // 点的是进度条上的圆点，卡片多半在屏幕外。
                                // 不滚过去，用户只会看到进度跳了一下，以为"没打开"。
                                val index = markers.indexOfFirst { it.id == m.id }
                                if (index >= 0) {
                                    scope.launch {
                                        listState.animateScrollToItem(MARKER_ITEM_OFFSET + index)
                                    }
                                }
                            },
                            onPausePointClick = { event ->
                                scope.launch {
                                    snackbar.showSnackbar(
                                        if (event.type == PauseEvent.TYPE_USER_PAUSE) "此处为手动暂停"
                                        else "此处因${event.reason ?: "来电/其他应用打断"}，录音暂停",
                                    )
                                }
                            },
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(top = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                Formatters.clockTime(position),
                                // 播放进度每 200ms 刷新，不等宽会让整行左右抖
                                style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                Formatters.clockTime(duration.takeIf { it > 0 } ?: rec.durationMs),
                                style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // 播放键居中，倍速靠右：倍速是"设置"不是"操作"，不该挤在播放键旁边
                        Box(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                            Row(
                                Modifier.align(Alignment.Center),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val backSource = remember { MutableInteractionSource() }
                                IconButton(
                                    onClick = { vm.skip(-SKIP_MS) },
                                    interactionSource = backSource,
                                    modifier = Modifier.pressFeedback(backSource, MotionSpec.PRESS_SCALE_ICON),
                                ) {
                                    Icon(
                                        Icons.Filled.Replay10,
                                        contentDescription = "后退 10 秒",
                                        modifier = Modifier.size(26.dp),
                                    )
                                }
                                val playSource = remember { MutableInteractionSource() }
                                FilledIconButton(
                                    onClick = { vm.togglePlay() },
                                    interactionSource = playSource,
                                    modifier = Modifier
                                        .size(60.dp)
                                        .pressFeedback(playSource, 0.94f),
                                ) {
                                    // 播放/暂停交叉淡入缩放：图标瞬切是最"廉价"的那种反馈
                                    AnimatedContent(
                                        targetState = playing,
                                        transitionSpec = {
                                            (fadeIn(tween(150)) +
                                                scaleIn(tween(150), initialScale = 0.80f)) togetherWith
                                                (fadeOut(tween(110)) +
                                                    scaleOut(tween(110), targetScale = 0.80f))
                                        },
                                        contentAlignment = Alignment.Center,
                                        label = "playPauseIcon",
                                    ) { isPlaying ->
                                        Icon(
                                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = if (isPlaying) "暂停" else "播放",
                                            // 三角形几何居中但视觉偏左，右移 1.5dp 找视觉中心
                                            modifier = Modifier
                                                .size(30.dp)
                                                .offset(x = if (isPlaying) 0.dp else 1.5.dp),
                                        )
                                    }
                                }
                                val forwardSource = remember { MutableInteractionSource() }
                                IconButton(
                                    onClick = { vm.skip(SKIP_MS) },
                                    interactionSource = forwardSource,
                                    modifier = Modifier.pressFeedback(forwardSource, MotionSpec.PRESS_SCALE_ICON),
                                ) {
                                    Icon(
                                        Icons.Filled.Forward10,
                                        contentDescription = "前进 10 秒",
                                        modifier = Modifier.size(26.dp),
                                    )
                                }
                            }
                            SpeedButton(
                                speed = speed,
                                onClick = { showSpeedMenu = true },
                                modifier = Modifier.align(Alignment.CenterEnd),
                            )
                            androidx.compose.material3.DropdownMenu(
                                expanded = showSpeedMenu,
                                onDismissRequest = { showSpeedMenu = false },
                            ) {
                                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { v ->
                                    // 当前倍速加勾：原来六项长得完全一样，选中的那个没有落点
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = {
                                            Text(
                                                formatSpeed(v),
                                                style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                                                color = if (v == speed) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                            )
                                        },
                                        trailingIcon = if (v == speed) {
                                            {
                                                Icon(
                                                    Icons.Filled.Check,
                                                    contentDescription = "当前倍速",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                        onClick = {
                                            vm.setSpeed(v)
                                            showSpeedMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 发送 / 重发：录音结束后不再自动发送，这里是唯一的出口
            item {
                val pendingCount = markers.count {
                    it.aiStatus == Marker.AI_PENDING || it.aiStatus == Marker.AI_SENDING
                }
                val failedCount = markers.count { it.aiStatus == Marker.AI_FAILED }
                val unsent = pendingCount + failedCount
                if (unsent > 0) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 只有"既有没发过的、又有失败的"时，才需要把两者拆开
                        if (failedCount > 0 && pendingCount > 0) {
                            val failedSource = remember { MutableInteractionSource() }
                            OutlinedButton(
                                onClick = { vm.resendFailed() },
                                interactionSource = failedSource,
                                modifier = Modifier.pressFeedback(
                                    failedSource,
                                    MotionSpec.PRESS_SCALE_WIDE,
                                ),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 14.dp,
                                ),
                            ) {
                                Text("只重发失败", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        val allSource = remember { MutableInteractionSource() }
                        Button(
                            onClick = { vm.resendAll() },
                            interactionSource = allSource,
                            modifier = Modifier.pressFeedback(allSource, MotionSpec.PRESS_SCALE_WIDE),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 16.dp,
                            ),
                        ) {
                            Text(
                                if (pendingCount > 0) "发送全部" else "全部重发",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        if (unsent >= 2) {
                            TextButton(onClick = { mergeMode = true; mergeSel.clear() }) {
                                Text("合并", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        // 窄屏上这行原本会被挤出屏幕：让计数可压缩并右对齐，而不是把按钮顶出去
                        Text(
                            "$unsent 个未发送",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (failedCount > 0 && pendingCount == 0) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // 这节课：把散落的标记串成一份提纲，并允许就整节课提问
            item {
                CourseDigestCard(
                    markerCount = markers.size,
                    digest = digest,
                    digestBusy = digestBusy,
                    digestDraft = digestDraft,
                    digestError = digestError,
                    chats = chats,
                    chatBusy = chatBusy,
                    chatDraft = chatDraft,
                    chatError = chatError,
                    onGenerate = { vm.generateDigest() },
                    onAsk = { vm.ask(it) },
                    onClearChat = { vm.clearChatHistory() },
                )
            }

            // 标记列表
            item {
                Row(
                    Modifier.fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (onlyReview) "待复习（" + visibleMarkers.size + "）" else "标记（按时间排序）",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    val totalCost = ModelPricing.format(
                        ModelPricing.costOf(turnsByMarker.values.flatten(), aiModel),
                    )
                    if (totalCost.isNotEmpty()) {
                        // 「累计」不是修饰：编辑、重新生成、合并都保留旧版本，
                        // 每个版本都是一次真实调用、真实扣费，所以这里加的是全部版本，
                        // 和各条标记"当前显示的那一版"之和对不上是正常的
                        Text(
                            "累计 $totalCost",
                            style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    val reviewCount = markers.count { it.needsReview }
                    // 已经在筛选态时必须保留这个 chip：取消最后一个星标后 reviewCount 归零，
                    // 若此时把 chip 一起藏了，用户就卡在空列表里退不出来
                    if (reviewCount > 0 || onlyReview) {
                        FilterChip(
                            selected = onlyReview,
                            onClick = { onlyReview = !onlyReview },
                            label = { Text("待复习 " + reviewCount) },
                        )
                    }
                }
            }
            if (markers.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Filled.PhotoCamera,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("本条录音没有标记", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "录音时拍的标记会出现在这里",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
            items(visibleMarkers, key = { it.id }) { marker ->
                MarkerCard(
                    marker = marker,
                    expanded = expandedMarkerId == marker.id,
                    turns = turnsByMarker[marker.id].orEmpty(),
                    streaming = streaming?.takeIf { it.markerId == marker.id },
                    onClick = {
                        if (mergeMode) {
                            if (mergeSel.contains(marker.id)) mergeSel.remove(marker.id)
                            else mergeSel.add(marker.id)
                        } else {
                            vm.jumpToMarker(marker)
                            expandedMarkerId = if (expandedMarkerId == marker.id) null else marker.id
                        }
                    },
                    onToggleExpand = {
                        expandedMarkerId = if (expandedMarkerId == marker.id) null else marker.id
                    },
                    onRegenerateTurn = { turnId -> vm.regenerateTurn(marker.id, turnId) },
                    onEditTurn = { turnId, text -> vm.editTurn(marker.id, turnId, text) },
                    onFollowUp = { q -> vm.followUp(marker.id, q) },
                    onStopTurn = { turnId -> vm.stopTurn(turnId) },
                    onResendMarker = { vm.resendOne(marker.id) },
                    onEditImage = { img ->
                        val src = java.io.File(img.filePath)
                        if (src.exists()) {
                            editingImage = img
                            cropLauncher.launch(existingFileUri(context, src) to newCropUri(context))
                        } else {
                            scope.launch { snackbar.showSnackbar("原图文件不在了，可能已被清理") }
                        }
                    },
                    onToggleReview = { vm.toggleReview(marker) },
                    costLabel = ModelPricing.format(
                        ModelPricing.costOf(turnsByMarker[marker.id].orEmpty(), aiModel),
                    ),
                    mergeMode = mergeMode,
                    mergeSelected = mergeSel.contains(marker.id),
                    onToggleMerge = {
                        if (mergeSel.contains(marker.id)) mergeSel.remove(marker.id)
                        else mergeSel.add(marker.id)
                    },
                    onEditPrompt = { promptTarget = marker },
                    onSelectVariantTurn = { turnId -> vm.selectVariant(marker.id, turnId) },
                    onSaveImage = { img ->
                        scope.launch {
                            val ctx = context
                            val ok = withContext(Dispatchers.IO) {
                                com.suitanglu.app.util.GallerySaver.save(ctx, img.filePath)
                            }
                            snackbar.showSnackbar(
                                if (ok) "已保存到相册" else "保存失败，请重试",
                            )
                        }
                    },
                    onOpenMerged = {
                        val headId = marker.mergedInto
                        if (headId != null) {
                            expandedMarkerId = headId
                            val idx = markers.indexOfFirst { it.id == headId }
                            if (idx >= 0) {
                                scope.launch {
                                    listState.animateScrollToItem(MARKER_ITEM_OFFSET + idx)
                                }
                            }
                        }
                    },
                    imageVersion = imageVersion,
                    loadImages = { vm.imagesOf(marker) },
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        val miniRec = rec
        AnimatedVisibility(
            visible = showMiniPlayer &&
                miniRec != null &&
                player != null &&
                miniRec.status != Recording.STATUS_AUDIO_DELETED,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(220)) { it } + fadeIn(tween(180)),
            exit = slideOutVertically(tween(160)) { it } + fadeOut(tween(120)),
        ) {
            if (miniRec != null) {
                MiniPlayer(
                    title = miniRec.title,
                    positionMs = position,
                    durationMs = duration.takeIf { it > 0 } ?: miniRec.durationMs,
                    markers = markers,
                    playing = playing,
                    speed = speed,
                    onTogglePlay = { vm.togglePlay() },
                    onSpeed = { showSpeedMenu = true },
                    onSeek = { vm.seekTo(it) },
                    onExpand = { scope.launch { listState.animateScrollToItem(0) } },
                )
            }
        }
        }
    }
}


/**
 * 这节课：把十几个互不相干的标记讲解串成一份提纲，并允许就整节课提问。
 *
 * 折叠时只占一行——它不是主操作，不该和播放器抢注意力；
 * 生成完成后自动展开一次，否则用户不知道已经好了。
 */
@Composable
private fun CourseDigestCard(
    markerCount: Int,
    digest: RecordingDigest?,
    digestBusy: Boolean,
    digestDraft: String,
    digestError: String?,
    chats: List<RecordingChat>,
    chatBusy: Boolean,
    chatDraft: String,
    chatError: String?,
    onGenerate: () -> Unit,
    onAsk: (String) -> Unit,
    onClearChat: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var question by rememberSaveable { mutableStateOf("") }
    val body = digest?.content

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable { expanded = !expanded }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "这节课",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    when {
                        digestBusy -> "归纳中…"
                        body != null -> "已归纳"
                        markerCount == 0 -> "还没有标记"
                        else -> "$markerCount 个标记"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }

            if (!expanded) return@Column

            Spacer(Modifier.height(12.dp))
            when {
                digestBusy && digestDraft.isBlank() -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "正在通读 $markerCount 个标记…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                digestBusy -> MarkdownView(digestDraft, Modifier.fillMaxWidth())
                body != null -> MarkdownView(body, Modifier.fillMaxWidth())
                else -> {
                    Text(
                        if (markerCount == 0) {
                            "先拍几张课件，再回来生成归纳。"
                        } else {
                            "把 $markerCount 个标记的讲解汇总成提纲、术语表和三个自测问题。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    // 展开交给人来触发：进入页面时不展开（原来靠"body 非空"推断，
                    // digest 异步加载完成那一刻也会触发，等于每次进来都是展开态）
                    Button(
                        onClick = {
                            expanded = true
                            onGenerate()
                        },
                        enabled = markerCount > 0,
                    ) {
                        Text("生成复习提纲")
                    }
                }
            }

            digestError?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // 有提纲之后才开放对话：没有材料时它只会编
            if (body != null) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))

                // 只渲染最近 20 条：每轮 AI 回答都是一个 WebView，
                // 整段历史全铺开的话，几十轮下来会同时存在几十个 WebView 实例
                val visibleChats = remember(chats) { chats.takeLast(20) }
                if (chats.size > visibleChats.size) {
                    Text(
                        "仅显示最近 ${visibleChats.size} 条，更早的已折叠",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                visibleChats.forEach { msg ->
                    val mine = msg.role == RecordingChat.ROLE_USER
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
                    ) {
                        Surface(
                            color = if (mine) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.widthIn(max = 300.dp),
                        ) {
                            if (mine) {
                                Text(
                                    msg.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(10.dp),
                                )
                            } else {
                                MarkdownView(msg.content, Modifier.padding(10.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (chatBusy && chatDraft.isBlank()) {
                    Text(
                        "正在思考…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                } else if (chatDraft.isNotBlank()) {
                    MarkdownView(chatDraft, Modifier.fillMaxWidth())
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = question,
                        onValueChange = { question = it },
                        placeholder = { Text("问这节课…") },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                        enabled = !chatBusy,
                    )
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            onAsk(question)
                            question = ""
                        },
                        enabled = !chatBusy && question.isNotBlank(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                    }
                }

                chatError?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (chats.isNotEmpty()) {
                    TextButton(onClick = onClearChat) { Text("清空对话") }
                }
            }
        }
    }
}

/**
 * 播放页顶栏。
 *
 * 修了两件一直存在但没被看见的事：
 * 1. 原来没有 `statusBarsPadding()`——在 edge-to-edge 下标题被状态栏压住，
 *    返回键的一部分还落在系统手势区里；
 * 2. 滚动时顶栏和内容之间没有任何分界，标题像浮在录音列表上。
 *    这里让背景从页面底色抬到卡片色、再淡入一条 1dp 细线，
 *    高度全程不变，所以列表不跳。
 *
 * 信息仍是最常回看的三项：哪节课、哪天、多长。
 */
@Composable
private fun PlaybackTopBar(
    title: String,
    subtitle: String?,
    scrolled: Boolean,
    onBack: () -> Unit,
) {
    val barColor by animateColorAsState(
        targetValue = if (scrolled) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.background
        },
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "topBarColor",
    )
    val hairline by animateFloatAsState(
        targetValue = if (scrolled) 1f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "topBarHairline",
    )
    val backSource = remember { MutableInteractionSource() }

    Column(Modifier.fillMaxWidth().background(barColor)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                interactionSource = backSource,
                modifier = Modifier.pressFeedback(backSource, MotionSpec.PRESS_SCALE_ICON),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier.size(21.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
        // 恒高 1dp：滚动时只改透明度，顶栏高度不变，所以列表不会跟着跳
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = hairline)),
        )
    }
}


/** 发送前编辑提示词的小对话框；合并发送和单条自定义共用 */
@Composable
private fun PromptDialog(
    title: String,
    hint: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8,
                    placeholder = { Text("留空使用全局提示词") },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/**
 * 标记卡的标题行：优先用用户自己的备注，其次取 AI 回复的第一段有效文字。
 * 旧实现按 20 个字符硬截断，经常切断词语；这里按"第一个非空行 + 去 Markdown 记号"取。
 */
private fun markerPreview(marker: Marker): String {
    marker.userText?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val answer = marker.aiResponse
    if (answer.isNullOrBlank()) {
        return if (marker.aiStatus == Marker.AI_FAILED) "讲解失败" else "照片标记"
    }
    val firstLine = answer.lineSequence()
        .map { it.trim().trimStart('#', '*', '-', '>', ' ') }
        .firstOrNull { it.isNotEmpty() }
        .orEmpty()
    return firstLine.ifEmpty { "照片标记" }
}

@Composable
fun MarkerProgressBar(
    durationMs: Long,
    positionMs: Long,
    markers: List<Marker>,
    pauseEvents: List<PauseEvent>,
    onSeek: (Long) -> Unit,
    onMarkerClick: (Marker) -> Unit,
    onPausePointClick: (PauseEvent) -> Unit,
) {
    val extra = MaterialTheme.recordColors
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val playedColor = MaterialTheme.colorScheme.primary
    val successColor = extra.success
    val failedColor = MaterialTheme.colorScheme.error
    val pendingColor = extra.pending
    val pauseColor = extra.pauseMark
    val density = androidx.compose.ui.platform.LocalDensity.current
    val barHeight = with(density) { 8.dp.toPx() }
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val badgeBg = MaterialTheme.colorScheme.inverseSurface
    val badgeText = MaterialTheme.colorScheme.inverseOnSurface

    val totalMs = durationMs.coerceAtLeast(1)

    // 按住/拖动轨道时播放头放大：手指落下的那一刻，画面要承认"你抓的是这个东西"。
    // 松手回到原尺寸比放大慢一倍——收得快会像被弹开。
    var dragging by remember { mutableStateOf(false) }
    val headScale by animateFloatAsState(
        targetValue = if (dragging) 1.35f else 1f,
        animationSpec = tween(
            durationMillis = if (dragging) 110 else 200,
            easing = FastOutSlowInEasing,
        ),
        label = "playhead",
    )

    // 相邻标记按比例 2% 聚合成簇，密集时不再糊成一串
    // 合并后的成员标记不再单独画点：它们已经并入组首的时间段里。
    // 组首标记带 mergedEndOffsetMs，单独画成一段区间；其余普通标记照旧聚成簇。
    val standalone = remember(markers) { markers.filter { it.mergedInto == null } }
    val mergedRanges = remember(standalone) { standalone.filter { it.mergedEndOffsetMs != null } }
    val plainMarkers = remember(standalone) { standalone.filter { it.mergedEndOffsetMs == null } }
    val clusters = remember(plainMarkers, totalMs) {
        val result = mutableListOf<MutableList<Marker>>()
        for (m in plainMarkers.sortedBy { it.offsetMs }) {
            val last = result.lastOrNull()
            if (last != null && (m.offsetMs - last.first().offsetMs).toFloat() / totalMs < 0.02f) {
                last.add(m)
            } else {
                result.add(mutableListOf(m))
            }
        }
        result
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            // 60dp：轨道居中偏下，给上方"多标记"角标留出完整绘制空间
            // （52dp 时角标圆心在 2.8dp，圆被画布顶边裁掉一半）
            .height(60.dp)
            // 拖动定位：先于 tap 挂载，横向移动超过阈值就由它接管，静止点击仍走 tap
            .pointerInput(totalMs) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        onSeek(((offset.x / size.width).coerceIn(0f, 1f) * totalMs).toLong())
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onDrag = { change, _ ->
                        onSeek(((change.position.x / size.width).coerceIn(0f, 1f) * totalMs).toLong())
                    },
                )
            }
            .pointerInput(clusters, pauseEvents, totalMs) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                    val ms = (fraction * totalMs).toLong()
                    // 取离点击最近的那一簇，而不是第一个落在容差内的。
                    // 标记密集时几簇的 4% 容差会互相重叠，firstOrNull 会稳定选中左边那个，
                    // 于是"点哪个都跳到第一个"——看起来就是点不准。
                    val cluster = clusters
                        .filter { c ->
                            val cx = (c.sumOf { it.offsetMs }.toFloat() / c.size) / totalMs
                            kotlin.math.abs(cx - fraction) < 0.04f
                        }
                        .minByOrNull { c ->
                            val cx = (c.sumOf { it.offsetMs }.toFloat() / c.size) / totalMs
                            kotlin.math.abs(cx - fraction)
                        }
                    if (cluster != null) {
                        onMarkerClick(
                            cluster.minByOrNull {
                                kotlin.math.abs(it.offsetMs.toFloat() / totalMs - fraction)
                            }!!,
                        )
                        return@detectTapGestures
                    }
                    // 合并时间段：点击落在 [offsetMs, mergedEndOffsetMs] 区间内就命中
                    val mergedHit = mergedRanges.firstOrNull { m ->
                        val end = m.mergedEndOffsetMs ?: m.offsetMs
                        val startFrac = (m.offsetMs.toFloat() / totalMs).coerceIn(0f, 1f)
                        val endFrac = (end.toFloat() / totalMs).coerceIn(startFrac, 1f)
                        fraction in startFrac..endFrac
                    }
                    if (mergedHit != null) {
                        onMarkerClick(mergedHit)
                        return@detectTapGestures
                    }
                    val pauseEvent = pauseEvents.firstOrNull {
                        val mxFraction = it.offsetMs.toFloat() / totalMs
                        kotlin.math.abs(mxFraction - fraction) < 0.04f
                    }
                    if (pauseEvent != null) {
                        onPausePointClick(pauseEvent)
                        return@detectTapGestures
                    }
                    onSeek(ms)
                }
            },
    ) {
        val y = size.height / 2 + 4.dp.toPx()
        // 分钟刻度（间隔足够时才画）
        val totalMinutes = (totalMs / 60_000).toInt()
        if (totalMinutes in 3..240) {
            val gapPx = size.width / totalMinutes
            if (gapPx > 20f) {
                val tickColor = trackColor.copy(alpha = 0.9f)
                for (i in 1 until totalMinutes) {
                    val x = gapPx * i
                    drawLine(
                        color = tickColor,
                        start = Offset(x, y + barHeight / 2 + 4.dp.toPx()),
                        end = Offset(x, y + barHeight / 2 + 9.dp.toPx()),
                        strokeWidth = 2f,
                    )
                }
            }
        }
        // 轨道
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, y - barHeight / 2),
            size = Size(size.width, barHeight),
            cornerRadius = CornerRadius(barHeight / 2),
        )
        // 已播放
        val playedWidth = size.width * (positionMs.toFloat() / totalMs).coerceIn(0f, 1f)
        drawRoundRect(
            color = playedColor,
            topLeft = Offset(0f, y - barHeight / 2),
            size = Size(playedWidth, barHeight),
            cornerRadius = CornerRadius(barHeight / 2),
        )
        // 暂停/打断点（菱形 + 描边）
        val borderStroke = 2.dp.toPx()
        pauseEvents.filter { it.type != PauseEvent.TYPE_RESUME }.forEach { event ->
            val x = size.width * (event.offsetMs.toFloat() / totalMs).coerceIn(0f, 1f)
            val r = barHeight * 1.3f
            rotate(45f, pivot = Offset(x, y)) {
                drawRect(
                    color = trackColor,
                    topLeft = Offset(x - r / 2 - borderStroke / 2, y - r / 2 - borderStroke / 2),
                    size = Size(r + borderStroke, r + borderStroke),
                )
                drawRect(
                    color = pauseColor,
                    topLeft = Offset(x - r / 2, y - r / 2),
                    size = Size(r, r),
                )
            }
        }
        // 标记簇（圆形 + 描边；多标记时加计数角标）
        // 合并标记：画成一段横跨 [offsetMs, mergedEndOffsetMs] 的区间，
        // 起止两端各一个实心点，中间是半透明填充，区别于普通单点标记
        mergedRanges.forEach { m ->
            val end = m.mergedEndOffsetMs ?: m.offsetMs
            val startFrac = (m.offsetMs.toFloat() / totalMs).coerceIn(0f, 1f)
            val endFrac = (end.toFloat() / totalMs).coerceIn(startFrac, 1f)
            val x0 = size.width * startFrac
            val x1 = size.width * endFrac
            val color = when {
                m.aiStatus == Marker.AI_FAILED -> failedColor
                m.aiStatus == Marker.AI_SUCCESS -> successColor
                else -> pendingColor
            }
            // 区间条画在轨道**下方**，不覆盖轨道本身：
            // 覆盖式填充会和"已播放部分"同色叠在一起，看不出边界；
            // 而且原来的阈值是 8dp，两张图隔得近时整个区间直接不画。
            if (x1 - x0 > 2.dp.toPx()) {
                val barY = y + barHeight * 1.9f
                drawRoundRect(
                    color = color.copy(alpha = 0.55f),
                    topLeft = Offset(x0, barY - 1.5.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(x1 - x0, 3.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx()),
                )
            }
            // 起止两个端点
            drawCircle(color = trackColor, radius = barHeight * 1.05f + borderStroke, center = Offset(x0, y))
            drawCircle(color = color, radius = barHeight * 1.05f, center = Offset(x0, y))
            drawCircle(color = Color.White, radius = barHeight * 0.45f, center = Offset(x0, y))
            if (x1 - x0 > barHeight * 3) {
                drawCircle(color = trackColor, radius = barHeight * 1.05f + borderStroke, center = Offset(x1, y))
                drawCircle(color = color, radius = barHeight * 1.05f, center = Offset(x1, y))
                drawCircle(color = Color.White, radius = barHeight * 0.45f, center = Offset(x1, y))
            }
        }
        clusters.forEach { c ->
            val cxFraction = (c.sumOf { it.offsetMs }.toFloat() / c.size) / totalMs
            val x = size.width * cxFraction.coerceIn(0f, 1f)
            val color = when {
                c.any { it.aiStatus == Marker.AI_FAILED } -> failedColor
                c.all { it.aiStatus == Marker.AI_SUCCESS } -> successColor
                else -> pendingColor
            }
            drawCircle(color = trackColor, radius = barHeight * 1.05f + borderStroke, center = Offset(x, y))
            drawCircle(color = color, radius = barHeight * 1.05f, center = Offset(x, y))
            drawCircle(color = Color.White, radius = barHeight * 0.45f, center = Offset(x, y))
            if (c.size > 1) {
                val badgeR = 8.dp.toPx()
                val badgeCenter = Offset(x, y - barHeight * 1.4f - badgeR)
                drawCircle(color = badgeBg, radius = badgeR, center = badgeCenter)
                val text = "${c.size}"
                val layout = textMeasurer.measure(
                    text,
                    androidx.compose.ui.text.TextStyle(
                        fontSize = 10.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = badgeText,
                    ),
                )
                drawText(
                    layout,
                    topLeft = Offset(
                        badgeCenter.x - layout.size.width / 2f,
                        badgeCenter.y - layout.size.height / 2f,
                    ),
                )
            }
        }
        // 播放头（外圈光晕 + 实心）
        // 光晕收在 2.1 倍轨道高（原来 2.6 倍，在 8dp 轨道上直径 41dp，比播放键还抢）
        drawCircle(
            color = playedColor.copy(alpha = 0.18f),
            radius = barHeight * 2.1f * headScale,
            center = Offset(playedWidth, y),
        )
        drawCircle(
            color = playedColor,
            radius = barHeight * 1.8f * headScale,
            center = Offset(playedWidth, y),
        )
        drawCircle(
            color = Color.White,
            radius = barHeight * 0.7f * headScale,
            center = Offset(playedWidth, y),
        )
    }
}

@Composable
private fun MarkerCard(
    marker: Marker,
    expanded: Boolean,
    turns: List<MarkerTurn>,
    streaming: StreamingState?,
    onClick: () -> Unit,
    onToggleExpand: () -> Unit,
    onRegenerateTurn: (Long) -> Unit,
    onEditTurn: (Long, String) -> Unit,
    onFollowUp: (String) -> Unit,
    onStopTurn: (Long) -> Unit,
    onResendMarker: () -> Unit,
    onEditImage: (MarkerImage) -> Unit,
    onSaveImage: (MarkerImage) -> Unit,
    onToggleReview: () -> Unit,
    /** 这一条讲解花了多少钱；空串表示不展示（非 DeepSeek 端点或还没生成过） */
    costLabel: String,
    mergeMode: Boolean,
    mergeSelected: Boolean,
    onToggleMerge: () -> Unit,
    onEditPrompt: () -> Unit,
    onOpenMerged: () -> Unit,
    /** 切到某一轮的另一版（编辑/重新生成留下的历史版本） */
    onSelectVariantTurn: (Long) -> Unit,
    /** 改过图之后自增，用来让已展开的卡片重新拉一次图片列表 */
    imageVersion: Int,
    loadImages: suspend (Marker) -> List<MarkerImage>,
) {
    var images by remember(marker.id) { mutableStateOf<List<MarkerImage>>(emptyList()) }
    var fullImage by remember { mutableStateOf<MarkerImage?>(null) }
    var followUpText by remember { mutableStateOf("") }
    var editingTurnId by remember { mutableStateOf<Long?>(null) }
    var editDraft by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotion()
    // 展开箭头转 180° 而不是瞬间翻转：它同时是"这里能展开"的提示
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "chevron",
    )
    LaunchedEffect(expanded, imageVersion) {
        if (expanded) images = loadImages(marker)
    }
    val anySending = turns.any { it.status == MarkerTurn.TURN_SENDING }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        // 展开用 tween 而不是默认的 spring：卡片高度本来就跳得远，
        // 再叠一层回弹会让人以为点错了。240ms 接近"抽屉阻尼"的手感。
        Column(
            Modifier
                .padding(16.dp)
                .animateContentSize(
                    animationSpec = tween(240, easing = FastOutSlowInEasing),
                ),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                if (mergeMode) {
                    Icon(
                        if (mergeSelected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = if (mergeSelected) "取消选择" else "选中",
                        tint = if (mergeSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(top = 2.dp, end = 8.dp).size(20.dp),
                    )
                }
                // 时间是小节号：等宽、主色，扫列表时先看这一列
                Text(
                    Formatters.clockTime(marker.offsetMs),
                    style = MaterialTheme.typography.titleSmall.tabularNumbers(),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.alignByBaseline(),
                )
                if (costLabel.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    // 基线对齐而不是手工 padding：两行字号不同，固定 top 在不同字体缩放下会错开
                    Text(
                        costLabel,
                        style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
                Spacer(Modifier.width(10.dp))
                // 展开后不再截断：折叠时是摘要，2 行够用；展开是"我要看全"，
                // 这时候还留省略号就是 bug。没有备注时标题退成"照片标记"，
                // 别再重复一遍下方已经完整显示的 AI 回答
                Text(
                    if (expanded && marker.userText.isNullOrBlank()) "照片标记" else markerPreview(marker),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                StatusChip(if (anySending) Marker.AI_SENDING else marker.aiStatus)
                // 待复习：展开时始终可见（可以标），收起时只在已标记时显示（提醒）
                if (expanded || marker.needsReview) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(MaterialTheme.shapes.small)
                            .clickable(
                                onClickLabel = if (marker.needsReview) "取消待复习" else "标记为待复习",
                                onClick = onToggleReview,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (marker.needsReview) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = null,
                            tint = if (marker.needsReview) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 2.dp, top = 2.dp)
                        .size(18.dp)
                        .graphicsLayer { rotationZ = chevronRotation },
                )
            }
            if (marker.aiStatus == Marker.AI_FAILED && marker.aiError != null && !expanded) {
                Text(
                    "失败原因：${marker.aiError}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                if (images.isNotEmpty()) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        images.forEach { img ->
                            AsyncImage(
                                model = img.filePath,
                                contentDescription = null,
                                modifier = Modifier
                                    .width(120.dp)
                                    .height(90.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    // 图片边缘在深色底上会糊开，加一道中性细描边收住轮廓
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                                        RoundedCornerShape(10.dp),
                                    )
                                    .clickable { fullImage = img },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                marker.userText?.let {
                    Text("备注：$it", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                }

                // ===== 多轮对话 =====
                if (turns.isEmpty()) {
                    if (marker.mergedInto != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "已并入合并讲解",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = onOpenMerged) { Text("查看") }
                        }
                    } else when (marker.aiStatus) {
                        Marker.AI_PENDING -> Text(
                            "待发送",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Marker.AI_SENDING -> Text(
                            "正在发送 AI 讲解请求…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Marker.AI_FAILED -> {
                            Text(
                                "生成失败：${marker.aiError ?: "未知原因"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            TextButton(onClick = onResendMarker) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("重新生成")
                            }
                        }
                        else -> {
                            // 无轮次记录的历史成功数据：直接展示旧回复
                            Text("AI 讲解", style = MaterialTheme.typography.labelMedium)
                            Text(marker.aiResponse.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = onResendMarker) {
                                Text("重新生成", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    // 发送前就能改：钉住这条要 AI 怎么讲，而不是发送后再去改问题
                    if (marker.mergedInto == null) {
                        TextButton(onClick = onEditPrompt) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("发送提示词", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                } else {
                    // 同一 orderIndex 可能有多版（编辑/重新生成都会新建一版），
                    // 只渲染生效那版，其余靠「1/2」切
                    val turnGroups = turns.groupBy { it.orderIndex }.toSortedMap().values.toList()
                    turnGroups.forEachIndexed { groupIdx, versions ->
                        val live = versions.firstOrNull { it.active } ?: versions.last()
                        val liveIdx = versions.indexOfFirst { it.id == live.id }.coerceAtLeast(0)
                        TurnView(
                            turn = live,
                            streaming = streaming?.takeIf { it.turnId == live.id },
                            onStopTurn = { onStopTurn(live.id) },
                            onRegenerate = { onRegenerateTurn(live.id) },
                            onEdit = {
                                editingTurnId = live.id
                                editDraft = live.question.orEmpty()
                            },
                            variantIndex = liveIdx + 1,
                            variantTotal = versions.size,
                            onSelectVariant = { target ->
                                versions.getOrNull(target)?.let { onSelectVariantTurn(it.id) }
                            },
                        )
                        if (groupIdx < turnGroups.lastIndex) Spacer(Modifier.height(8.dp))
                    }

                    // 编辑提问：用弹窗，不再在轮次下方展开一个内联输入框。
                    // 内联会把整张卡片撑高、把下面的轮次推走，编辑时反而看不到上下文；
                    // 弹窗固定在屏幕中央，键盘弹起也不挤压列表。
                    editingTurnId?.let { turnId ->
                        AppAlertDialog(
                            title = "编辑提问",
                            message = "改完会另起一版，旧版保留，可用 1/2 切回。",
                            confirmLabel = "重新生成",
                            confirmEnabled = editDraft.isNotBlank(),
                            onConfirm = {
                                onEditTurn(turnId, editDraft.trim())
                                editingTurnId = null
                            },
                            onDismissRequest = { editingTurnId = null },
                        ) {
                            OutlinedTextField(
                                value = editDraft,
                                onValueChange = { editDraft = it },
                                label = { Text("发送给 AI 的内容") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                maxLines = 8,
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // ===== 追问输入 =====
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = followUpText,
                            onValueChange = { followUpText = it },
                            label = { Text("追问…") },
                            modifier = Modifier.weight(1f),
                            enabled = !anySending,
                            minLines = 1,
                            maxLines = 4,
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                onFollowUp(followUpText.trim())
                                followUpText = ""
                            },
                            enabled = !anySending && followUpText.isNotBlank(),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送追问")
                        }
                    }
                }
            }
        }
    }
    fullImage?.let { img ->
        // 看图模式：黑底、按比例适应、点任意处或右上角关闭。
        // 原来直接塞一张全宽图，长截图会被压扁，也没有明显的关闭方式。
        // 进出场都做：图片从 0.93 展开、蒙版同步加深，关闭时先收再消失。
        Dialog(
            onDismissRequest = { fullImage = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            var shown by remember(img.filePath) { mutableStateOf(false) }
            LaunchedEffect(img.filePath) { shown = true }
            val close: () -> Unit = {
                shown = false
                scope.launch {
                    // 关掉动画时不白等这一下
                    if (!reducedMotion) delay(MotionSpec.DIALOG_EXIT_MS.toLong())
                    fullImage = null
                }
            }
            val backdrop by animateFloatAsState(
                targetValue = if (shown) 1f else 0f,
                animationSpec = tween(
                    if (shown) 200 else MotionSpec.DIALOG_EXIT_MS,
                    easing = FastOutSlowInEasing,
                ),
                label = "viewerBackdrop",
            )
            val imageScale by animateFloatAsState(
                targetValue = if (shown) 1f else 0.93f,
                animationSpec = tween(
                    if (shown) 240 else MotionSpec.DIALOG_EXIT_MS,
                    easing = FastOutSlowInEasing,
                ),
                label = "viewerScale",
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.94f * backdrop))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "关闭",
                        onClick = close,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // 双指缩放 + 放大后拖动：板书字小的时候全靠这个
                com.suitanglu.app.ui.components.ZoomableImage(
                    model = img.filePath,
                    contentDescription = null,
                    background = androidx.compose.ui.graphics.Color.Transparent,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 48.dp)
                        .graphicsLayer {
                            scaleX = imageScale
                            scaleY = imageScale
                            alpha = backdrop
                        },
                )
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 课后修图入口：就在看图的地方裁，比回列表里找编辑按钮顺手
                    IconButton(
                        onClick = {
                            onEditImage(img)
                            fullImage = null
                        },
                    ) {
                        Icon(
                            Icons.Filled.Crop,
                            contentDescription = "裁剪这张图",
                            tint = Color.White,
                        )
                    }
                    IconButton(
                        onClick = { onSaveImage(img) },
                    ) {
                        Icon(
                            Icons.Outlined.Download,
                            contentDescription = "保存到相册",
                            tint = Color.White,
                        )
                    }
                    IconButton(onClick = close) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "关闭",
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}

/** 单轮问答：问题气泡（可编辑）+ 回答（markdown/公式渲染，可重新生成/停止） */
@Composable
private fun TurnView(
    turn: MarkerTurn,
    streaming: StreamingState?,
    onStopTurn: () -> Unit,
    onRegenerate: () -> Unit,
    onEdit: () -> Unit,
    /** 当前是这一轮的第几版（1 起） */
    variantIndex: Int = 1,
    /** 这一轮一共几版 */
    variantTotal: Int = 1,
    /** 切到指定版本，参数是 0 起的索引 */
    onSelectVariant: (Int) -> Unit = {},
) {
    Column(Modifier.fillMaxWidth()) {
        // 问题气泡（首轮为实际发送的提示词，可编辑）
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        RoundedCornerShape(10.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    turn.question ?: (if (turn.orderIndex == 0) "（详细讲解本页内容）" else ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        // 版本切换 + 编辑：编辑/重新生成都会新建一版而不是覆盖，所以这里能来回切
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (variantTotal > 1) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "‹",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (variantIndex > 1) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = variantIndex > 1) { onSelectVariant(variantIndex - 2) }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                    Text(
                        "$variantIndex/$variantTotal",
                        style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "›",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (variantIndex < variantTotal) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = variantIndex < variantTotal) { onSelectVariant(variantIndex) }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
            TextButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = null, Modifier.size(14.dp))
                Spacer(Modifier.width(2.dp))
                Text("编辑", style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(6.dp))
        // 回答区
        when {
            turn.status == MarkerTurn.TURN_SENDING && streaming != null -> {
                Text(
                    streaming.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("生成中…", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onStopTurn) {
                        Icon(Icons.Filled.Stop, contentDescription = null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("停止生成")
                    }
                }
            }
            turn.status == MarkerTurn.TURN_SENDING -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("正在生成…", style = MaterialTheme.typography.labelMedium)
                }
            }
            turn.status == MarkerTurn.TURN_SUCCESS -> {
                MarkdownView(text = turn.answer.orEmpty())
                Spacer(Modifier.height(2.dp))
                UsageHint(turn)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onRegenerate, enabled = turn.status != MarkerTurn.TURN_SENDING) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, Modifier.size(14.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("重新生成", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            turn.status == MarkerTurn.TURN_FAILED -> {
                Text(
                    "生成失败：${turn.error ?: "未知原因"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onRegenerate) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("重试")
                }
            }
        }
    }
}

/** token 用量与状态提示行 */
@Composable
private fun UsageHint(turn: MarkerTurn) {
    val hints = mutableListOf<String>()
    if (turn.promptTokens > 0) {
        hints.add(
            if (turn.cacheHitTokens > 0) {
                "输入 ${turn.promptTokens}（缓存命中 ${turn.cacheHitTokens}）· 输出 ${turn.completionTokens}"
            } else {
                "输入 ${turn.promptTokens} · 输出 ${turn.completionTokens}"
            },
        )
    }
    when (turn.finishReason) {
        "stopped_by_user" -> hints.add("已手动停止生成，可追问「继续」接上")
        "length" -> hints.add("已达到最大输出长度被截断，可追问「继续」")
    }
    if (hints.isEmpty()) return
    Text(
        hints.joinToString("；"),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun StatusChip(status: String) {
    val extra = MaterialTheme.recordColors
    val (label, fg, bg) = when (status) {
        Marker.AI_SUCCESS -> Triple("成功", extra.success, extra.successContainer)
        Marker.AI_FAILED -> Triple(
            "失败",
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.errorContainer,
        )
        Marker.AI_SENDING -> Triple("发送中", extra.pending, extra.pendingContainer)
        else -> Triple(
            "待发送",
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
    Row(
        modifier = Modifier
            .background(bg, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 颜色 + 圆点双通道：不依赖颜色也能分辨状态
        Box(Modifier.size(6.dp).background(fg, CircleShape))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

/**
 * 倍速按钮。
 * 倍速是"设置"不是"操作"，所以用低饱和胶囊、靠右放，不跟播放键争中间那个位置。
 * 46×40 的可点区域，视觉上仍是小胶囊；切换倍速时数字交叉淡入，而不是直接换一个字符串。
 */
@Composable
private fun SpeedButton(
    speed: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressFeedback(source, 0.94f)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(
                interactionSource = source,
                indication = LocalIndication.current,
                onClickLabel = "选择播放速度",
                onClick = onClick,
            )
            .heightIn(min = 40.dp)
            .widthIn(min = 46.dp)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = speed,
            // 缺 SizeTransform 时容器尺寸会瞬跳（"1×" ↔ "1.25×" 宽度不同），
            // 加上之后胶囊会在 130ms 内平滑变宽；clip=false 防止数字被裁
            transitionSpec = {
                (fadeIn(tween(130)) togetherWith fadeOut(tween(90)))
                    .using(SizeTransform(clip = false))
            },
            contentAlignment = Alignment.Center,
            label = "speedValue",
        ) { value ->
            Text(
                formatSpeed(value),
                style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * 倍速文案：1.0 → "1×"，1.25 → "1.25×"。
 * 用真正的乘号（×）而不是小写字母 x——后者在中文字体里会掉到基线以下，看着像笔误。
 */
private fun formatSpeed(value: Float): String {
    val text = if (value == value.toInt().toFloat()) {
        value.toInt().toString()
    } else {
        value.toString().trimEnd('0').trimEnd('.')
    }
    return "$text×"
}

/** 迷你条上的细进度条：带标记点、可点可拖。高度 16dp 只为手指服务，视觉上仍是 3dp 的线 */
@Composable
private fun MiniProgressBar(
    positionMs: Long,
    durationMs: Long,
    markers: List<Marker>,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extra = MaterialTheme.recordColors
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val playedColor = MaterialTheme.colorScheme.primary
    val dotBorder = MaterialTheme.colorScheme.surface
    val failColor = MaterialTheme.colorScheme.error
    val total = durationMs.coerceAtLeast(1)
    val fraction = (positionMs.toFloat() / total).coerceIn(0f, 1f)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(16.dp)
            .pointerInput(total) {
                detectTapGestures { o ->
                    onSeek(((o.x / size.width).coerceIn(0f, 1f) * total).toLong())
                }
            }
            .pointerInput(total) {
                detectHorizontalDragGestures { change, _ ->
                    onSeek(((change.position.x / size.width).coerceIn(0f, 1f) * total).toLong())
                }
            },
    ) {
        val lineH = 3.dp.toPx()
        val y = size.height / 2
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, y - lineH / 2),
            size = Size(size.width, lineH),
            cornerRadius = CornerRadius(lineH / 2),
        )
        drawRoundRect(
            color = playedColor,
            topLeft = Offset(0f, y - lineH / 2),
            size = Size(size.width * fraction, lineH),
            cornerRadius = CornerRadius(lineH / 2),
        )
        // 合并成员不画点（已并入组首）；合并组首画成一段小横条
        val standalone = markers.filter { it.mergedInto == null }
        standalone.filter { it.mergedEndOffsetMs != null }.forEach { m ->
            val end = m.mergedEndOffsetMs ?: m.offsetMs
            val startFrac = (m.offsetMs.toFloat() / total).coerceIn(0f, 1f)
            val endFrac = (end.toFloat() / total).coerceIn(startFrac, 1f)
            val x0 = size.width * startFrac
            val x1 = size.width * endFrac
            val c = when (m.aiStatus) {
                Marker.AI_SUCCESS -> extra.success
                Marker.AI_FAILED -> failColor
                else -> extra.pending
            }
            if (x1 - x0 > 1.dp.toPx()) {
                drawRoundRect(
                    color = c.copy(alpha = 0.35f),
                    topLeft = Offset(x0, y - lineH),
                    size = Size(x1 - x0, lineH * 2),
                    cornerRadius = CornerRadius(lineH),
                )
            }
            drawCircle(color = c, radius = 2.2.dp.toPx(), center = Offset(x0, y))
        }
        standalone.filter { it.mergedEndOffsetMs == null }.forEach { m ->
            val x = (m.offsetMs.toFloat() / total).coerceIn(0f, 1f) * size.width
            val c = when (m.aiStatus) {
                Marker.AI_SUCCESS -> extra.success
                Marker.AI_FAILED -> failColor
                else -> extra.pending
            }
            // 先描一圈底色再画点：压在已播放部分上时不会糊成一片
            drawCircle(
                color = dotBorder,
                radius = 3.2.dp.toPx(),
                center = Offset(x, y),
            )
            drawCircle(color = c, radius = 2.2.dp.toPx(), center = Offset(x, y))
        }
    }
}

/**
 * 迷你播放条：播放器滚出屏幕后接管控制。
 * 顶边一条细进度条不占高度，点整行回到顶部看大图。
 */
@Composable
private fun MiniPlayer(
    title: String,
    positionMs: Long,
    durationMs: Long,
    markers: List<Marker>,
    playing: Boolean,
    speed: Float,
    onTogglePlay: () -> Unit,
    onSpeed: () -> Unit,
    onSeek: (Long) -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 不要再加 navigationBarsPadding()：外层 Box 的 Scaffold padding 已经让出了导航栏高度，
    // 两处都加会让这条播放器向上飘出一个导航栏的距离，底下的点击区还变成死区
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onExpand),
    ) {
        MiniProgressBar(
            positionMs = positionMs,
            durationMs = durationMs,
            markers = markers,
            onSeek = onSeek,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val miniPlaySource = remember { MutableInteractionSource() }
            FilledIconButton(
                onClick = onTogglePlay,
                interactionSource = miniPlaySource,
                modifier = Modifier
                    .size(40.dp)
                    .pressFeedback(miniPlaySource, MotionSpec.PRESS_SCALE_ICON),
            ) {
                AnimatedContent(
                    targetState = playing,
                    transitionSpec = {
                        (fadeIn(tween(140)) + scaleIn(tween(140), initialScale = 0.80f)) togetherWith
                            (fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.80f))
                    },
                    contentAlignment = Alignment.Center,
                    label = "miniPlayPause",
                ) { isPlaying ->
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        modifier = Modifier
                            .size(20.dp)
                            .offset(x = if (isPlaying) 0.dp else 0.5.dp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${Formatters.clockTime(positionMs)} / ${Formatters.clockTime(durationMs)}",
                    style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SpeedButton(speed = speed, onClick = onSpeed, compact = true)
        }
    }
}
