package com.suitanglu.app.ui.screens.settings

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suitanglu.app.data.CameraMode
import com.suitanglu.app.ui.components.SecondaryPage
import com.suitanglu.app.ui.theme.recordColors

/** 二级页：录音与权限（拍照用的相机、保活与通知权限）。改动即存，没有保存键 */
@Composable
fun RecordingSettingsScreen(onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel()
    val general by vm.generalConfig.collectAsState()
    val context = LocalContext.current

    // 授权状态每次回到前台（ON_RESUME）重读一次：去系统设置授权后回来，
    // 行内状态立即跟上，不用退出设置页再进
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var batteryIgnored by remember { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        fun refresh() {
            batteryIgnored = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            notificationsEnabled = if (Build.VERSION.SDK_INT >= 33) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.areNotificationsEnabled()
            } else {
                true
            }
        }
        refresh()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SecondaryPage(title = "录音与权限", onBack = onBack) {
        SettingsSection("相机") {
            Text("标记拍照时使用的相机", style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = general.cameraMode == CameraMode.SYSTEM,
                    onClick = { vm.saveGeneral(general.copy(cameraMode = CameraMode.SYSTEM)) },
                    label = { Text("系统相机") },
                )
                FilterChip(
                    selected = general.cameraMode == CameraMode.IN_APP,
                    onClick = { vm.saveGeneral(general.copy(cameraMode = CameraMode.IN_APP)) },
                    label = { Text("应用内相机") },
                )
            }
            Text(
                "系统相机兼容性好；若录音中调用系统相机导致录音中断，请切换为应用内相机。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsSection("权限与保活") {
            Text(
                "为防止录音被系统杀死，建议忽略电池优化并允许后台运行。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 已授权给状态行（CheckCircle + 「已开启」双通道）；未授权才显示按钮
            if (batteryIgnored) {
                PermissionStatusRow("忽略电池优化")
            } else {
                Text(
                    "忽略电池优化未开启：省电策略可能中断后台录音。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.requestIgnoreBatteryOptimization() }) { Text("忽略电池优化") }
                    OutlinedButton(onClick = { vm.openAppWhitelistSettings() }) { Text("应用详情设置") }
                }
            }
            if (Build.VERSION.SDK_INT >= 33) {
                if (notificationsEnabled) {
                    PermissionStatusRow("通知权限")
                } else {
                    Text(
                        "通知权限未开启：录音常驻通知显示不出来。",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // 直达本应用的通知设置页，不用让用户自己去系统设置里翻
                    OutlinedButton(onClick = { vm.openNotificationSettings() }) { Text("去通知设置") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** 已授权状态行：CheckCircle 图标 + 「已开启」文字，颜色与文字双通道（与 StatusChip 口径一致） */
@Composable
private fun PermissionStatusRow(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.recordColors.success,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        Text(
            "已开启",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.recordColors.success,
        )
    }
}
