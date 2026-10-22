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
import androidx.compose.ui.text.font.FontWeight
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
                "导出为 zip，包含数据库、录音、图片、课程表与 AI 配置。导入为合并模式：内容相同的跳过，不同的新增并追加「_导入_N」。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
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
                style = MaterialTheme.typography.labelMedium,
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
        }

        SettingsSection("录音与图片") {
            OutlinedTextField(
                value = sizeLimit,
                onValueChange = { sizeLimit = it.filter { c -> c.isDigit() } },
                label = { Text("单个录音文件上限（MB）") },
                supportingText = { Text("填 0 表示不限制") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
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

    backupResult?.let { msg ->
        // 成功与失败分开表达：标题先说结果，用户不用读完整段才知道成没成
        val failed = msg.contains("失败") || msg.contains("错误") || msg.contains("异常")
        AppAlertDialog(
            title = if (failed) "操作失败" else "已完成",
            message = msg,
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
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
            fontWeight = FontWeight.SemiBold,
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.then(Modifier),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
