package net.alan.jupiter.ui.theme

import androidx.compose.ui.graphics.Color

// ========== Light Theme Palette ==========
val LightPrimary = Color(0xFF1565C0)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFD1E4FF)
val LightOnPrimaryContainer = Color(0xFF001D36)
val LightSecondary = Color(0xFF535F70)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFD7E3F7)
val LightOnSecondaryContainer = Color(0xFF101C2B)
val LightTertiary = Color(0xFF6B5778)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFF2DAFF)
val LightOnTertiaryContainer = Color(0xFF251431)
val LightBackground = Color(0xFFFDFCFF)
val LightOnBackground = Color(0xFF1A1C1E)
val LightSurface = Color(0xFFFDFCFF)
val LightOnSurface = Color(0xFF1A1C1E)
val LightSurfaceVariant = Color(0xFFDFE2EB)
val LightOnSurfaceVariant = Color(0xFF43474E)
val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)
val LightOutline = Color(0xFF73777F)
val LightOutlineVariant = Color(0xFFC3C7CF)
val LightInverseSurface = Color(0xFF2F3033)
val LightInverseOnSurface = Color(0xFFF1F0F4)
val LightInversePrimary = Color(0xFF9ECAFF)
val LightScrim = Color(0xFF000000)

// ========== Login Screen Background ==========
val LoginBackground = Color(0xFF1C2D4F)
val LoginBackgroundDark = Color(0xFF0F1A2E)

// ========== Dark Theme Palette ==========
val DarkPrimary = Color(0xFF9ECAFF)
val DarkOnPrimary = Color(0xFF003258)
val DarkPrimaryContainer = Color(0xFF00497D)
val DarkOnPrimaryContainer = Color(0xFFD1E4FF)
val DarkSecondary = Color(0xFFBBC7DB)
val DarkOnSecondary = Color(0xFF253140)
val DarkSecondaryContainer = Color(0xFF3B4858)
val DarkOnSecondaryContainer = Color(0xFFD7E3F7)
val DarkTertiary = Color(0xFFD7BDE4)
val DarkOnTertiary = Color(0xFF3B2948)
val DarkTertiaryContainer = Color(0xFF523F5F)
val DarkOnTertiaryContainer = Color(0xFFF2DAFF)
val DarkBackground = Color(0xFF1A1C1E)
val DarkOnBackground = Color(0xFFE3E2E6)
val DarkSurface = Color(0xFF1A1C1E)
val DarkOnSurface = Color(0xFFE3E2E6)
val DarkSurfaceVariant = Color(0xFF43474E)
val DarkOnSurfaceVariant = Color(0xFFC3C7CF)
val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)
val DarkOutline = Color(0xFF8D9199)
val DarkOutlineVariant = Color(0xFF43474E)
val DarkInverseSurface = Color(0xFFE3E2E6)
val DarkInverseOnSurface = Color(0xFF2F3033)
val DarkInversePrimary = Color(0xFF1565C0)
val DarkScrim = Color(0xFF000000)

// ========== Status Colors ==========
val StatusPresent = Color(0xFF4CAF50)
val StatusPersonalLeave = Color(0xFFFF9800)
val StatusSickLeave = Color(0xFFFF9800)
val StatusAbsent = Color(0xFFF44336)
val StatusMilitary = Color(0xFF795548)
val StatusIntern = Color(0xFF2196F3)
val StatusFemale = Color(0xFFE91E63)
val StatusChecked = Color(0xFF4CAF50)
val StatusUnchecked = Color(0xFFBDBDBD)

fun statusColor(statusCode: Int): Color = when (statusCode) {
    1 -> StatusPresent
    2 -> StatusPersonalLeave
    3 -> StatusSickLeave
    4 -> StatusAbsent
    5 -> StatusMilitary
    6 -> StatusIntern
    else -> StatusPresent
}