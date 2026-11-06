package net.alan.jupiter.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * 极客风排版：中文正文用系统黑体，标签与数字统一等宽。
 */
private val QnTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 30.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 26.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 11.5.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium, fontSize = 10.5.sp, lineHeight = 14.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium, fontSize = 9.5.sp, lineHeight = 13.sp
    )
)

/** 圆角统一 ≤2dp；不使用大圆角与阴影。 */
private val QnShapes = Shapes(
    extraSmall = RoundedCornerShape(1.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(2.dp),
    large = RoundedCornerShape(2.dp),
    extraLarge = RoundedCornerShape(2.dp)
)

private val QnColorScheme = darkColorScheme(
    primary = QnAccent,
    onPrimary = QnOnAccent,
    primaryContainer = QnAccentWash,
    onPrimaryContainer = QnAccent,
    secondary = QnTextMuted,
    onSecondary = QnBg,
    secondaryContainer = QnPanelAlt,
    onSecondaryContainer = QnText,
    tertiary = QnAccent,
    onTertiary = QnOnAccent,
    tertiaryContainer = QnAccentWash,
    onTertiaryContainer = QnText,
    background = QnBg,
    onBackground = QnText,
    surface = QnPanel,
    onSurface = QnText,
    surfaceVariant = QnPanelAlt,
    onSurfaceVariant = QnTextMuted,
    error = QnDanger,
    onError = QnBg,
    errorContainer = QnDangerWash,
    onErrorContainer = QnDanger,
    outline = QnLineStrong,
    outlineVariant = QnLine,
    inverseSurface = QnText,
    inverseOnSurface = QnBg,
    inversePrimary = QnAccentDeep,
    scrim = androidx.compose.ui.graphics.Color(0xFF000000)
)

/**
 * 寝安主题：本轮只做暗色（唯一主题），不跟随系统浅色。
 */
@Composable
fun ChinanTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = QnPanelAlt.toArgb()
            window.navigationBarColor = QnBg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = QnColorScheme,
        typography = QnTypography,
        shapes = QnShapes,
        content = content
    )
}
