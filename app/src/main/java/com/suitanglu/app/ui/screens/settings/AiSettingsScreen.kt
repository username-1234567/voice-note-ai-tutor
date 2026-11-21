package com.suitanglu.app.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suitanglu.app.ai.AiPrompts
import com.suitanglu.app.data.AiConfig
import com.suitanglu.app.ui.components.BottomActionRow
import com.suitanglu.app.ui.components.SecondaryPage

/** 思考强度四档；用列表驱动 UI，同时充当非法值的兜底白名单 */
private val EFFORT_OPTIONS = listOf(
    "off" to "关闭",
    "low" to "低",
    "high" to "高",
    "max" to "最高",
)

/** 讲解风格：决定 system 消息，长期不变，换风格等于换一个助教 */
private val STYLE_OPTIONS = listOf(
    "general" to "通用助教",
    "math" to "数学 Pro",
)

/** 讲解重点：追加在用户消息里，随时可换，不影响上下文缓存的前缀 */
private val MODE_OPTIONS = listOf(
    "auto" to "默认",
    "steps" to "逐步推导",
    "intuition" to "直观理解",
    "check" to "笔记检查",
)

private fun styleHint(style: String): String = if (style == "math") {
    "数学专业口径：先理解再推导，每个符号都解释，不跳步、不用「显然」带过。"
} else {
    "跨学科：按图片内容判断学科，数学重推导、文史重脉络、语言重用法。"
}

private fun modeHint(mode: String): String = when (mode) {
    "steps" -> "逐行解释每一次变形：做了什么、为什么可以、用了哪个定理、需要什么条件。"
    "intuition" -> "先用例子、图像和特殊情况建立直观，最后再回到严格定义。"
    "check" -> "检查笔记里的公式、步骤和定理条件，区分明确错误、可能有问题和不够严谨。"
    else -> "先讲这页在讲什么，再讲概念和推导，最后给例子和易错点。"
}

