package com.suitanglu.app.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.suitanglu.app.AppGraph
import com.suitanglu.app.data.CourseSchedule
import com.suitanglu.app.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.suitanglu.app.ui.components.BottomActionRow
import com.suitanglu.app.ui.components.SecondaryPage

/** 一次时段编辑：day 天 index 节，start/end 为分钟数 */
private data class SlotEdit(val day: Int, val index: Int, val start: Int, val end: Int)

/** 校验失败的就地标记：哪天、哪节、起还是止、一句话错误说明 */
private data class SlotError(val day: Int, val slot: Int, val isEnd: Boolean, val message: String)

@Composable
fun ScheduleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = AppGraph.get(context)
    val scope = rememberCoroutineScope()
    // 课前提醒依赖课程表排期，放在同一页；开关与提前分钟数即改即存（复用 VM 里的重排闹钟逻辑）
    val settingsVm: SettingsViewModel = viewModel()
    val reminder by settingsVm.reminderConfig.collectAsState()
    var selectedDay by remember { mutableIntStateOf(1) }
    var names by remember { mutableStateOf(mapOf<Pair<Int, Int>, String>()) }
    // 文本按天缓存：切天不丢未保存的时间段编辑
    var startTexts by remember { mutableStateOf(mapOf<Int, Map<Int, String>>()) }
    var endTexts by remember { mutableStateOf(mapOf<Int, Map<Int, String>>()) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<SlotError?>(null) }

    suspend fun loadDayTexts(day: Int) {
        val s = container.settings.slotsNow(day)
        startTexts = startTexts + (day to s.associate { it.index to minutesToText(it.startMinute) })
        endTexts = endTexts + (day to s.associate { it.index to minutesToText(it.endMinute) })
    }

    LaunchedEffect(Unit) {
        names = container.settings.allCourseNames().first()
        loadDayTexts(selectedDay)
        loaded = true
    }
    LaunchedEffect(selectedDay) {
        if (startTexts[selectedDay] == null) loadDayTexts(selectedDay)
    }

    // 校验并写入所有已加载天的时段 + 全部课程名；某天未改动时写回的值与库里一致，无副作用
    fun save(onSaved: () -> Unit) {
        val edits = mutableListOf<SlotEdit>()
        for ((day, dayStarts) in startTexts) {
            val dayEnds = endTexts[day].orEmpty()
            for (slotIndex in 1..5) {
                val start = parseHHmm(dayStarts[slotIndex].orEmpty())
                val end = parseHHmm(dayEnds[slotIndex].orEmpty())
                if (start == null || end == null) {
                    // 就地可见：标出具体字段，并自动切到出错的那天，让用户立刻看到出错格子
                    val message = when {
                        start == null && end == null -> "时间格式应为 HH:mm，例如 08:00"
                        start == null -> "开始时间格式应为 HH:mm，例如 08:00"
                        else -> "结束时间格式应为 HH:mm，例如 08:00"
                    }
                    error = SlotError(day, slotIndex, isEnd = start != null, message = message)
                    selectedDay = day
                    return
                }
                if (start >= end) {
                    error = SlotError(day, slotIndex, isEnd = true, message = "开始时间必须早于结束时间")
                    selectedDay = day
                    return
                }
                edits.add(SlotEdit(day, slotIndex, start, end))
            }
        }
        scope.launch {
            for (e in edits) {
                container.settings.setSlotTime(e.day, e.index, e.start, e.end)
            }
            container.settings.setAllCourseNames(names)
            onSaved()
        }
    }

    // 系统返回也走「返回即保存」。SecondaryPage 的 unsavedChanges 不启用（保存模型不变），
    // 它的放弃确认框与返回拦截均不生效，系统返回由这里的 BackHandler 接管
    BackHandler { save { onBack() } }

    // 统一二级页容器：imePadding 保留（键盘弹起整体上移）；
    // 「返回即保存」用 onBack 直通，不启用 unsavedChanges 确认框
    SecondaryPage(
        title = "课程表与提醒",
        onBack = { save { onBack() } },
        bottomBar = {
            BottomActionRow {
                Button(
                    onClick = { save { onBack() } },
                    modifier = Modifier.weight(1f),
                ) { Text("保存课程表") }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            for (s in SettingsRepository.DEFAULT_SLOTS) {
                                container.settings.setSlotTime(selectedDay, s.index, s.startMinute, s.endMinute)
                            }
                            loadDayTexts(selectedDay)
                            error = null
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("恢复默认时段") }
            }
        },
    ) {
        SettingsSection("课前提醒") {
            SwitchRow(
                title = "课前提醒",
                subtitle = if (reminder.first) "按课程表排期，提前 ${reminder.second} 分钟" else "关闭",
                checked = reminder.first,
                onCheckedChange = { settingsVm.saveReminder(it, reminder.second) },
            )
            if (reminder.first) {
                // 自己输入而不是选固定档：有人要 3 分钟（走到教室），有人要 30 分钟（先预习）
                var leadText by rememberSaveable(reminder.second) {
                    mutableStateOf(reminder.second.toString())
                }
                LaunchedEffect(leadText) {
                    val v = leadText.toIntOrNull() ?: return@LaunchedEffect
                    if (v in 1..60) {
                        if (v != reminder.second) {
                            // 停手 500ms 再落库：连续敲两位数不会触发两次重排闹钟
                            delay(500)
                            settingsVm.saveReminder(true, v)
                        }
                    } else {
                        // 越界（如敲出 99）抖回当前生效值：输入框显示的必须就是实际生效的
                        leadText = reminder.second.toString()
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("提前", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(10.dp))
                    // 单位放输入框外面，不用 suffix：suffix 紧跟输入文字，
                    // 框宽固定时会「5」后面空一大截、单位不贴右边缘
                    OutlinedTextField(
                        value = leadText,
                        onValueChange = { raw ->
                            leadText = raw.filter { it.isDigit() }.take(2)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        // 「提前/分钟」只是两侧的普通文字，与输入框语义不关联；读屏聚焦时读不出用途
                        modifier = Modifier
                            .width(88.dp)
                            .semantics { contentDescription = "课前提前提醒的分钟数" },
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("分钟", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "1–60 分钟。只对配置了课程名的时段生效，周末不提醒；系统省电策略可能让通知稍晚几分钟。" +
                        "小米/Redmi 还要在系统通知管理里打开「悬浮通知」，否则提醒只静默进通知栏。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            "按星期几配置课程名称与时间段；时间段起止可修改（每天独立）。课程名可为空，为空或录音未命中时间段时显示“未命名”。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CourseSchedule.dayNames().forEachIndexed { index, label ->
                val day = index + 1
                FilterChip(
                    selected = selectedDay == day,
                    onClick = { selectedDay = day },
                    label = { Text(label) },
                )
            }
        }
        if (loaded) {
            for (slotIndex in 1..5) {
                val key = selectedDay to slotIndex
                // 就地错误：只标出错的那一格，切到出错天时用户一眼定位
                val startError = error?.takeIf { it.day == selectedDay && it.slot == slotIndex && !it.isEnd }
                val endError = error?.takeIf { it.day == selectedDay && it.slot == slotIndex && it.isEnd }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "第 $slotIndex 节",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            val startValue = startTexts[selectedDay]?.get(slotIndex).orEmpty()
                            OutlinedTextField(
                                value = startValue,
                                onValueChange = { v ->
                                    val next = sanitizeHHmm(v, autoColon = v.length > startValue.length)
                                    startTexts = startTexts +
                                        (selectedDay to (startTexts[selectedDay].orEmpty() + (slotIndex to next)))
                                    // 改动即撤就地错误，下次保存再验
                                    if (startError != null) error = null
                                },
                                label = { Text("开始") },
                                isError = startError != null,
                                supportingText = {
                                    startError?.let { e ->
                                        Text(e.message, style = MaterialTheme.typography.labelSmall)
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(110.dp),
                                singleLine = true,
                                placeholder = { Text("HH:mm") },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("–", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            val endValue = endTexts[selectedDay]?.get(slotIndex).orEmpty()
                            OutlinedTextField(
                                value = endValue,
                                onValueChange = { v ->
                                    val next = sanitizeHHmm(v, autoColon = v.length > endValue.length)
                                    endTexts = endTexts +
                                        (selectedDay to (endTexts[selectedDay].orEmpty() + (slotIndex to next)))
                                    if (endError != null) error = null
                                },
                                label = { Text("结束") },
                                isError = endError != null,
                                supportingText = {
                                    endError?.let { e ->
                                        Text(e.message, style = MaterialTheme.typography.labelSmall)
                                    }
                                },
                                // 时间是纯数字 + 自动补冒号（sanitizeHHmm），配数字键盘而不是文本键盘
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(110.dp),
                                singleLine = true,
                                placeholder = { Text("HH:mm") },
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = names[key].orEmpty(),
                            onValueChange = { v -> names = names + (key to v) },
                            label = { Text("课程名称（可为空）") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                }
            }
        }
        // 兜底：就地提示之上再给一行带天/节上下文的完整错误说明
        error?.let { e ->
            Text(
                "${CourseSchedule.dayNames()[e.day - 1]} 第${e.slot}节：${e.message}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** 只允许数字与冒号；正在输入（变长）时敲满两位小时自动补「:」，减少格式错误 */
private fun sanitizeHHmm(raw: String, autoColon: Boolean): String {
    val filtered = raw.filter { it.isDigit() || it == ':' }
    if (!autoColon) return filtered.take(5)
    if (filtered.length == 2 && filtered.none { it == ':' }) return "$filtered:"
    if (filtered.length > 2 && filtered[2] != ':') {
        return (filtered.substring(0, 2) + ":" + filtered.substring(2)).take(5)
    }
    return filtered.take(5)
}

private fun parseHHmm(text: String): Int? {
    val parts = text.trim().split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

private fun minutesToText(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
