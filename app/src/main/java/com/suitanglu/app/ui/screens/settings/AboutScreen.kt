package com.suitanglu.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suitanglu.app.ui.components.IconPreview
import com.suitanglu.app.ui.components.SecondaryPage
import com.suitanglu.app.ui.theme.tabularNumbers
import com.suitanglu.app.update.UpdateConfig

/** 二级页：外观与关于（启动图标、版本与更新、数据安全说明） */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel()
    val appIcon by vm.appIcon.collectAsState()
    val updateState by vm.updateState.collectAsState()

    SecondaryPage(title = "外观与关于", onBack = onBack) {
        SettingsSection("应用图标") {
            Text(
                "更换后桌面图标需几秒刷新",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                com.suitanglu.app.util.AppIconManager.ALL.forEach { alias ->
                    val selected = appIcon == alias
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        // 单选语义：无障碍读出"已选中/单选"，点击行为不变
                        modifier = Modifier.selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { vm.saveAppIcon(alias) },
                        ),
                    ) {
                        Box(modifier = Modifier.size(58.dp), contentAlignment = Alignment.BottomEnd) {
                            com.suitanglu.app.ui.components.IconPreview(
                                alias = alias,
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(16.dp),
                                    ),
                            )
                            if (selected) {
                                // 选中角标：垫一圈页面底色的圆，避免和预览图糊在一起；
                                // 贴右下角、只盖住一小块，不挡图标主体
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = "已选中",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            com.suitanglu.app.util.AppIconManager.labelOf(alias),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        SettingsSection("版本") {
            Text(
                "随堂录 v${UpdateConfig.CURRENT_VERSION}",
                style = MaterialTheme.typography.bodyLarge,
            )
            when (val s = updateState) {
                is UpdateUiState.Idle -> OutlinedButton(onClick = { vm.checkUpdate() }) { Text("检查更新") }
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
                    // 进度百分比持续刷新：等宽数字防止 1 位数↔2 位数时文字左右跳
                    Text(
                        "下载中 ${s.percent}%",
                        style = MaterialTheme.typography.bodyLarge.tabularNumbers(),
                    )
                    LinearProgressIndicator(
                        progress = { s.percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is UpdateUiState.ReadyToInstall -> {
                    // 缺权限时不再把状态打回 Error：包还在，授权回来直接重按安装
                    if (s.needPermission) {
                        Text(
                            "缺「安装未知应用」权限，已跳转授权页；允许后回来再点一次安装。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text("下载完成", color = MaterialTheme.colorScheme.primary)
                    Button(onClick = { vm.installUpdate(s.file) }) { Text("安装更新") }
                }
                is UpdateUiState.Error -> {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    // 重试按失败来源重发：下载失败重新下载、安装失败重新安装、检查失败重新检查
                    TextButton(
                        onClick = {
                            val retry = s.retry
                            if (retry != null) retry() else vm.checkUpdate()
                        },
                    ) { Text("重试") }
                }
            }
        }

        Text(
            "所有数据（录音、图片、AI 回复）保存在应用私有目录，卸载应用会全部删除，请定期在「数据与存储」中导出备份。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
}
