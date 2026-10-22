package com.suitanglu.app.ui.screens.render

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.suitanglu.app.ui.screens.playback.MarkdownView
import kotlinx.coroutines.delay

/**
 * 渲染预览：把从别处复制来的原始 Markdown/LaTeX 贴进来，直接看排好版的样子。
 *
 * 场景很具体——DeepSeek 官方应用里复制出来的回答是一堆 `$...$`、`**`、`|---|`，
 * 看着像乱码。这里不调模型、不存数据，纯粹是把对话页那套渲染器借过来用一遍。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenderScreen(onBack: () -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    // 渲染用另一份状态，输入停顿 300ms 才同步过去：
    // MarkdownView 每次刷新都要重跑一遍 WebView，逐字符跟着打字会明显卡
    var rendered by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(input) {
        delay(300)
        rendered = input
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("渲染预览") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            input = clipboard.getText()?.text.orEmpty()
                            rendered = input
                        },
                    ) { Text("粘贴") }
                    if (input.isNotEmpty()) {
                        IconButton(onClick = { input = ""; rendered = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "清空")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = {
                    Text("把带 **Markdown** 和 \$LaTeX\$ 的原文贴进来，下面会渲染成排版后的样子")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .heightIn(min = 120.dp, max = 220.dp),
                textStyle = MaterialTheme.typography.bodySmall,
            )

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${input.length} 字",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (input.isEmpty()) {
                    TextButton(onClick = { input = SAMPLE; rendered = SAMPLE }) {
                        Text("填个示例")
                    }
                }
            }

            HorizontalDivider(
                Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (rendered.isBlank()) {
                    Column(
                        Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "渲染结果会出现在这里",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    MarkdownView(rendered, Modifier.fillMaxWidth().padding(12.dp))
                }
            }
        }
    }
}

private val SAMPLE = """
## 一、费米能级

在热平衡下，电子服从 **费米-狄拉克分布**：

${'$'}${'$'}f(E)=\frac{1}{1+e^{(E-E_F)/k_BT}}${'$'}${'$'}

当 ${'$'}T\to 0${'$'} 时，分布退化为阶跃函数：

- ${'$'}E<E_F${'$'}：${'$'}f(E)=1${'$'}
- ${'$'}E>E_F${'$'}：${'$'}f(E)=0${'$'}

| 温度 | 费米能级位置 |
|---|---|
| 0 K | 严格等于 ${'$'}E_F${'$'} |
| 300 K | 近似不变 |

> 注意：这里的 ${'$'}E_F${'$'} 是化学势在 0 K 的取值。
""".trimIndent()
