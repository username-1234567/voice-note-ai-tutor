package com.suitanglu.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.suitanglu.app.ui.theme.MotionSpec
import com.suitanglu.app.ui.theme.pressFeedback
import com.suitanglu.app.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 全应用统一的确认弹窗。
 *
 * 结构固定为：图标容器（可选）→ 标题 → 正文 → 自定义内容 → 按钮区。
 * 按钮按重要性分级而不是按语义：主操作实心、次操作纯文本；
 * 破坏性操作只在确认弹窗里升格为主按钮，底色用 errorContainer（浅底深字），
 * 而不是一整块高饱和红——后者会成为整屏唯一的红色块，压过标题本身。
 *
 * 动效：蒙版淡入、卡片从 0.95 缩放到位（200ms），关闭时先播 120ms 退场再回调，
 * 避免点"删除"后弹窗凭空消失。系统关闭动画时全部直接到位。
 *
 * @param destructive 不可撤销的操作（彻底删除、仅删音频），确认键转为 error 色系
 * @param confirmEnabled false 时确认键置灰（例如输入框还空着）
 * @param showDismiss false 时只保留一个按钮（用于"必须处理"的阻断式提示）
 */
@Composable
fun AppAlertDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    message: String? = null,
    destructive: Boolean = false,
    showIcon: Boolean = false,
    dismissLabel: String = "取消",
    showDismiss: Boolean = true,
    confirmEnabled: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val reduced = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    // 首帧不可见，组合完成后再置 true，让进场动画有起点
    var visible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { visible = true }

    // 先退场，再执行动作：确认类操作值得这 120ms，用户能看见"我点的那下生效了"
    fun close(then: () -> Unit) {
        if (closing) return
        closing = true
        visible = false
        scope.launch {
            if (!reduced) delay(MotionSpec.DIALOG_EXIT_MS.toLong())
            then()
        }
    }

    Dialog(
        onDismissRequest = { close(onDismissRequest) },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val scrimAlpha by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(
                if (visible) MotionSpec.DIALOG_ENTER_MS else MotionSpec.DIALOG_EXIT_MS,
                easing = FastOutSlowInEasing,
            ),
            label = "scrim",
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.34f * scrimAlpha))
                .safeDrawingPadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "关闭",
                ) { close(onDismissRequest) },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(MotionSpec.DIALOG_ENTER_MS, easing = FastOutSlowInEasing)) +
                    scaleIn(
                        animationSpec = tween(MotionSpec.DIALOG_ENTER_MS, easing = FastOutSlowInEasing),
                        initialScale = MotionSpec.POP_IN_SCALE,
                    ),
                exit = fadeOut(tween(MotionSpec.DIALOG_EXIT_MS)) +
                    scaleOut(
                        animationSpec = tween(MotionSpec.DIALOG_EXIT_MS, easing = FastOutSlowInEasing),
                        targetScale = MotionSpec.POP_OUT_SCALE,
                    ),
            ) {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 28.dp)
                        .widthIn(max = 340.dp)
                        .fillMaxWidth()
                        // 消费卡片内的空白点击，否则会穿透到蒙版把弹窗关掉
                        .pointerInput(Unit) { detectTapGestures { } },
                    shape = MaterialTheme.shapes.extraLarge,
                    // surfaceBright：浅色下是纯白、深色下比页面底色亮一档，
                    // 浮层的"浮起感"来自这里，不靠再叠一层深阴影
                    color = MaterialTheme.colorScheme.surfaceBright,
                    shadowElevation = 10.dp,
                ) {
                    Column(
                        Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                    ) {
                        if (showIcon) {
                            // 40dp 容器 + 20dp 图标 + 10dp 内边距：容器圆角 10dp 与之同心
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (destructive) {
                                            MaterialTheme.colorScheme.errorContainer
                                        } else {
                                            MaterialTheme.colorScheme.primaryContainer
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.WarningAmber,
                                    contentDescription = null,
                                    tint = if (destructive) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    },
                                    modifier = Modifier.size(21.dp),
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                        }

                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        if (message != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        if (content != null) {
                            Spacer(Modifier.height(16.dp))
                            content()
                        }

                        Spacer(Modifier.height(20.dp))

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (showDismiss) {
                                val dismissSource = remember { MutableInteractionSource() }
                                TextButton(
                                    onClick = { close(onDismissRequest) },
                                    modifier = Modifier
                                        .height(44.dp)
                                        .pressFeedback(dismissSource, MotionSpec.PRESS_SCALE_WIDE),
                                    interactionSource = dismissSource,
                                    contentPadding = PaddingValues(horizontal = 14.dp),
                                ) {
                                    Text(
                                        dismissLabel,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            val confirmSource = remember { MutableInteractionSource() }
                            Button(
                                onClick = { close(onConfirm) },
                                enabled = confirmEnabled,
                                modifier = Modifier
                                    .height(44.dp)
                                    .pressFeedback(confirmSource, MotionSpec.PRESS_SCALE_WIDE),
                                interactionSource = confirmSource,
                                shape = MaterialTheme.shapes.small,
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                colors = if (destructive) {
                                    // 破坏性确认键用 error 实心：这是整屏唯一的高饱和块，
                                    // 它就该抢眼——用户在这里停顿半秒是好事。
                                    // （图标容器仍用 errorContainer 浅底，避免出现两块同样重的红）
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                    )
                                } else {
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    )
                                },
                            ) {
                                Text(
                                    confirmLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
