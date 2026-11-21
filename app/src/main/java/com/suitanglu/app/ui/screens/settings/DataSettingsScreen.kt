package com.suitanglu.app.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suitanglu.app.data.Formatters
import com.suitanglu.app.ui.components.AppAlertDialog
import com.suitanglu.app.ui.components.BottomActionRow
import com.suitanglu.app.ui.components.SecondaryPage
import com.suitanglu.app.ui.theme.tabularNumbers

/** 二级页：数据与存储（备份、导入、空间、图片压缩） */
@Composable
fun DataSettingsScreen(onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel()
    val general by vm.generalConfig.collectAsState()
    val backupProgress by vm.backupProgress.collectAsState()
    val backupResult by vm.backupResult.collectAsState()
    val storage by vm.storageUsage.collectAsState()

    var sizeLimit by remember(general.sizeLimitMb) { mutableStateOf(general.sizeLimitMb.toString()) }
    var compress by remember(general.compressImages) { mutableStateOf(general.compressImages) }
    var compressQuality by remember(general.compressQuality) { mutableStateOf(general.compressQuality.toFloat()) }
    // 失焦回填用：只在真实经历过一次聚焦后才回填，避免页面刚打开就改写显示值
    var sizeLimitFocused by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let { vm.exportTo(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { vm.importFrom(it) } }

    SecondaryPage(
        title = "数据与存储",
        onBack = onBack,
        // 存储设置也是表单：改了没存就返回，配置会悄悄退回原样
        unsavedChanges = sizeLimit != general.sizeLimitMb.toString() ||
            compress != general.compressImages ||
            compressQuality != general.compressQuality.toFloat(),
        bottomBar = {
            BottomActionRow {
                Button(
                    onClick = {
                        vm.saveGeneral(
                            general.copy(
                                sizeLimitMb = sizeLimit.toIntOrNull() ?: 0,
                                compressImages = compress,
                                compressQuality = compressQuality.toInt(),
                            ),
                        )
                        vm.refreshStorage()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("保存存储设置") }
            }
        },
    ) {
        SettingsSection("备份与恢复") {
            Text(
                "导出为 zip，包含数据库、录音、图片、课程表与 AI 配置；网页版对话与学术研讨的会话不在备份范围内。" +
                    "导入为合并模式：内容相同的跳过，不同的新增并追加「_导入_N」。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // 降级为描边按钮：一屏只留底部「保存存储设置」一个实心主色块
                OutlinedButton(
                    onClick = {
                        val ts = java.time.LocalDateTime.now()
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                        exportLauncher.launch("backup_$ts.zip")
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("导出备份") }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                    modifier = Modifier.weight(1f),
                ) { Text("导入备份") }
            }
            backupProgress?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                "备份文件含明文 API Key，请妥善保管。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsSection("空间占用") {
            storage?.let { (rec, img) ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    StorageStat("录音", Formatters.bytes(rec))
                    StorageStat("图片", Formatters.bytes(img))
                    StorageStat("合计", Formatters.bytes(rec + img), emphasized = true)
                }
            }
            TextButton(
                onClick = { vm.clearTempFiles() },
                modifier = Modifier.padding(start = 0.dp),
            ) { Text("清理临时文件") }
            Text(
                "清理范围：裁剪输出、拍摄原图与待发送暂存；录音进行中会保留待发送暂存。" +
                    "清完弹窗会显示实际释放的空间。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsSection("录音与图片") {
            OutlinedTextField(
                value = sizeLimit,
                onValueChange = { sizeLimit = it.filter { c -> c.isDigit() } },
                label = { Text("单个录音文件上限（MB）") },
                supportingText = {
                    Text(
                        "填 0 或留空表示不限制",
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { state ->
                        if (state.isFocused) {
                            sizeLimitFocused = true
                        } else if (sizeLimitFocused && sizeLimit.isBlank()) {
                            // 留空回填 0：框里显示的就是实际生效值（0 = 不限制）
                            sizeLimitFocused = false
                            sizeLimit = "0"
                        }
                    },
                singleLine = true,
            )
            SwitchRow(
                title = "上传前压缩标记图片",
                subtitle = if (compress) {
                    "以 JPEG 质量 ${compressQuality.toInt()}% 压缩，省流量也省内存"
                } else {
                    "当前发送原图，画质最好但更占内存"
                },
                checked = compress,
                onCheckedChange = { compress = it },
            )
            if (compress) {
                Column {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("压缩质量", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "${compressQuality.toInt()}%",
                            style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = compressQuality,
                        onValueChange = { compressQuality = it },
                        valueRange = 30f..100f,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    backupResult?.let { result ->
        // 成功与失败用类型区分：标题先说结果，用户不用读完整段才知道成没成
        val failed = result is BackupUiResult.Failure
        val message = when (result) {
            is BackupUiResult.Failure -> result.message
            is BackupUiResult.Success -> result.message
        }
        AppAlertDialog(
            title = if (failed) "操作失败" else "已完成",
            message = message,
            confirmLabel = "知道了",
            destructive = failed,
            showDismiss = false,
            onConfirm = { vm.consumeBackupResult() },
            onDismissRequest = { vm.consumeBackupResult() },
        )
    }
}

/** 空间统计：数字在上、标签在下，数字等宽，合计用主色压轴 */
@Composable
private fun StorageStat(label: String, value: String, emphasized: Boolean = false) {
    Column {
        Text(
            value,
            // 存储统计是数字展示：等宽避免刷新数值时左右跳（DESIGN.md §3.3）
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp).tabularNumbers(),
            fontWeight = FontWeight.SemiBold,
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
