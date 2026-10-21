package com.suitanglu.app.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 系统是否关闭了动画（开发者选项或无障碍里把"动画时长"调成 0）。
 *
 * 开启后所有装饰性动效直接到位：页面切换、列表出场、圆点呼吸。
 * 录音环的音量条与计时不受影响——那是数据本身，不是装饰。
 *
 * 只在进入页面时读一次：用户中途改这个开关的情况极少，
 * 为此挂一个 ContentObserver 不划算。
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
}
