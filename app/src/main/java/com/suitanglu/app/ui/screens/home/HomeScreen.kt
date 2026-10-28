package com.suitanglu.app.ui.screens.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suitanglu.app.AppGraph
import com.suitanglu.app.data.Formatters
import com.suitanglu.app.data.db.Recording
import com.suitanglu.app.service.RecordingBus
import com.suitanglu.app.ui.components.AppAlertDialog
import com.suitanglu.app.ui.theme.recordColors
import com.suitanglu.app.ui.theme.rememberReducedMotion
import com.suitanglu.app.ui.theme.tabularNumbers
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val RowHeight = 76.dp
private val TimeColumnWidth = 54.dp
private val AxisWidth = 26.dp
private val NodeCenterY = 21.dp

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    hasNotificationPermission: Boolean,
    onRequestPermissions: () -> Unit,
    onStartRecording: () -> Unit,
    onOpenChat: () -> Unit,
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
    val courses by vm.courses.collectAsState()
    val courseFilter by vm.courseFilter.collectAsState()
    val tipShown by vm.firstDeleteTipShown.collectAsState()
    val query by vm.searchQuery.collectAsState()
    val noRecordingsAtAll by vm.noRecordingsAtAll.collectAsState()
    val reducedMotion = rememberReducedMotion()
    // 只在本次进程首次进入首页时出场一次；从回放返回不重放
    var entered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val enterAlpha by animateFloatAsState(
        targetValue = if (entered || reducedMotion) 1f else 0f,
        animationSpec = tween(260),
        label = "enterAlpha",
    )
    val enterShift by animateFloatAsState(
        targetValue = if (entered || reducedMotion) 0f else 14f,
        animationSpec = tween(260),
        label = "enterShift",
    )
    val listState = rememberLazyListState()
    var showDatePicker by remember { mutableStateOf(false) }
    // 只在空↔非空跳变时更新，避免录音计时器每 500ms 触发整页重组
    // （不能用 derivedStateOf 包 StateFlow.value：StateFlow 不是快照状态，不会触发重算）
    val isRecording by remember {
        RecordingBus.state.map { it != null }.distinctUntilChanged()
    }.collectAsState(initial = RecordingBus.state.value != null)
    val recoveredCount by container.recordingRepository.recoveredCount.collectAsState()

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

    // 每个分组对应的日期，以及分组头在 LazyColumn 中的位置（分组头 + 该组条目数累加）
    val sectionDates = remember(sections) {
        sections.map {
            Instant.ofEpochMilli(it.items.first().recording.startTime)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        }
    }
    val headerIndices = remember(sections) {
        var acc = 0
        sections.map { section ->
            acc.also { acc += 1 + section.items.size }
        }
    }

    fun jumpToDate(utcMillis: Long) {
        if (sections.isEmpty()) {
            scope.launch { snackbar.showSnackbar("还没有可跳转的录音") }
            return
        }
        // DatePicker 给的是 UTC 零点，按 UTC 取回日期才不会差一天
        val target: LocalDate = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val exact = sectionDates.indexOf(target)
        // 分组按时间倒序：第一个"不晚于目标"的分组就是最近的一天
        val nearest = sectionDates.indexOfFirst { it <= target }.let { if (it >= 0) it else sectionDates.lastIndex }
        val index = if (exact >= 0) exact else nearest
        scope.launch { listState.scrollToItem(headerIndices[index]) }
        if (exact < 0) {
            scope.launch {
                snackbar.showSnackbar(
                    "${target.monthValue}月${target.dayOfMonth}日没有录音，已跳到最近的 ${Formatters.dateLabel(sections[index].items.first().recording.startTime)}",
                )
            }
        }
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (isRecording) {
                        onStartRecording()
                    } else if (hasNotificationPermission || android.os.Build.VERSION.SDK_INT < 33) {
                        onStartRecording()
                    } else {
                        showNoPermissionHint = true
                        onRequestPermissions()
                    }
                },
                // 全页唯一的高饱和块：主操作必须最显眼
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(if (isRecording) "返回录音中" else "开始录音") },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (selectionMode) {
                SelectionHeader(
                    count = selectedIds.size,
                    onExit = { exitSelection() },
                    onSelectAll = { selectedIds = allRecordingIds },
                    onDeleteAudioOnly = {
                        vm.deleteAudioOnlyMany(selectedIds)
                        val n = selectedIds.size
                        exitSelection()
                        scope.launch { snackbar.showSnackbar("已删除 $n 条录音的音频，标记与讲解保留") }
                    },
                    onDelete = {
                        vm.deleteToRecycleMany(selectedIds)
                        val n = selectedIds.size
                        exitSelection()
                        scope.launch { snackbar.showSnackbar("已将 $n 条录音移入回收站") }
                    },
                )
            } else {
                HomeHeader(
                    totalCount = sections.sumOf { it.items.size },
                    totalMs = sections.sumOf { section -> section.items.sumOf { it.recording.durationMs } },
                    courseName = courseFilter,
                    onOpenRecycle = onOpenRecycle,
                    onOpenChat = onOpenChat,
                    onOpenSettings = onOpenSettings,
                )
            }

            // 空状态隐藏搜索与筛选：没有内容时它们是空壳控件
            if (!selectionMode && !noRecordingsAtAll) {
                SearchRow(
                    query = query,
                    onQueryChange = { vm.setQuery(it) },
                    onPickDate = { showDatePicker = true },
                )
            }

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
                        .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CourseFilterChip(
                        label = "全部",
                        selected = courseFilter == null,
                        dotColor = null,
                        onClick = { vm.setCourseFilter(null) },
                    )
                    courses.forEach { course ->
                        val active = courseFilter == course
                        CourseFilterChip(
                            label = course,
                            selected = active,
                            dotColor = courseColor(course),
                            // 再点一次已选中的课程 = 取消筛选
                            onClick = { vm.setCourseFilter(if (active) null else course) },
                        )
                    }
                }
            }

            if (sections.isEmpty() && query.isNotBlank()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("没有匹配「$query」的录音", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "可以搜课程名、录音标题，或标记里的文字与讲解内容。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { vm.setQuery("") }) { Text("清除搜索") }
                }
            } else if (sections.isEmpty() && courseFilter != null) {
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
                EmptyState()
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = enterAlpha
                            translationY = enterShift.dp.toPx()
                        },
                    state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
                ) {
                    for (section in sections) {
                        stickyHeader(key = "header_${section.label}") {
                            GroupHeader(
                                label = section.label,
                                weekday = Formatters.weekdayShort(section.items.first().recording.startTime),
                                count = section.items.size,
                            )
                        }
                        itemsIndexed(section.items, key = { _, it -> it.recording.id }) { index, item ->
                            TimelineRow(
                                modifier = Modifier.animateItem(),
                                item = item,
                                isFirst = index == 0,
                                isLast = index == section.items.lastIndex,
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

    // 第一次删除提醒：只是移到回收站，可恢复，所以确认键仍是主色
    pendingDelete?.let { rec ->
        AppAlertDialog(
            title = "删除录音",
            message = "「${rec.title}」将移入回收站，可随时恢复或彻底删除。",
            confirmLabel = "移入回收站",
            onConfirm = {
                vm.deleteToRecycle(rec.id)
                vm.markDeleteTipShown()
                pendingDelete = null
            },
            onDismissRequest = { pendingDelete = null },
        )
    }

    // 仅删除音频：不可恢复，确认键转 error 色并加警示图标
    pendingAudioOnly?.let { rec ->
        AppAlertDialog(
            title = "仅删除录音文件",
            message = "只删除「${rec.title}」的音频，标记、图片和 AI 讲解都会保留。此操作不可恢复。",
            confirmLabel = "删除音频",
            destructive = true,
            showIcon = true,
            onConfirm = {
                vm.deleteAudioOnly(rec.id)
                pendingAudioOnly = null
            },
            onDismissRequest = { pendingAudioOnly = null },
        )
    }

    // 重命名
    renameTarget?.let { rec ->
        AppAlertDialog(
            title = "重命名录音",
            confirmLabel = "保存",
            // 空名字会让列表里出现一条没有标题的记录，直接禁掉确认键
            confirmEnabled = renameText.isNotBlank(),
            onConfirm = {
                vm.rename(rec.id, renameText) { ok ->
                    scope.launch {
                        snackbar.showSnackbar(if (ok) "已重命名" else "重命名失败")
                    }
                }
                renameTarget = null
            },
            onDismissRequest = { renameTarget = null },
        ) {
            OutlinedTextField(
                value = renameText,
                onValueChange = { renameText = it },
                singleLine = true,
                label = { Text("录音名称") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    // 按日期跳转：直接滚到那天的分组；那天没录音就退到最近的一天并说明
    if (showDatePicker) {
        val dateState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    enabled = dateState.selectedDateMillis != null,
                    onClick = {
                        dateState.selectedDateMillis?.let { jumpToDate(it) }
                        showDatePicker = false
                    },
                ) { Text("跳转") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = dateState)
        }
    }
}

/** 搜索 + 日期跳转合成一行：都是"找某段录音"的入口，没必要各占一行 */
@Composable
private fun SearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onPickDate: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .height(42.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(12.dp))
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    "搜索课程、标题或标记文字",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "清除搜索",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        IconButton(onClick = onPickDate, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Filled.CalendarMonth,
                contentDescription = "按日期跳转",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

/**
 * 大标题 + 一行统计 + 仅有的两个入口。
 * 副标题不写产品口号，写"一共存了多少课"——这是用户唯一会在意的横向信息。
 */
@Composable
private fun HomeHeader(
    totalCount: Int,
    totalMs: Long,
    courseName: String?,
    onOpenRecycle: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "随堂录",
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 23.sp),
            )
            Text(
                summaryLine(totalCount, totalMs, courseName),
                style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        // 回收站不是"删除"：用还原图标，避免看成一个清空按钮
        IconButton(onClick = onOpenRecycle) {
            Icon(
                Icons.Outlined.RestoreFromTrash,
                contentDescription = "回收站",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpenChat) {
            Icon(
                Icons.Outlined.Forum,
                contentDescription = "对话",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = "设置",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "高等数学 · 12 条 · 共 9 小时 24 分"；没有录音时不显示这条 */
private fun summaryLine(totalCount: Int, totalMs: Long, courseName: String?): String {
    if (totalCount == 0) return "课堂录音 · 拍照标记 · AI 讲解"
    val hours = totalMs / 3_600_000
    val minutes = (totalMs % 3_600_000) / 60_000
    val duration = when {
        hours > 0 && minutes > 0 -> "$hours 小时 $minutes 分"
        hours > 0 -> "$hours 小时"
        else -> "$minutes 分钟"
    }
    return buildString {
        if (!courseName.isNullOrBlank()) {
            append(courseName)
            append(" · ")
        }
        append("$totalCount 条录音 · 共 $duration")
    }
}

@Composable
private fun SelectionHeader(
    count: Int,
    onExit: () -> Unit,
    onSelectAll: () -> Unit,
    onDeleteAudioOnly: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onExit) {
            Icon(Icons.Filled.Close, contentDescription = "退出选择")
        }
        Text(
            "已选 $count 项",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onSelectAll) { Text("全选") }
        // 两个删除动作都配图标：原来纯文字，多选时看不出哪个是"只删音频"
        TextButton(onClick = onDeleteAudioOnly, enabled = count > 0) {
            Icon(
                Icons.Filled.MusicOff,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text("仅删音频")
        }
        TextButton(onClick = onDelete, enabled = count > 0) {
            Icon(
                Icons.Filled.DeleteOutline,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text("回收站")
        }
    }
}

/** 日期分组头：日期 + 一条贯穿到右边的细线，替代卡片分组的视觉重量 */
@Composable
private fun GroupHeader(label: String, weekday: String, count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.alignByBaseline(),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            weekday,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alignByBaseline(),
        )
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "$count 条",
            style = MaterialTheme.typography.labelSmall.tabularNumbers(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

/**
 * 时间轴行：左列是时间（找录音的第一线索），中间是轴与课程色节点，右侧是标题与讲解进度。
 * 不用卡片：卡片会把每条录音切成互相独立的方块，这里要的是一条连续的"课堂记录"。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineRow(
    modifier: Modifier = Modifier,
    item: HomeItem,
    isFirst: Boolean,
    isLast: Boolean,
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
    val nodeColor = courseColor(rec.courseName) ?: MaterialTheme.colorScheme.outline
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val gapColor = MaterialTheme.colorScheme.background
    val shape = RoundedCornerShape(10.dp)

    Row(
        modifier
            .fillMaxWidth()
            .height(RowHeight)
            .padding(horizontal = 8.dp)
            .background(
                when {
                    selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    else -> Color.Transparent
                },
                shape,
            )
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelect() else onClick() },
                onLongClick = { if (!selectionMode) onLongPress() },
            )
            .padding(start = 12.dp),
    ) {
        // 时间列
        Column(Modifier.width(TimeColumnWidth).padding(top = 12.dp)) {
            Text(
                Formatters.timeOnly(rec.startTime),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp).tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                if (audioDeleted) "音频已删" else Formatters.durationShort(rec.durationMs),
                style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }

        // 轴：一段竖线 + 一个课程色节点；首行上端、末行下端留白，线不会戳出分组
        Canvas(Modifier.width(AxisWidth).fillMaxHeight()) {
            val x = 4.dp.toPx()
            val centerY = NodeCenterY.toPx()
            val top = if (isFirst) centerY else 0f
            val bottom = if (isLast) centerY else size.height
            drawLine(
                color = lineColor,
                start = Offset(x, top),
                end = Offset(x, bottom),
                strokeWidth = 1.dp.toPx(),
            )
            // 背景色描边把线与节点隔开
            drawCircle(gapColor, radius = 6.dp.toPx(), center = Offset(x, centerY))
            drawCircle(nodeColor, radius = 4.dp.toPx(), center = Offset(x, centerY))
        }

        // 内容
        Column(
            Modifier
                .weight(1f)
                .padding(start = 4.dp, top = 12.dp, end = 2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) {
                    Icon(
                        if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = if (selected) "已选中" else "未选中",
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    rec.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isRecording) {
                    RecordingPulseDot()
                    Spacer(Modifier.width(5.dp))
                }
                val pending = pendingOf(item)
                val pendingColor = MaterialTheme.recordColors.pending
                Text(
                    subtitleOf(item, isRecording, audioDeleted),
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        audioDeleted -> MaterialTheme.colorScheme.onSurfaceVariant
                        item.markerFailed > 0 -> MaterialTheme.colorScheme.error
                        pending > 0 -> pendingColor
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.markerFailed > 0 && !audioDeleted) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(12.dp),
                    )
                } else if (pending > 0 && !audioDeleted) {
                    // 取消自动发送后，"还欠着"必须自己有话说，否则这节录音就被忘在列表里
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = "有标记待发送",
                        tint = pendingColor,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }

        // 更多操作
        if (!selectionMode) {
            Box(Modifier.width(40.dp).fillMaxHeight()) {
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.align(Alignment.Center).size(40.dp),
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "更多操作",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
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
                    DropdownMenuItem(
                        text = { Text("仅删除录音文件") },
                        leadingIcon = {
                            Icon(Icons.Filled.MusicOff, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        onClick = onDeleteAudioOnly,
                    )
                    DropdownMenuItem(
                        text = { Text("导出单条录音") },
                        leadingIcon = { Icon(Icons.Filled.IosShare, null) },
                        onClick = onExport,
                    )
                }
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
    }
}

/** 卡片上没发出去的标记数：录音结束后不再自动发送，这是用户唯一能看见"还欠着"的地方 */
private fun pendingOf(item: HomeItem): Int =
    (item.markerTotal - item.markerSuccess - item.markerFailed).coerceAtLeast(0)

/** 副标题只回答"这节课的讲解进度"，不再堆时间/时长 */
private fun subtitleOf(item: HomeItem, isRecording: Boolean, audioDeleted: Boolean): String {
    if (isRecording) return "录制中"
    if (audioDeleted) return "音频已删除 · 标记与讲解仍在"
    if (item.markerTotal == 0) return "无标记"
    val pending = pendingOf(item)
    return when {
        pending > 0 && item.markerFailed > 0 ->
            "${item.markerTotal} 个标记 · $pending 待发送，${item.markerFailed} 个失败"
        pending > 0 -> "${item.markerTotal} 个标记 · $pending 个待发送"
        item.markerFailed > 0 -> "${item.markerTotal} 个标记 · ${item.markerFailed} 个讲解失败"
        else -> "${item.markerTotal} 个标记 · 全部已讲解"
    }
}

@Composable
private fun CourseFilterChip(
    label: String,
    selected: Boolean,
    dotColor: Color?,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = dotColor?.let {
            {
                Box(Modifier.size(7.dp).background(it, CircleShape))
            }
        },
        shape = RoundedCornerShape(10.dp),
    )
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 与应用图标同一母题（五段声波），空页面也保持品牌一致
        val barColor = MaterialTheme.colorScheme.primary
        Canvas(Modifier.size(width = 132.dp, height = 56.dp)) {
            val heights = listOf(0.34f, 0.62f, 1f, 0.52f, 0.30f)
            val alphas = listOf(0.22f, 0.45f, 0.95f, 0.45f, 0.22f)
            val barWidth = 8.dp.toPx()
            val gap = 10.dp.toPx()
            val totalWidth = heights.size * barWidth + (heights.size - 1) * gap
            var x = (size.width - totalWidth) / 2f
            heights.forEachIndexed { index, ratio ->
                val h = size.height * ratio
                drawRoundRect(
                    color = barColor.copy(alpha = alphas[index]),
                    topLeft = Offset(x, (size.height - h) / 2f),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                )
                x += barWidth + gap
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("还没有录音", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "上课时点右下角「开始录音」，遇到重点拍照标记。\n课后在回放页修好图，再一起发给 AI 讲解。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
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
            .size(7.dp)
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
        shape = RoundedCornerShape(16.dp),
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
                    style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}
