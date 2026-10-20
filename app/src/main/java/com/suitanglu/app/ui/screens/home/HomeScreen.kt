package com.suitanglu.app.ui.screens.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suitanglu.app.AppGraph
import com.suitanglu.app.data.Formatters
import com.suitanglu.app.data.db.Recording
import com.suitanglu.app.service.RecordingBus
import com.suitanglu.app.service.RecordingService
import com.suitanglu.app.ui.theme.recordColors
import com.suitanglu.app.ui.theme.tabularNumbers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    hasNotificationPermission: Boolean,
    onRequestPermissions: () -> Unit,
    onStartRecording: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRecording: (Long) -> Unit,
    onOpenRecycle: () -> Unit,
) {
    val vm: HomeViewModel = viewModel()
    val context = LocalContext.current
    val container = AppGraph.get(context)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val sections by vm.sections.collectAsState()
    val tipShown by vm.firstDeleteTipShown.collectAsState()
    // 只在空↔非空跳变时更新，避免录音计时器每 500ms 触发整页重组
    // （不能用 derivedStateOf 包 StateFlow.value：StateFlow 不是快照状态，不会触发重算）
    val isRecording by remember {
        RecordingBus.state.map { it != null }.distinctUntilChanged()
    }.collectAsState(initial = RecordingBus.state.value != null)
    val recoveredCount by container.recordingRepository.recoveredCount.collectAsState()
    val courses by vm.courses.collectAsState()
    val courseFilter by vm.courseFilter.collectAsState()

    LaunchedEffect(recoveredCount) {
        recoveredCount?.let {
            snackbar.showSnackbar("已恢复 $it 条因系统中断结束的录音")
            container.recordingRepository.clearRecovered()
        }
    }

    var pendingDelete by remember { mutableStateOf<Recording?>(null) }
    var pendingAudioOnly by remember { mutableStateOf<Recording?>(null) }
    var renameTarget by remember { mutableStateOf<Recording?>(null) }
    var renameText by remember { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<Recording?>(null) }
    var showNoPermissionHint by remember { mutableStateOf(false) }

    // 长按进入的批量选择模式
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    val allRecordingIds = sections.flatMap { it.items }.map { it.recording.id }.toSet()

    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }

    // 授权完成后自动清除提示
    LaunchedEffect(hasNotificationPermission) {
        if (hasNotificationPermission) showNoPermissionHint = false
    }

    var exportTargetId by remember { mutableStateOf(0L) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val id = exportTargetId
        if (uri != null && id > 0) {
            scope.launch {
                val result = container.backupManager.exportSingle(id, uri) { }
                snackbar.showSnackbar(
                    if (result.isSuccess) "已导出单条录音" else "导出失败：${result.exceptionOrNull()?.message}",
                )
            }
        }
        exportTargetId = 0
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("已选 ${selectedIds.size} 项") },
                    navigationIcon = {
                        IconButton(onClick = { exitSelection() }) {
                            Icon(Icons.Filled.Close, contentDescription = "退出选择")
                        }
                    },
                    actions = {
                        TextButton(onClick = { selectedIds = allRecordingIds }) { Text("全选") }
                        TextButton(
                            enabled = selectedIds.isNotEmpty(),
                            onClick = {
                                vm.deleteToRecycleMany(selectedIds)
                                val n = selectedIds.size
                                exitSelection()
                                scope.launch { snackbar.showSnackbar("已将 $n 条录音移入回收站") }
                            },
                        ) { Text("移入回收站") }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("随堂录", style = MaterialTheme.typography.titleLarge) },
                    actions = {
                        IconButton(onClick = onOpenRecycle) {
                            Icon(Icons.Filled.Delete, contentDescription = "回收站")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "设置")
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (isRecording) {
                        // 正在录音：回录音页
                        onStartRecording()
                    } else {
                        if (hasNotificationPermission || android.os.Build.VERSION.SDK_INT < 33) {
                            onStartRecording()
                        } else {
                            showNoPermissionHint = true
                            onRequestPermissions()
                        }
                    }
                },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(if (isRecording) "返回录音中" else "开始录音") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (isRecording) {
                RecordingBanner(onClick = { onStartRecording() })
            }
            if (showNoPermissionHint) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Text(
                        "需要通知权限以显示录音常驻通知，请允许后在通知栏查看录音状态。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            // 课程筛选：只列出真正出现过的课程，避免空课程占位
            if (courses.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = courseFilter == null,
                        onClick = { vm.setCourseFilter(null) },
                        label = { Text("全部") },
                    )
                    courses.forEach { course ->
                        val active = courseFilter == course
                        FilterChip(
                            selected = active,
                            // 再点一次已选中的课程 = 取消筛选，省一次回「全部」
                            onClick = { vm.setCourseFilter(if (active) null else course) },
                            label = { Text(course, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .background(courseColor(course) ?: Color.Transparent, CircleShape),
                                )
                            },
                        )
                    }
                }
            }
            if (sections.isEmpty() && courseFilter != null) {
                // 该课程下暂时没有录音：给一个退出口，而不是复用全局空状态
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("「$courseFilter」下没有录音", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "换一门课，或查看全部录音。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { vm.setCourseFilter(null) }) { Text("查看全部") }
                }
            } else if (sections.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Audiotrack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("还没有录音", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "上课时点「开始录音」，遇到重点拍照标记。\n录音结束后会自动把图片发给 AI 讲解。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
                    for (section in sections) {
                        stickyHeader(key = "header_${section.label}") {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                Text(
                                    section.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "${section.items.size} 条",
                                    style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(section.items, key = { it.recording.id }) { item ->
                            RecordingCard(
                                item = item,
                                selectionMode = selectionMode,
                                selected = item.recording.id in selectedIds,
                                onToggleSelect = {
                                    val id = item.recording.id
                                    selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                                },
                                onClick = {
                                    // 录音中的条目没有可回放的音频，回录音页
                                    if (item.recording.status == Recording.STATUS_RECORDING) {
                                        onStartRecording()
                                    } else {
                                        onOpenRecording(item.recording.id)
                                    }
                                },
                                onLongPress = {
                                    // 长按进入批量选择并选中该条
                                    selectionMode = true
                                    selectedIds = selectedIds + item.recording.id
                                },
                                menuVisible = menuFor?.id == item.recording.id,
                                onDismissMenu = { menuFor = null },
                                onMenuClick = { menuFor = item.recording },
                                onRename = {
                                    renameTarget = item.recording
                                    renameText = item.recording.title
                                    menuFor = null
                                },
                                onDelete = {
                                    menuFor = null
                                    if (tipShown) {
                                        // 提醒过一次后直接进回收站（可恢复，无需每次确认）
                                        vm.deleteToRecycle(item.recording.id)
                                    } else {
                                        pendingDelete = item.recording
                                    }
                                },
                                onDeleteAudioOnly = {
                                    pendingAudioOnly = item.recording
                                    menuFor = null
                                },
                                onExport = {
                                    exportTargetId = item.recording.id
                                    exportLauncher.launch("recording_${item.recording.id}.zip")
                                    menuFor = null
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    // 第一次删除提醒
    pendingDelete?.let { rec ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除录音") },
            text = { Text("「${rec.title}」将移入回收站，可前往回收站恢复或彻底删除。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteToRecycle(rec.id)
                    vm.markDeleteTipShown()
                    pendingDelete = null
                }) { Text("移入回收站") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }

    // 仅删除录音确认
    pendingAudioOnly?.let { rec ->
        AlertDialog(
            onDismissRequest = { pendingAudioOnly = null },
            title = { Text("仅删除录音文件") },
            text = { Text("只删除「${rec.title}」的音频文件，标记、图片和 AI 回复将保留。此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteAudioOnly(rec.id)
                    pendingAudioOnly = null
                }) { Text("仅删除音频") }
            },
            dismissButton = { TextButton(onClick = { pendingAudioOnly = null }) { Text("取消") } },
        )
    }

    // 重命名
    renameTarget?.let { rec ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名录音") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("展示名称") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.rename(rec.id, renameText) { ok ->
                        scope.launch {
                            snackbar.showSnackbar(if (ok) "已重命名" else "重命名失败")
                        }
                    }
                    renameTarget = null
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingCard(
    item: HomeItem,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    menuVisible: Boolean,
    onDismissMenu: () -> Unit,
    onMenuClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDeleteAudioOnly: () -> Unit,
    onExport: () -> Unit,
) {
    val rec = item.recording
    val isRecording = rec.status == Recording.STATUS_RECORDING
    val audioDeleted = rec.status == Recording.STATUS_AUDIO_DELETED
    val shape = MaterialTheme.shapes.large
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                },
            )
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelect() else onClick() },
                onLongClick = { if (!selectionMode) onLongPress() },
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = when {
                selected -> MaterialTheme.colorScheme.primaryContainer
                isRecording -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                else -> MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 0.dp else 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) {
                    Icon(
                        if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = if (selected) "已选中" else "未选中",
                        tint = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                } else {
                    // 课程色点：同课程同色，扫一眼对上课
                    val dotColor = courseColor(rec.courseName)
                    if (dotColor != null) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .background(dotColor, CircleShape),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                }
                Text(
                    rec.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!selectionMode) {
                    Box {
                        IconButton(onClick = onMenuClick, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = "更多操作",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        DropdownMenu(expanded = menuVisible, onDismissRequest = onDismissMenu) {
                            DropdownMenuItem(
                                text = { Text("重命名") },
                                leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, null) },
                                onClick = onRename,
                            )
                            DropdownMenuItem(
                                text = { Text("删除到回收站") },
                                leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                onClick = onDelete,
                            )
                            DropdownMenuItem(text = { Text("仅删除录音文件") }, onClick = onDeleteAudioOnly)
                            DropdownMenuItem(
                                text = { Text("导出单条录音") },
                                leadingIcon = { Icon(Icons.Filled.IosShare, null) },
                                onClick = onExport,
                            )
                        }
                    }
                }
                if (isRecording) {
                    RecordingPulseDot()
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "录制中",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else if (audioDeleted) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                buildString {
                    append(Formatters.timeOnly(rec.startTime))
                    append(" · ")
                    append(Formatters.duration(rec.durationMs))
                    if (!audioDeleted) {
                        append(" · ")
                        append(
                            when {
                                item.markerTotal == 0 -> "无标记"
                                item.markerFailed > 0 ->
                                    "${item.markerTotal} 标记 · ${item.markerSuccess} 成功 ${item.markerFailed} 失败"
                                else -> "${item.markerTotal} 标记"
                            },
                        )
                    }
                },
                style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                color = if (isRecording) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.markerFailed > 0) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "有标记未发送，可重发",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** 录制中的小红点（呼吸动画） */
@Composable
private fun RecordingPulseDot() {
    val pulse = rememberInfiniteTransition(label = "dot")
    val alpha by pulse.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "dotAlpha",
    )
    Box(
        Modifier
            .size(8.dp)
            .background(MaterialTheme.colorScheme.error.copy(alpha = alpha), CircleShape),
    )
}

/** 课程名 → 稳定颜色（同课程同色；课程名为空时不显示色点） */
@Composable
private fun courseColor(name: String?): Color? {
    if (name.isNullOrBlank()) return null
    val palette = MaterialTheme.recordColors.coursePalette
    return palette[kotlin.math.abs(name.hashCode()) % palette.size]
}

/** 录音中首页顶部的横幅卡：点击回录音页；内部自行收集状态，避免整页随计时器重组 */
@Composable
private fun RecordingBanner(onClick: () -> Unit) {
    val state by RecordingBus.state.collectAsState()
    val s = state ?: return
    val pulse = rememberInfiniteTransition(label = "pulse")
    val dotAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "dotAlpha",
    )
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(
                        MaterialTheme.colorScheme.error.copy(
                            alpha = if (s.paused || s.interrupted) 0.9f else dotAlpha,
                        ),
                        CircleShape,
                    ),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        s.interrupted -> "录音被打断"
                        s.paused -> "录音已暂停"
                        else -> "正在录音"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    Formatters.duration(s.elapsedMs) + " · 点击返回录音页",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}
