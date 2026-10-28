package com.suitanglu.app.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * 应用图标切换。
 *
 * Android 没有"运行时换图标"的 API，标准做法是声明多个 `activity-alias`，
 * 每个 alias 指向同一个 Activity、带不同 icon，运行时只启用其中一个。
 * 见 AndroidManifest 里的 `.iconIndigo / .iconTeal / .iconClay / .iconInk`。
 *
 * [apply] 里的兜底很重要：如果传入的 alias 不在白名单内，回退到 INDIGO。
 * 万一某个版本的 alias 名改了、而用户设置里存着旧值，全部禁用的结果就是
 * 桌面上找不到这个 App。
 */
object AppIconManager {

    const val INDIGO = "iconIndigo"
    const val TEAL = "iconTeal"
    const val CLAY = "iconClay"
    const val INK = "iconInk"

    val ALL = listOf(INDIGO, TEAL, CLAY, INK)

    fun labelOf(alias: String): String = when (alias) {
        TEAL -> "松绿"
        CLAY -> "陶土"
        INK -> "墨黑"
        else -> "靛蓝"
    }

    fun apply(context: Context, alias: String) {
        val target = alias.takeIf { it in ALL } ?: INDIGO
        val pkg = context.packageName
        val pm = context.packageManager
        ALL.forEach { name ->
            val state = if (name == target) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            runCatching {
                pm.setComponentEnabledSetting(
                    ComponentName(pkg, "$pkg.$name"),
                    state,
                    // DONT_KILL_APP：只换图标，不该把正在录音的进程杀掉
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }
}
