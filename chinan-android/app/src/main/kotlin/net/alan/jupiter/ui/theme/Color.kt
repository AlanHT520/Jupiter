package net.alan.jupiter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── MD3 亮色主题 token（seed #3DDC97） ──
val MdLightPrimary = Color(0xFF006C4C)
val MdLightOnPrimary = Color(0xFFFFFFFF)
val MdLightPrimaryContainer = Color(0xFF89F8C7)
val MdLightOnPrimaryContainer = Color(0xFF002114)
val MdLightSecondary = Color(0xFF4B6358)
val MdLightOnSecondary = Color(0xFFFFFFFF)
val MdLightSecondaryContainer = Color(0xFFCDE9DC)
val MdLightOnSecondaryContainer = Color(0xFF082017)
val MdLightTertiary = Color(0xFF3F6375)
val MdLightOnTertiary = Color(0xFFFFFFFF)
val MdLightTertiaryContainer = Color(0xFFC2E8FC)
val MdLightOnTertiaryContainer = Color(0xFF001E2B)
val MdLightBackground = Color(0xFFF5FBF7)
val MdLightOnBackground = Color(0xFF171D1A)
val MdLightSurface = Color(0xFFF5FBF7)
val MdLightOnSurface = Color(0xFF171D1A)
val MdLightSurfaceVariant = Color(0xFFDBE5DE)
val MdLightOnSurfaceVariant = Color(0xFF3F4944)
val MdLightOutline = Color(0xFF6F7973)
val MdLightOutlineVariant = Color(0xFFBFC9C2)
val MdLightError = Color(0xFFBA1A1A)
val MdLightOnError = Color(0xFFFFFFFF)
val MdLightErrorContainer = Color(0xFFFFDAD6)
val MdLightOnErrorContainer = Color(0xFF410002)

// ── MD3 暗色主题 token（seed #3DDC97） ──
val MdDarkPrimary = Color(0xFF3DDC97)
val MdDarkOnPrimary = Color(0xFF003823)
val MdDarkPrimaryContainer = Color(0xFF005236)
val MdDarkOnPrimaryContainer = Color(0xFF6FFBBE)
val MdDarkSecondary = Color(0xFFB0CCC0)
val MdDarkOnSecondary = Color(0xFF1B352C)
val MdDarkSecondaryContainer = Color(0xFF324B41)
val MdDarkOnSecondaryContainer = Color(0xFFCCE9DC)
val MdDarkTertiary = Color(0xFFA8CBE0)
val MdDarkOnTertiary = Color(0xFF0B3446)
val MdDarkTertiaryContainer = Color(0xFF264B5D)
val MdDarkOnTertiaryContainer = Color(0xFFC4E7FB)
val MdDarkBackground = Color(0xFF0B0E11)
val MdDarkOnBackground = Color(0xFFE6EDF0)
val MdDarkSurface = Color(0xFF0F1417)
val MdDarkOnSurface = Color(0xFFE6EDF0)
val MdDarkSurfaceVariant = Color(0xFF3F4944)
val MdDarkOnSurfaceVariant = Color(0xFFBEC9C2)
val MdDarkOutline = Color(0xFF89938D)
val MdDarkOutlineVariant = Color(0xFF3F4944)
val MdDarkError = Color(0xFFFFB4AB)
val MdDarkOnError = Color(0xFF690005)
val MdDarkErrorContainer = Color(0xFF93000A)
val MdDarkOnErrorContainer = Color(0xFFFFDAD6)

// ── Surface container 色调层级：显式声明，不依赖版本的默认推导 ──
val MdLightSurfaceContainerLowest = Color(0xFFF7FBF8)
val MdLightSurfaceContainerLow = Color(0xFFF1F7F3)
val MdLightSurfaceContainer = Color(0xFFEDF3EF)
val MdLightSurfaceContainerHigh = Color(0xFFE7EEE9)
val MdLightSurfaceContainerHighest = Color(0xFFE0E7E2)
val MdDarkSurfaceContainerLowest = Color(0xFF0A0D0C)
val MdDarkSurfaceContainerLow = Color(0xFF151B19)
val MdDarkSurfaceContainer = Color(0xFF1B2220)
val MdDarkSurfaceContainerHigh = Color(0xFF232B28)
val MdDarkSurfaceContainerHighest = Color(0xFF2B3330)

/**
 * 界面语义色调。
 *
 * MD3 没有 warning 角色，约定：成功 = `primaryContainer`、警示/请假 = `tertiaryContainer`、
 * 错误/异常 = `errorContainer`、中性 = `surfaceContainerHighest`。
 * 屏幕里不再出现任何固定色值，浅深主题与动态取色都能自动跟随。
 */
enum class QnTone { Success, Warning, Danger, Neutral }

@Composable
fun QnTone.container(): Color = when (this) {
    QnTone.Success -> MaterialTheme.colorScheme.primaryContainer
    QnTone.Warning -> MaterialTheme.colorScheme.tertiaryContainer
    QnTone.Danger -> MaterialTheme.colorScheme.errorContainer
    QnTone.Neutral -> MaterialTheme.colorScheme.surfaceContainerHighest
}

@Composable
fun QnTone.content(): Color = when (this) {
    QnTone.Success -> MaterialTheme.colorScheme.onPrimaryContainer
    QnTone.Warning -> MaterialTheme.colorScheme.onTertiaryContainer
    QnTone.Danger -> MaterialTheme.colorScheme.onErrorContainer
    QnTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** 强调色（文字 / 图标）：成功用 primary，警示用 tertiary，错误用 error。 */
@Composable
fun QnTone.accent(): Color = when (this) {
    QnTone.Success -> MaterialTheme.colorScheme.primary
    QnTone.Warning -> MaterialTheme.colorScheme.tertiary
    QnTone.Danger -> MaterialTheme.colorScheme.error
    QnTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * 查寝状态码 → 语义色调：在寝 = 成功、旷宿 = 错误、其余请假/离校类 = 警示。
 * 状态色不再硬编码为暗色板，全部走色角色。
 */
fun statusTone(statusCode: Int?): QnTone = when (statusCode) {
    1 -> QnTone.Success
    4 -> QnTone.Danger
    2, 3, 5, 6, 7, 8, 9 -> QnTone.Warning
    else -> QnTone.Neutral
}
