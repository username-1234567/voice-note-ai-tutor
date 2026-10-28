package com.suitanglu.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.suitanglu.app.util.AppIconManager

/**
 * 设置页里的图标预览，直接用 Canvas 画，不加载 adaptive-icon。
 *
 * 用 `painterResource(R.mipmap.ic_launcher_xxx)` 加载 `<adaptive-icon>` 在 Compose 里
 * 并不可靠——它需要 Android 的 AdaptiveIconDrawable 参与测量，尺寸给不到位时
 * 轻则空白、重则整页测量卡住（表现就是"设置页点不动"）。这里按同一套几何重画，
 * 与真实图标保持一致。
 */
@Composable
fun IconPreview(alias: String, modifier: Modifier = Modifier) {
    val (start, end) = when (alias) {
        AppIconManager.TEAL -> Color(0xFF25937B) to Color(0xFF0C4F41)
        AppIconManager.CLAY -> Color(0xFFB06A3E) to Color(0xFF6B3A1E)
        AppIconManager.INK -> Color(0xFF3A4150) to Color(0xFF14181F)
        else -> Color(0xFF4A6BC0) to Color(0xFF26386B)
    }

    Canvas(modifier) {
        val k = size.minDimension / 108f
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(start, end),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
            size = size,
        )
        val c = Offset(54f * k, 54f * k)
        // 中心实心圆 r=8
        drawCircle(Color.White, radius = 8f * k, center = c)
        // 内弧 r=17：左 135°→225°，右 315°→45°
        val inner = 17f * k
        drawArc(
            color = Color.White,
            startAngle = 135f, sweepAngle = 90f, useCenter = false,
            topLeft = Offset(c.x - inner, c.y - inner),
            size = Size(inner * 2, inner * 2),
            style = Stroke(width = 6f * k, cap = StrokeCap.Round),
        )
        drawArc(
            color = Color.White,
            startAngle = 315f, sweepAngle = 90f, useCenter = false,
            topLeft = Offset(c.x - inner, c.y - inner),
            size = Size(inner * 2, inner * 2),
            style = Stroke(width = 6f * k, cap = StrokeCap.Round),
        )
        // 外弧 r=26，半透明
        val outer = 26f * k
        drawArc(
            color = Color.White.copy(alpha = 0.6f),
            startAngle = 135f, sweepAngle = 90f, useCenter = false,
            topLeft = Offset(c.x - outer, c.y - outer),
            size = Size(outer * 2, outer * 2),
            style = Stroke(width = 5f * k, cap = StrokeCap.Round),
        )
        drawArc(
            color = Color.White.copy(alpha = 0.6f),
            startAngle = 315f, sweepAngle = 90f, useCenter = false,
            topLeft = Offset(c.x - outer, c.y - outer),
            size = Size(outer * 2, outer * 2),
            style = Stroke(width = 5f * k, cap = StrokeCap.Round),
        )
    }
}
