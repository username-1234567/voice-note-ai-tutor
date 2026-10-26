package com.suitanglu.app.ui.screens.onboarding

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 新手引导：三步，一分钟内能走完。
 *
 * 只做三件事：说清这东西怎么用、把 AI 配好、交代权限。
 * 课程表不在这里配——它有默认时段，多数人上课前才发现要改，
 * 硬塞进引导只会让人第一步就卡住。
 */
@Composable
fun OnboardingScreen(
    onSaveApiKey: (String) -> Unit,
    onDone: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var key by rememberSaveable { mutableStateOf("") }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            // 进度条：让人知道还剩几步，而不是无穷无尽地问下去
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(3) { i ->
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .background(
                                if (i <= step) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                                androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
                            ),
                    )
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp),
            ) {
                Spacer(Modifier.height(24.dp))
                when (step) {
                    0 -> StepWelcome()
                    1 -> StepApiKey(key) { key = it }
                    else -> StepPermissions()
                }
            }

            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Button(
                    onClick = {
                        if (step < 2) {
                            if (step == 1) onSaveApiKey(key.trim())
                            step++
                        } else {
                            onDone()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        when (step) {
                            0 -> "开始"
                            1 -> if (key.isBlank()) "稍后再填" else "保存并继续"
                            else -> "开始使用"
                        },
                    )
                }
                if (step == 1 && key.isNotBlank()) {
                    TextButton(
                        onClick = { key = ""; onSaveApiKey("") },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("清空") }
                }
            }
        }
    }
}

@Composable
private fun StepWelcome() {
    Text("随堂录", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text(
        "上课来不及记，就拍一张。课后让 AI 一张一张讲给你听。",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(28.dp))
    Bullet(Icons.Outlined.GraphicEq, "录音", "点开始就录，锁屏、切后台都不中断。中途来电会自动暂停并记下位置。")
    Spacer(Modifier.height(18.dp))
    Bullet(Icons.Outlined.PhotoCamera, "拍照标记", "看到重点短按拍照（可裁剪写备注），长按直接拍完入库，不打断听课。")
    Spacer(Modifier.height(18.dp))
    Bullet(Icons.Outlined.AutoAwesome, "课后讲解", "回放页点「发送全部」，每张照片都会配一段 AI 讲解，不懂可以接着问。")
}

@Composable
private fun StepApiKey(value: String, onChange: (String) -> Unit) {
    Text("填一个 API Key", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))
    Text(
        "讲解要靠大模型。默认接 DeepSeek，去 platform.deepseek.com 建一个 Key 填进来；" +
            "以后想换别的服务，在「设置 → AI 配置」里改地址和模型即可。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(20.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text("API Key") },
        placeholder = { Text("sk-…") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    Text(
        "Key 只存在这台手机上，不会上传到任何地方。也可以先跳过，用的时候再填。",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StepPermissions() {
    Text("最后，关于权限", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(10.dp))
    Text(
        "录音要麦克风，拍照标记要相机，提醒要通知——第一次用到时会弹系统授权。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))
    Bullet(Icons.Outlined.CheckCircle, "数据只在本地", "录音、照片、AI 回复都存在这台手机上，没有账号也不会上传。卸载即删除，重要内容记得导出备份。")
    Spacer(Modifier.height(18.dp))
    Bullet(Icons.Outlined.CheckCircle, "课程表可以以后配", "设置 → 课程表，按星期几设好时段和课程名，录音会自动按课名命名。")
    Spacer(Modifier.height(18.dp))
    Bullet(Icons.Outlined.CheckCircle, "随时能改", "所有设置都在首页右上角的齿轮里。")
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun Bullet(icon: ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth()) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp).size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
