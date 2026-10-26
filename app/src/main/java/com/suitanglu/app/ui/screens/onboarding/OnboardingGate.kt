package com.suitanglu.app.ui.screens.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.suitanglu.app.AppGraph
import kotlinx.coroutines.launch

/**
 * 首次使用时挡一层引导。
 *
 * 两个条件同时成立才显示：没走过引导、且一条录音都没有。
 * 只看"没走过引导"会把已经攒了几十条录音的老用户也拦下来——他们只是没经历过这个版本。
 */
@Composable
fun OnboardingGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val container = remember { AppGraph.get(context.applicationContext) }
    // null = 还在判断，先铺一层背景，避免闪一下主页再跳到引导
    var show by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        val onboarded = runCatching { container.settings.isOnboarded() }.getOrDefault(true)
        val hasData = runCatching {
            container.db.recordingDao().getAll().isNotEmpty()
        }.getOrDefault(true)
        show = !onboarded && !hasData
    }

    when (show) {
        true -> OnboardingScreen(
            onSaveApiKey = { key ->
                if (key.isNotBlank()) {
                    container.appScope.launch {
                        runCatching {
                            val ai = container.settings.aiConfigNow()
                            container.settings.setAiConfig(ai.copy(apiKey = key))
                        }
                    }
                }
            },
            onDone = {
                container.appScope.launch { runCatching { container.settings.setOnboarded() } }
                show = false
            },
        )

        false -> content()

        null -> Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize())
        }
    }
}
