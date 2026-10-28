package com.suitanglu.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ===== 基调：克制的工具理性 =====
// 录音是后台任务，界面要安静：低饱和绿灰 + 白色卡片浮在浅灰页面上，
// 不用渐变、不用玻璃拟态、不用高饱和强调色。颜色只用来强化状态与操作层级。

/**
 * 是否跟随系统壁纸取色（Material You）。
 *
 * 默认关闭：动态取色会让同一个 App 在不同手机上变成完全不同的两套色，
 * 与固定的品牌绿（录音圆环、进度条）打架，是界面显得"配色很乱"的根因。
 * 需要时可在 RecordTheme 调用处传 true 恢复。
 */
private const val USE_DYNAMIC_COLOR = false

private val LightColors = lightColorScheme(
    primary = Color(0xFF3B5BA5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4F7),
    onPrimaryContainer = Color(0xFF0A1633),
    inversePrimary = Color(0xFFAFBFF0),

    secondary = Color(0xFF59627A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E6F0),
    onSecondaryContainer = Color(0xFF161C2A),

    tertiary = Color(0xFF7A5A2E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF7E7CB),
    onTertiaryContainer = Color(0xFF271A00),

    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFBE3E1),
    onErrorContainer = Color(0xFF410E0B),

    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF141A26),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF141A26),
    surfaceVariant = Color(0xFFE3E6EE),
    onSurfaceVariant = Color(0xFF555D6E),
    surfaceTint = Color(0xFF3B5BA5),

    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE4E8F0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBFCFE),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEFF2F8),
    surfaceContainerHighest = Color(0xFFE6EAF3),

    outline = Color(0xFFB4BAC8),
    outlineVariant = Color(0xFFD7DCE7),
    inverseSurface = Color(0xFF2A303C),
    inverseOnSurface = Color(0xFFEDEFF4),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAEC0F5),
    onPrimary = Color(0xFF10203F),
    primaryContainer = Color(0xFF253866),
    onPrimaryContainer = Color(0xFFD9E2FF),
    inversePrimary = Color(0xFF3B5BA5),

    secondary = Color(0xFFB9C1D4),
    onSecondary = Color(0xFF232A38),
    secondaryContainer = Color(0xFF394255),
    onSecondaryContainer = Color(0xFFD6DCEA),

    tertiary = Color(0xFFE0B87A),
    onTertiary = Color(0xFF452C00),
    tertiaryContainer = Color(0xFF4A3510),
    onTertiaryContainer = Color(0xFFFBDFAE),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF0E1118),
    onBackground = Color(0xFFE3E6EE),
    surface = Color(0xFF141821),
    onSurface = Color(0xFFE3E6EE),
    surfaceVariant = Color(0xFF3A4150),
    onSurfaceVariant = Color(0xFFA8AFBE),
    surfaceTint = Color(0xFFAEC0F5),

    surfaceBright = Color(0xFF2C3341),
    surfaceDim = Color(0xFF0E1118),
    surfaceContainerLowest = Color(0xFF090C12),
    surfaceContainerLow = Color(0xFF141821),
    surfaceContainer = Color(0xFF181D27),
    surfaceContainerHigh = Color(0xFF222835),
    surfaceContainerHighest = Color(0xFF2C3341),

    outline = Color(0xFF6E7688),
    outlineVariant = Color(0xFF3A4150),
    inverseSurface = Color(0xFFE3E6EE),
    inverseOnSurface = Color(0xFF2C3341),
    scrim = Color(0xFF000000),
)

/**
 * 语义色：M3 色板里没有"待发送 / 成功 / 打断"这类产品语义，
 * 集中在这里定义，避免各页面各写一套硬编码色。
 */
@Immutable
data class RecordColors(
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val pending: Color,
    val pendingContainer: Color,
    val onPendingContainer: Color,
    /** 进度条上的暂停/打断点（琥珀，与"失败红"区分开） */
    val pauseMark: Color,
    /** 课程名 → 稳定颜色；深色模式用更亮的一档，保证在深底上可辨 */
    val coursePalette: List<Color>,
)

private val LightExtra = RecordColors(
    success = Color(0xFF16695A),
    successContainer = Color(0xFFD2EDE6),
    onSuccessContainer = Color(0xFF04241D),
    pending = Color(0xFF8A5A00),
    pendingContainer = Color(0xFFF7E7CB),
    onPendingContainer = Color(0xFF271A00),
    // 浅色轨道上实测：原 #E08A00 只有 2.24:1，降到 #C2410C 后 4.30:1（非文本图形需 ≥3:1）
    pauseMark = Color(0xFFC2410C),
    // 首位用绿而不是主色靛蓝：这套色板的功能是"区分不同科目"，
    // 和主色同调会让第一门课看起来像被"强调"，而不是被"分类"
    coursePalette = listOf(
        Color(0xFF246B5A),
        Color(0xFF3B6FA0),
        Color(0xFF7A57B8),
        Color(0xFFC97A38),
        Color(0xFFB0507F),
        Color(0xFF2A7E99),
        Color(0xFF5468B4),
        Color(0xFF7D6257),
    ),
)

private val DarkExtra = RecordColors(
    success = Color(0xFF6FCFBC),
    successContainer = Color(0xFF10332C),
    onSuccessContainer = Color(0xFFB9EFDD),
    pending = Color(0xFFE7B96B),
    pendingContainer = Color(0xFF3D2E12),
    onPendingContainer = Color(0xFFFBDFAE),
    pauseMark = Color(0xFFFFB74D),
    coursePalette = listOf(
        Color(0xFF6FD3B6),
        Color(0xFF7FAEDA),
        Color(0xFFB49AE8),
        Color(0xFFE9A87A),
        Color(0xFFE38BB2),
        Color(0xFF6FC6CE),
        Color(0xFF95A6EE),
        Color(0xFFC2A79B),
    ),
)

private val LocalRecordColors = staticCompositionLocalOf { LightExtra }

/** 语义色入口：`MaterialTheme.recordColors.pending` */
val MaterialTheme.recordColors: RecordColors
    @Composable @ReadOnlyComposable get() = LocalRecordColors.current

/** 品牌强调色（录音圆环、进度条已播放段）。跟随主题，不再另起一套色 */
@Composable
fun BrandAccent(): Color = MaterialTheme.colorScheme.primary

/** 数字等宽（计时器、时间标签不左右跳动） */
fun TextStyle.tabularNumbers(): TextStyle = copy(fontFeatureSettings = "tnum")

// 只用两档圆角：10dp = 控件（输入框、缩略图、图标块、行高亮），16dp = 容器（卡片、横幅）。
// 之前 8/12/14/18 混用，同一屏能数出五种圆角，视觉上就是"没规矩"。
// 换靛蓝主题时一度改成 M3 标准的 8/10/12/16/20，等于把这套约定又拆了，已改回两档。
private val RecordShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

// 中文界面：正文行高按 1.5 倍留白，标题收紧；层级靠字重与颜色，不靠一味放大字号
val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 25.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp),
)

@Composable
fun RecordTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = USE_DYNAMIC_COLOR,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalRecordColors provides if (darkTheme) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = RecordShapes,
            content = content,
        )
    }
}
