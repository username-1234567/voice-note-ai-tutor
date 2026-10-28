package com.suitanglu.app.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.suitanglu.app.AppGraph
import com.suitanglu.app.update.InstallResult
import com.suitanglu.app.update.UpdateCheckResult
import com.suitanglu.app.update.UpdateConfig
import com.suitanglu.app.update.UpdateInfo
import com.suitanglu.app.update.UpdateInstaller
import kotlinx.coroutines.launch

private enum class UpdatePhase { Hidden, Prompt, Downloading, Failed }

/**
 * 启动时静默检查一次更新，有新版本就弹窗提醒。
 *
 * 几处刻意的取舍：
 *
 * - **同一版本只提醒一次**。点过「稍后」就把版本号记下来，下次启动不再打扰；
 *   记的是版本号而不是布尔值，所以下一个版本发布时会重新提醒。
 * - **先要权限再下载**。缺「安装未知应用」时直接下载，下完了装不上更让人恼火，
 *   所以先跳授权页，回来后接着下。
 * - **下载失败不记为「稍后」**。失败是网络问题，不是用户拒绝，下次启动仍应提示。
 * - **检查失败安静退出**。离线、GitHub 抽风都不该在启动时糊用户一脸报错，
 *   想看结果可以去设置页手动查，那里有完整的错误信息。
 */
@Composable
fun UpdatePromptHost() {
    val context = LocalContext.current
    val container = remember { AppGraph.get(context) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var phase by remember { mutableStateOf(UpdatePhase.Hidden) }
    var progress by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }
    // 用户被送去授权「安装未知应用」，回来后要接着下载
    var awaitingPermission by remember { mutableStateOf(false) }

    fun dismissAndRemember() {
        val version = info?.version ?: return
        phase = UpdatePhase.Hidden
        scope.launch { runCatching { container.settings.dismissUpdateVersion(version) } }
    }

    fun beginDownload(target: UpdateInfo) {
        if (!UpdateInstaller.canInstall(context)) {
            awaitingPermission = true
            UpdateInstaller.openPermissionSettings(context)
            return
        }
        phase = UpdatePhase.Downloading
        progress = 0
        scope.launch {
            val result = container.updateChecker.download(target) { progress = it }
            result.fold(
                onSuccess = { file ->
                    when (val res = UpdateInstaller.install(context, file)) {
                        is InstallResult.Started -> phase = UpdatePhase.Hidden
                        is InstallResult.NeedPermission -> {
                            awaitingPermission = true
                            phase = UpdatePhase.Prompt
                        }
                        is InstallResult.Failed -> {
                            error = res.message
                            phase = UpdatePhase.Failed
                        }
                    }
                },
                onFailure = {
                    error = it.message ?: "下载失败"
                    phase = UpdatePhase.Failed
                },
            )
        }
    }

    LaunchedEffect(Unit) {
        val result = runCatching { container.updateChecker.check() }.getOrNull()
        if (result is UpdateCheckResult.Available) {
            val dismissed = runCatching { container.settings.dismissedUpdateVersionNow() }
                .getOrDefault("")
            if (dismissed != result.info.version) {
                info = result.info
                phase = UpdatePhase.Prompt
            }
        }
    }

    // 从「安装未知应用」授权页返回：若已拿到权限就接着下载
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && awaitingPermission) {
                awaitingPermission = false
                val target = info
                if (target != null && UpdateInstaller.canInstall(context)) {
                    beginDownload(target)
                }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val current = info ?: return
    if (phase == UpdatePhase.Hidden) return

    AppAlertDialog(
        title = when (phase) {
            UpdatePhase.Downloading -> "正在下载 v${current.version}"
            UpdatePhase.Failed -> "更新失败"
            else -> "发现新版本 v${current.version}"
        },
        confirmLabel = when (phase) {
            UpdatePhase.Downloading -> "下载中"
            UpdatePhase.Failed -> "重试"
            else -> "立即更新"
        },
        confirmEnabled = phase != UpdatePhase.Downloading,
        onConfirm = { beginDownload(current) },
        dismissLabel = if (phase == UpdatePhase.Failed) "关闭" else "稍后",
        showDismiss = phase != UpdatePhase.Downloading,
        onDismissRequest = {
            when (phase) {
                // 下载中不允许关：中途退出会留个残缺包，下次进来还得重下
                UpdatePhase.Downloading -> Unit
                // 失败只是没下成，不算用户拒绝，不记版本，下次启动还会提示
                UpdatePhase.Failed -> phase = UpdatePhase.Hidden
                else -> dismissAndRemember()
            }
        },
    ) {
        when (phase) {
            UpdatePhase.Downloading -> {
                Text(
                    "$progress%",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            UpdatePhase.Failed -> {
                Text(
                    error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            else -> {
                Text(
                    "当前 v${UpdateConfig.CURRENT_VERSION}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val notes = current.changelog.trim()
                if (notes.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .heightIn(max = 190.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}