/** 二级页：AI 配置。表单较长，保存常驻底部，不用滚到底才能存 */
@OptIn(ExperimentalLayoutApi::class)
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
    // 兜底成 high：配置里存了非法值（手改或旧版本残留）时，不能让四个档位全都不选中
    var effort by remember(ai.reasoningEffort) {
        mutableStateOf(ai.reasoningEffort.takeIf { v -> EFFORT_OPTIONS.any { it.first == v } } ?: "high")
    }
    var promptStyle by remember(ai.promptStyle) {
        mutableStateOf(ai.promptStyle.takeIf { v -> STYLE_OPTIONS.any { it.first == v } } ?: "general")
    }
    var explainMode by remember(ai.explainMode) {
        mutableStateOf(ai.explainMode.takeIf { v -> MODE_OPTIONS.any { it.first == v } } ?: "auto")
    }
    var enrich by remember(ai.enrich) { mutableStateOf(ai.enrich) }

    // 界面当前填写的配置：保存与测试连接共用，避免"测的是旧配置"
    fun currentAiConfig(): AiConfig = ai.copy(
        apiKey = apiKey.trim(),
        baseUrl = baseUrl.trim(),
        model = model.trim(),
        stream = stream,
        timeoutSeconds = timeout.toIntOrNull() ?: 180,
        maxTokens = maxTokens.toIntOrNull() ?: 4096,
        prompt = prompt,
        promptStyle = promptStyle,
        explainMode = explainMode,
        enrich = enrich,
        reasoningEffort = effort,
    )

    // 任一项与已保存的配置不同就算有改动：返回时要问一句，否则填了半天的 Key 直接没
    val dirty = apiKey.trim() != ai.apiKey ||
        baseUrl.trim() != ai.baseUrl ||
        model.trim() != ai.model ||
        stream != ai.stream ||
        timeout != ai.timeoutSeconds.toString() ||
        maxTokens != ai.maxTokens.toString() ||
        prompt != ai.prompt ||
        promptStyle != ai.promptStyle ||
        explainMode != ai.explainMode ||
        enrich != ai.enrich ||
        effort != ai.reasoningEffort

    SecondaryPage(
        title = "AI 配置",
        onBack = onBack,
        unsavedChanges = dirty,
        bottomBar = {
            BottomActionRow {
                Button(
                    onClick = { vm.saveAi(currentAiConfig()) },
                    modifier = Modifier.weight(1f),
                ) { Text("保存配置") }
                OutlinedButton(
                    onClick = { vm.saveAndTestAi(currentAiConfig()) },
                    enabled = !aiTesting,
                ) { Text(if (aiTesting) "测试中…" else "测试连接") }
            }
        },
    ) {
        SettingsSection("连接") {
            OutlinedTextField(
                value = apiKey, onValueChange = { apiKey = it },
                label = { Text("API Key") },
                placeholder = { Text("sk-…") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            OutlinedTextField(
                value = baseUrl, onValueChange = { baseUrl = it },
                label = { Text("接口地址") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            OutlinedTextField(
                value = model, onValueChange = { model = it },
                label = { Text("模型名称") },
                supportingText = { Text("默认 deepseek-chat") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            SwitchRow(
                title = "流式输出",
                subtitle = "回复边生成边显示，长回答不用等整段返回",
                checked = stream,
                onCheckedChange = { stream = it },
            )
            // 测试结果就地显示在连接区，而不是飘在按钮下方
            AnimatedVisibility(
                visible = aiTestResult != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                aiTestResult?.let { result ->
                    val ok = !result.startsWith("失败")
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (ok) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                                },
                                RoundedCornerShape(10.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            if (ok) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp).padding(top = 1.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            result,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (ok) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        SettingsSection("生成参数") {
            // 思考强度放最前面：它同时决定"等多久"和"花多少钱"，是最常被调的一项
            Text("思考强度", style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EFFORT_OPTIONS.forEach { (value, label) ->
                    FilterChip(
                        selected = effort == value,
                        onClick = { effort = value },
                        label = { Text(label) },
                    )
                }
            }
            Text(
                when (effort) {
                    "off" -> "不思考，直接作答。最快、最省，但推导密集的板书容易讲错。"
                    "low" -> "轻度思考。讲解课件照片通常够用。"
                    "high" -> "默认档。适合有明显推导过程的科目。"
                    else -> "最深一档。慢且贵，留给公式推导、证明这类内容。"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!ai.baseUrl.contains("deepseek", ignoreCase = true)) {
                Text(
                    "当前接口不是 DeepSeek 官方地址，思考参数不会下发，此项不生效。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = timeout, onValueChange = { timeout = it.filter { c -> c.isDigit() } },
                    label = { Text("超时（秒）") }, modifier = Modifier.weight(1f), singleLine = true,
                )
                OutlinedTextField(
                    value = maxTokens, onValueChange = { maxTokens = it.filter { c -> c.isDigit() } },
                    label = { Text("最大输出 token") }, modifier = Modifier.weight(1f), singleLine = true,
                )
            }
            Text(
                "最大输出填 0 表示不限制，由模型决定（推荐）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsSection("讲解方式") {
            Text("讲解风格", style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                STYLE_OPTIONS.forEach { (value, label) ->
                    FilterChip(
                        selected = promptStyle == value,
                        onClick = { promptStyle = value },
                        label = { Text(label) },
                    )
                }
            }
            Text(
                styleHint(promptStyle),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))
            Text("讲解重点", style = MaterialTheme.typography.bodyLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                MODE_OPTIONS.forEach { (value, label) ->
                    FilterChip(
                        selected = explainMode == value,
                        onClick = { explainMode = value },
                        label = { Text(label) },
                    )
                }
            }
            Text(
                modeHint(explainMode),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SwitchRow(
                title = "讲完给追问建议和自测题",
                subtitle = "3 个可点的追问 + 1 道单选题，跟着同一轮回复一起出，不额外发请求",
                checked = enrich,
                onCheckedChange = { enrich = it },
            )

            OutlinedTextField(
                value = prompt, onValueChange = { prompt = it },
                label = { Text("提示词") },
                supportingText = { Text("每次讲解都会带上；课程名、拍摄时间和备注由应用自动补充，不用写在这里") },
                modifier = Modifier.fillMaxWidth(), minLines = 3,
            )
            // 默认提示词现在是一整段结构化任务，改坏了没法凭记忆敲回来
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = { prompt = AiPrompts.DEFAULT_TASK },
                    enabled = prompt.trim() != AiPrompts.DEFAULT_TASK.trim(),
                ) { Text("恢复默认提示词", style = MaterialTheme.typography.labelMedium) }
            }
        }

        Text(
            "API Key 以明文保存在应用私有目录，导出的备份也会包含它。" +
                "讲解时发送给 AI 的是：标记图片、课程名、拍摄时间、你的备注和这段提示词。",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
}

/** 开关行：标题 + 一句解释 + 右侧开关，整行可点 */
@Composable
internal fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
