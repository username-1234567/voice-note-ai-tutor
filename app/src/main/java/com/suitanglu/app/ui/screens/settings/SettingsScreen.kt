package com.suitanglu.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suitanglu.app.data.CameraMode
import com.suitanglu.app.data.Formatters
import com.suitanglu.app.update.UpdateConfig

/**
 * 设置首页 = 索引页：只放跳转入口，不就地配置。
 * 以前目录下面还挤着图标选择、提醒表单、相机、检查更新，一页滚两屏、语义混；
 * 现在全部下沉到二级页，这里只留四组入口，每行副标题带一条状态摘要。
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenData: () -> Unit,
    onOpenRecording: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val vm: SettingsViewModel = viewModel()
    val general by vm.generalConfig.collectAsState()
    val reminder by vm.reminderConfig.collectAsState()
    val ai by vm.aiConfig.collectAsState()
    val storage by vm.storageUsage.collectAsState()

    // 摘要跟着页面切换刷新：从「数据与存储」清完缓存回来，数字不至于停在旧值
    LaunchedEffect(Unit) { vm.refreshStorage() }

    // 这里曾经挂过 exitUntilCollapsedScrollBehavior + nestedScroll，但顶栏是自绘的 Row，
    // 没有把 behavior 交给 TopAppBar，导致 heightOffsetLimit 一直是初值 NaN，
    // nestedScrollConnection 会把每一次上滑距离全部吃掉 —— 表现就是"设置页划不动"。
    // 顶栏本来也是固定的 23sp 标题，不需要折叠行为，直接去掉。
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            // 索引页是"一级页面"，标题与首页同级（23sp），不再用会缩放的 LargeTopAppBar
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 4.dp, end = 16.dp, top = 2.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    "设置",
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 23.sp),
                )
            }
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
            SettingsSection("AI 讲解", compact = true) {
                SettingsEntry(
                    title = "AI 配置",
                    subtitle = if (ai.apiKey.isBlank()) "未填写 API Key" else "${ai.model} · 已配置",
                    icon = Icons.Outlined.AutoAwesome,
                    tint = MaterialTheme.colorScheme.tertiary,
                    onClick = onOpenAi,
                )
            }

            SettingsSection("课堂", compact = true) {
                SettingsEntry(
                    title = "课程表与提醒",
                    subtitle = if (reminder.first) {
                        "按课程表命名录音，课前 ${reminder.second} 分钟提醒"
                    } else {
                        "按课程表命名录音；课前提醒未开启"
                    },
                    icon = Icons.Outlined.CalendarMonth,
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = onOpenSchedule,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsEntry(
                    title = "录音与权限",
                    subtitle = if (general.cameraMode == CameraMode.SYSTEM) "当前使用系统相机" else "当前使用应用内相机",
                    icon = Icons.Outlined.Mic,
                    tint = MaterialTheme.colorScheme.secondary,
                    onClick = onOpenRecording,
                )
            }

            SettingsSection("数据", compact = true) {
                // 动态摘要：VM init 已拉过 storageUsage；还没读出来（null）时回落到静态文案
                val storageSubtitle = storage?.let { (rec, img) ->
                    "已用 ${Formatters.bytes(rec + img)}；导出备份、导入与图片压缩"
                } ?: "导出备份、导入、空间占用与图片压缩"
                SettingsEntry(
                    title = "数据与存储",
                    subtitle = storageSubtitle,
                    icon = Icons.Outlined.FolderOpen,
                    tint = MaterialTheme.colorScheme.secondary,
                    onClick = onOpenData,
                )
            }

            SettingsSection("应用", compact = true) {
                SettingsEntry(
                    title = "外观与关于",
                    subtitle = "随堂录 v${UpdateConfig.CURRENT_VERSION} · 图标、更新与数据说明",
                    icon = Icons.Outlined.Info,
                    tint = MaterialTheme.colorScheme.tertiary,
                    onClick = onOpenAbout,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
