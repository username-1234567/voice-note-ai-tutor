package com.suitanglu.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.gestures.detectTransformGestures
import coil.compose.AsyncImage

/**
 * 双指缩放 + 拖动的图片查看器。
 *
 * 只在"图片被放到最大"的场景用（全屏看图），缩略图不动它——
 * 缩略图里加手势会和列表滚动抢事件。
 *
 * 没有做双击复位：双击检测会和缩放手势抢 down 事件，
 * 单指拖动就会失灵；而单指拖动恰恰是放大之后最常用的操作。
 */
@Composable
fun ZoomableImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    background: Color = Color.Black,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var container by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier
            .clipToBounds()
            .background(background)
            .onSizeChanged { container = it }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val next = (scale * zoom).coerceIn(1f, 6f)
                    if (next <= 1.001f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        // 放大后可平移的余量 = (放大后的尺寸 - 容器) / 2
                        val maxX = container.width * (next - 1f) / 2f
                        val maxY = container.height * (next - 1f) / 2f
                        offset = Offset(
                            (offset.x + pan.x).coerceIn(-maxX, maxX),
                            (offset.y + pan.y).coerceIn(-maxY, maxY),
                        )
                        scale = next
                    }
                }
            },
    ) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}
