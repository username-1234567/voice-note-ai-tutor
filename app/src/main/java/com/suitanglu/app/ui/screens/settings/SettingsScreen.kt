package com.suitanglu.app.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suitanglu.app.data.CameraMode
import com.suitanglu.app.update.UpdateConfig

/**
 * 设置首页 = 索引页：三项常用设置跳到二级页，其余就地配置。
 * 以前全挤在一页（API 表单 + 备份 + 存储 + 权限 + 更新），滚两屏找不到东西。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenData: () -> Unit,
) {
    val vm: SettingsViewModel = viewModel()
    val general by vm.generalConfig.collectAsState()
    val ai by vm.aiConfig.collectAsState()
    val updateState by vm.updateState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
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
            SettingsSection("常用", compact = true) {
                SettingsEntry("课程表", "按星期几与时间段自动命名录音", onOpenSchedule)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsEntry(
                    "AI 配置",
                    if (ai.apiKey.isBlank()) "未填写 API Key" else "${ai.model} · 已配置",
                    onOpenAi,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsEntry("数据与存储", "导出备份、导入、空间占用与图片压缩", onOpenData)
            }

            SettingsSection("录音设置") {
                Text("相机", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = general.cameraMode == CameraMode.SYSTEM,
                        onClick = { vm.saveGeneral(general.copy(cameraMode = CameraMode.SYSTEM)) },
                        label = { Text("系统相机") },
                    )
                    FilterChip(
                        selected = general.cameraMode == CameraMode.IN_APP,
                        onClick = { vm.saveGeneral(general.copy(cameraMode = CameraMode.IN_APP)) },
                        label = { Text("应用内相机") },
                    )
                }
                Text(
                    "系统相机兼容性好；若录音中调用系统相机导致录音中断，请切换为应用内相机。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text("权限与保活", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "为防止录音被系统杀死，建议忽略电池优化并允许后台运行。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.requestIgnoreBatteryOptimization() }) { Text("忽略电池优化") }
                    OutlinedButton(onClick = { vm.openAppWhitelistSettings() }) { Text("应用详情设置") }
                }
                if (Build.VERSION.SDK_INT >= 33) {
                    Text(
                        "通知权限用于显示录音常驻通知，若未允许请在系统设置中开启。",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SettingsSection("应用") {
                Text(
                    "随堂录 v${UpdateConfig.CURRENT_VERSION}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                when (val s = updateState) {
                    is UpdateUiState.Idle -> Button(onClick = { vm.checkUpdate() }) { Text("检查更新") }
                    UpdateUiState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.width(16.dp).height(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("正在检查更新…")
                    }
                    is UpdateUiState.UpToDate -> {
                        Text("已是最新版本 v${s.version}", color = MaterialTheme.colorScheme.primary)
                        TextButton(onClick = { vm.checkUpdate() }) { Text("再次检查") }
                    }
                    is UpdateUiState.Available -> {
                        Text(
                            "发现新版本 ${s.info.version}（${s.info.releaseName}）",
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (s.info.changelog.isNotBlank()) {
                            Text(s.info.changelog, style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.downloadUpdate(s.info) }) { Text("下载并安装") }
                            TextButton(onClick = { vm.resetUpdateState() }) { Text("忽略") }
                        }
                    }
                    is UpdateUiState.Downloading -> {
                        Text("下载中 ${s.percent}%")
                        LinearProgressIndicator(
                            progress = { s.percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    is UpdateUiState.ReadyToInstall -> {
                        Text("下载完成，点击安装", color = MaterialTheme.colorScheme.primary)
                        Button(onClick = { vm.installUpdate(s.file) }) { Text("安装 APK") }
                    }
                    is UpdateUiState.Error -> {
                        Text(s.message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { vm.checkUpdate() }) { Text("重试") }
                    }
                }
                Text(
                    "所有数据（录音、图片、AI 回复）保存在应用私有目录，卸载应用会全部删除，请定期在「数据与存储」中导出备份。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
