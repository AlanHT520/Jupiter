package net.alan.jupiter.ui.theme

import android.app.Activity
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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ── MD3 Type Scale ──
// 遵循 Material Design 3 标准比例：display > headline > title > body > label
private val QnTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.Bold, fontSize = 45.sp, lineHeight = 52.sp
    ),
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp
    )
)

/** 等宽字体只给数字 / 日期 / ID / 房间号，全仓只在这里定义一次。 */
val QnMono = FontFamily.Monospace

// ── MD3 Shape Scale（4 / 8 / 12 / 16 / 28） ──
// 卡片 large(16dp)、Chip small(8dp)、输入框 medium(12dp)、对话框 extraLarge(28dp)
private val QnShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// ── MD3 Color Schemes ──
private val QnDarkScheme = darkColorScheme(
    primary = MdDarkPrimary,
    onPrimary = MdDarkOnPrimary,
    primaryContainer = MdDarkPrimaryContainer,
    onPrimaryContainer = MdDarkOnPrimaryContainer,
    inversePrimary = MdLightPrimary,
    secondary = MdDarkSecondary,
    onSecondary = MdDarkOnSecondary,
    secondaryContainer = MdDarkSecondaryContainer,
    onSecondaryContainer = MdDarkOnSecondaryContainer,
    tertiary = MdDarkTertiary,
    onTertiary = MdDarkOnTertiary,
    tertiaryContainer = MdDarkTertiaryContainer,
    onTertiaryContainer = MdDarkOnTertiaryContainer,
    background = MdDarkBackground,
    onBackground = MdDarkOnBackground,
    surface = MdDarkSurface,
    onSurface = MdDarkOnSurface,
    surfaceVariant = MdDarkSurfaceVariant,
    onSurfaceVariant = MdDarkOnSurfaceVariant,
    surfaceTint = MdDarkPrimary,
    inverseSurface = MdLightSurface,
    inverseOnSurface = MdLightOnSurface,
    outline = MdDarkOutline,
    outlineVariant = MdDarkOutlineVariant,
    error = MdDarkError,
    onError = MdDarkOnError,
    errorContainer = MdDarkErrorContainer,
    onErrorContainer = MdDarkOnErrorContainer,
    surfaceContainerLowest = MdDarkSurfaceContainerLowest,
    surfaceContainerLow = MdDarkSurfaceContainerLow,
    surfaceContainer = MdDarkSurfaceContainer,
    surfaceContainerHigh = MdDarkSurfaceContainerHigh,
    surfaceContainerHighest = MdDarkSurfaceContainerHighest
)

private val QnLightScheme = lightColorScheme(
    primary = MdLightPrimary,
    onPrimary = MdLightOnPrimary,
    primaryContainer = MdLightPrimaryContainer,
    onPrimaryContainer = MdLightOnPrimaryContainer,
    inversePrimary = MdDarkPrimary,
    secondary = MdLightSecondary,
    onSecondary = MdLightOnSecondary,
    secondaryContainer = MdLightSecondaryContainer,
    onSecondaryContainer = MdLightOnSecondaryContainer,
    tertiary = MdLightTertiary,
    onTertiary = MdLightOnTertiary,
    tertiaryContainer = MdLightTertiaryContainer,
    onTertiaryContainer = MdLightOnTertiaryContainer,
    background = MdLightBackground,
    onBackground = MdLightOnBackground,
    surface = MdLightSurface,
    onSurface = MdLightOnSurface,
    surfaceVariant = MdLightSurfaceVariant,
    onSurfaceVariant = MdLightOnSurfaceVariant,
    surfaceTint = MdLightPrimary,
    inverseSurface = MdDarkSurface,
    inverseOnSurface = MdDarkOnSurface,
    outline = MdLightOutline,
    outlineVariant = MdLightOutlineVariant,
    error = MdLightError,
    onError = MdLightOnError,
    errorContainer = MdLightErrorContainer,
    onErrorContainer = MdLightOnErrorContainer,
    surfaceContainerLowest = MdLightSurfaceContainerLowest,
    surfaceContainerLow = MdLightSurfaceContainerLow,
    surfaceContainer = MdLightSurfaceContainer,
    surfaceContainerHigh = MdLightSurfaceContainerHigh,
    surfaceContainerHighest = MdLightSurfaceContainerHighest
)

// ── ChinanTheme（主入口） ──
@Composable
fun ChinanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> QnDarkScheme
        else -> QnLightScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // edge-to-edge 下系统栏由系统绘制，只声明图标明暗；不再写死 statusBarColor（API 35+ 已失效）
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = QnTypography,
        shapes = QnShapes,
        content = content
    )
}
