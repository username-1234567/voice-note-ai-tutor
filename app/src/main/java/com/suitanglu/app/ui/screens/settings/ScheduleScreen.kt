package com.suitanglu.app.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.suitanglu.app.AppGraph
import com.suitanglu.app.data.CourseSchedule
import com.suitanglu.app.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.suitanglu.app.ui.components.SecondaryTopBar

/** 一次时段编辑：day 天 index 节，start/end 为分钟数 */
private data class SlotEdit(val day: Int, val index: Int, val start: Int, val end: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = AppGraph.get(context)
    val scope = rememberCoroutineScope()
    var selectedDay by remember { mutableIntStateOf(1) }
    var names by remember { mutableStateOf(mapOf<Pair<Int, Int>, String>()) }
    // 文本按天缓存：切天不丢未保存的时间段编辑
    var startTexts by remember { mutableStateOf(mapOf<Int, Map<Int, String>>()) }
    var endTexts by remember { mutableStateOf(mapOf<Int, Map<Int, String>>()) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

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
            val dayLabel = CourseSchedule.dayNames()[day - 1]
            for (slotIndex in 1..5) {
                val start = parseHHmm(dayStarts[slotIndex].orEmpty())
                val end = parseHHmm(dayEnds[slotIndex].orEmpty())
                if (start == null || end == null) {
                    error = "$dayLabel 第${slotIndex}节时间格式应为 HH:mm，例如 08:00"
                    return
                }
                if (start >= end) {
                    error = "$dayLabel 第${slotIndex}节的开始时间必须早于结束时间"
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

    BackHandler { save { onBack() } }

    Scaffold(
        // 这一页全是输入框（5 个时间段 × 起止），键盘弹起时必须整体上移
        modifier = Modifier.imePadding(),
        topBar = {
            // 返回即保存（原有的静默保存行为），文案在下方说明里写明
            SecondaryTopBar(title = "课程表", onBack = { save { onBack() } })
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "按星期几配置课程名称与时间段；时间段起止可修改（每天独立）。课程名可为空，为空或录音未命中时间段时显示“未命名”。",
                style = MaterialTheme.typography.bodyMedium,
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
                                OutlinedTextField(
                                    value = startTexts[selectedDay]?.get(slotIndex).orEmpty(),
                                    onValueChange = { v ->
                                        startTexts = startTexts +
                                            (selectedDay to (startTexts[selectedDay].orEmpty() + (slotIndex to v)))
                                    },
                                    label = { Text("开始") },
                                    modifier = Modifier.width(110.dp),
                                    singleLine = true,
                                    placeholder = { Text("HH:mm") },
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("–", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(8.dp))
                                OutlinedTextField(
                                    value = endTexts[selectedDay]?.get(slotIndex).orEmpty(),
                                    onValueChange = { v ->
                                        endTexts = endTexts +
                                            (selectedDay to (endTexts[selectedDay].orEmpty() + (slotIndex to v)))
                                    },
                                    label = { Text("结束") },
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
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { save { onBack() } },
                    modifier = Modifier.weight(1f).height(52.dp),
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
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("恢复默认时段") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
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
