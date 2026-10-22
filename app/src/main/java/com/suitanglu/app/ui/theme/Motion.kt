package com.suitanglu.app.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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

/**
 * 全应用动效刻度。
 *
 * 只定义"用得到的那几档"，不做成可配置的设计系统——
 * 同一类动作在哪儿都是同一个数，才有可能看着是"一个东西在做动作"。
 *
 * 时长依据：按下要立刻有回应（约 90ms 到位），松开比按下慢（160ms，回弹比下压软）；
 * 退出永远比进入短（约 60–65%），因为注意力已经移到下一件事上。
 */
object MotionSpec {
    /** 按下到位：约 90ms，短到几乎和手指同步 */
    const val PRESS_IN_MS = 90

    /** 松手回弹：160ms，比下压慢，手感才"弹"而不是"顶" */
    const val PRESS_OUT_MS = 160

    /** 弹窗进场：200ms，从 0.95 起缩放 */
    const val DIALOG_ENTER_MS = 200

    /** 弹窗退场：120ms */
    const val DIALOG_EXIT_MS = 120

    /** 整页切换：360ms。规范区间是 350–500ms，低于 350 会显得"闪了一下"，
     *  高于 500 用户就开始等——取区间下沿 */
    const val PAGE_ENTER_MS = 360

    /** 整页退场：240ms（进入的 67%），离开永远比进入快 */
    const val PAGE_EXIT_MS = 240

    /** 配角动作的起步延迟：主角先动，其余晚 40ms 跟上 */
    const val FOLLOW_MS = 40

    /** 常规按钮按下缩放（面积较大，过冲控制在 4% 以内） */
    const val PRESS_SCALE = 0.96f

    /** 长条按钮按下缩放（横向尺寸大，缩再多会晃） */
    const val PRESS_SCALE_WIDE = 0.98f

    /** 小图标按钮按下缩放（小物件可以有 8–10% 的形变） */
    const val PRESS_SCALE_ICON = 0.90f

    /** 弹窗/浮层的缩放起点：不从 0 开始，否则像"凭空长出来" */
    const val POP_IN_SCALE = 0.95f

    /** 弹窗退出的缩放终点 */
    const val POP_OUT_SCALE = 0.97f
}

/**
 * 按下即缩、松手即回的触觉反馈。
 *
 * 这是"按钮按下去毫无反应"的最小解：只动 transform，不碰布局，
 * 不会引起任何重排；缩放下限 0.90 起，面积越大缩得越少。
 *
 * 用法必须把同一个 [InteractionSource] 同时交给 `clickable` / `IconButton`，
 * 否则这里收不到按下事件。
 *
 * @param pressedScale 按下时的缩放比例，默认 0.96
 */
@Composable
fun Modifier.pressFeedback(
    interactionSource: InteractionSource,
    pressedScale: Float = MotionSpec.PRESS_SCALE,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val reduced = rememberReducedMotion()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) pressedScale else 1f,
        animationSpec = tween(
            durationMillis = if (pressed) MotionSpec.PRESS_IN_MS else MotionSpec.PRESS_OUT_MS,
            easing = FastOutSlowInEasing,
        ),
        label = "pressScale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
