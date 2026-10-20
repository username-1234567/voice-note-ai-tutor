package com.suitanglu.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.suitanglu.app.data.AiConfig

/** 二级页：AI 配置。表单较长，单独一页才放得下且不用滚三屏找按钮 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel()
    val ai by vm.aiConfig.collectAsState()
    val aiTestResult by vm.aiTestResult.collectAsState()
    val aiTesting by vm.aiTesting.collectAsState()

    var apiKey by remember(ai.apiKey) { mutableStateOf(ai.apiKey) }
    var baseUrl by remember(ai.baseUrl) { mutableStateOf(ai.baseUrl) }
    var model by remember(ai.model) { mutableStateOf(ai.model) }
    var stream by remember(ai.stream) { mutableStateOf(ai.stream) }
    var timeout by remember(ai.timeoutSeconds) { mutableStateOf(ai.timeoutSeconds.toString()) }
    var maxTokens by remember(ai.maxTokens) { mutableStateOf(ai.maxTokens.toString()) }
    var prompt by remember(ai.prompt) { mutableStateOf(ai.prompt) }

    // 界面当前填写的配置：保存与测试连接共用，避免"测的是旧配置"
    fun currentAiConfig(): AiConfig = ai.copy(
        apiKey = apiKey.trim(),
        baseUrl = baseUrl.trim(),
        model = model.trim(),
        stream = stream,
        timeoutSeconds = timeout.toIntOrNull() ?: 180,
        maxTokens = maxTokens.toIntOrNull() ?: 4096,
        prompt = prompt,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 配置") },
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
            SettingsSection("连接（DeepSeek）") {
                OutlinedTextField(
                    value = apiKey, onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                OutlinedTextField(
                    value = baseUrl, onValueChange = { baseUrl = it },
                    label = { Text("API Base URL") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                OutlinedTextField(
                    value = model, onValueChange = { model = it },
                    label = { Text("模型名称（默认 deepseek-flash）") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("流式输出", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "回复边生成边显示，长回答不用等整段返回",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = stream, onCheckedChange = { stream = it })
                }
            }

            SettingsSection("生成参数") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = timeout, onValueChange = { timeout = it.filter { c -> c.isDigit() } },
                        label = { Text("超时(秒)") }, modifier = Modifier.weight(1f), singleLine = true,
                    )
                    OutlinedTextField(
                        value = maxTokens, onValueChange = { maxTokens = it.filter { c -> c.isDigit() } },
                        label = { Text("最大输出 token") }, modifier = Modifier.weight(1f), singleLine = true,
                    )
                }
                Text(
                    "最大输出填 0 = 不限制，由模型决定（推荐）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = prompt, onValueChange = { prompt = it },
                    label = { Text("提示词（默认：详细讲解内容）") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.saveAi(currentAiConfig()) }) { Text("保存配置") }
                OutlinedButton(
                    onClick = { vm.saveAndTestAi(currentAiConfig()) },
                    enabled = !aiTesting,
                ) { Text("测试连接") }
            }

            if (aiTesting) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.width(16.dp).height(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("测试中…", style = MaterialTheme.typography.labelMedium)
                }
            }
            aiTestResult?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (it.startsWith("失败")) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }

            Text(
                "API Key 保存在应用私有目录，导出的备份会明文包含它；用户文字备注不会发送给 AI，只发送标记图片和提示词。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
