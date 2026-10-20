package com.suitanglu.app.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suitanglu.app.data.Formatters

/** 二级页：数据与存储（备份、导入、空间、图片压缩） */
@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("数据与存储") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
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
            SettingsSection("备份与恢复") {
                Text(
                    "导出为 zip：数据库、录音、图片、课程表与 AI 配置（含 API Key，不加密）。导入为合并模式，Hash 相同跳过，不同则新增并追加「_导入_N」。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val ts = java.time.LocalDateTime.now()
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                        exportLauncher.launch("backup_$ts.zip")
                    }) { Text("导出全部数据") }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                    ) { Text("导入备份") }
                }
                backupProgress?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "备份文件含明文 API Key，请妥善保管。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsSection("存储") {
                storage?.let { (rec, img) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column {
                            Text(
                                Formatters.bytes(rec),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "录音",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column {
                            Text(
                                Formatters.bytes(img),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "图片",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                OutlinedButton(onClick = { vm.clearTempFiles() }) { Text("清理临时文件") }
                OutlinedTextField(
                    value = sizeLimit,
                    onValueChange = { sizeLimit = it.filter { c -> c.isDigit() } },
                    label = { Text("录音文件大小上限（MB，0 = 不限制）") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("压缩标记图片后再上传", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "默认发送原图；开启后以 JPEG 质量 ${compressQuality.toInt()}% 压缩",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = compress, onCheckedChange = { compress = it })
                }
                if (compress) {
                    Text(
                        "压缩质量：${compressQuality.toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Slider(
                        value = compressQuality,
                        onValueChange = { compressQuality = it },
                        valueRange = 30f..100f,
                    )
                }
                Button(onClick = {
                    vm.saveGeneral(
                        general.copy(
                            sizeLimitMb = sizeLimit.toIntOrNull() ?: 0,
                            compressImages = compress,
                            compressQuality = compressQuality.toInt(),
                        ),
                    )
                    vm.refreshStorage()
                }) { Text("保存存储设置") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    backupResult?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.consumeBackupResult() },
            title = { Text("操作结果") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { vm.consumeBackupResult() }) { Text("好的") } },
        )
    }
}
