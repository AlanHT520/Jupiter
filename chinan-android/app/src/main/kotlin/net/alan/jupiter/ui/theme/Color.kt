package net.alan.jupiter.ui.theme

import androidx.compose.ui.graphics.Color

// ========== Light Theme Palette（与桌面端同一套品牌蓝） ==========
val LightPrimary = Color(0xFF3D7EFF)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFDCE9FF)
val LightOnPrimaryContainer = Color(0xFF0A2A5E)
val LightSecondary = Color(0xFF54627A)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFDCE6F8)
val LightOnSecondaryContainer = Color(0xFF101C2E)
val LightTertiary = Color(0xFF6B5778)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFF2DAFF)
val LightOnTertiaryContainer = Color(0xFF251431)
val LightBackground = Color(0xFFF5F7FB)
val LightOnBackground = Color(0xFF121826)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF121826)
val LightSurfaceVariant = Color(0xFFE8EDF5)
val LightOnSurfaceVariant = Color(0xFF5A6577)
val LightError = Color(0xFFD93A3A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)
val LightOutline = Color(0xFF7C889C)
val LightOutlineVariant = Color(0xFFDCE3EE)
val LightInverseSurface = Color(0xFF1F2937)
val LightInverseOnSurface = Color(0xFFF1F4FA)
val LightInversePrimary = Color(0xFF9EC2FF)
val LightScrim = Color(0xFF000000)

// ========== Login Screen Background ==========
val LoginBackground = Color(0xFF15233D)
val LoginBackgroundDark = Color(0xFF0C1526)

// ========== Dark Theme Palette（与桌面端深色一致） ==========
val DarkPrimary = Color(0xFF7AA9FF)
val DarkOnPrimary = Color(0xFF06224F)
val DarkPrimaryContainer = Color(0xFF1D3A6E)
val DarkOnPrimaryContainer = Color(0xFFDCE9FF)
val DarkSecondary = Color(0xFFB9C4D4)
val DarkOnSecondary = Color(0xFF23303F)
val DarkSecondaryContainer = Color(0xFF2A3A50)
val DarkOnSecondaryContainer = Color(0xFFDCE6F8)
val DarkTertiary = Color(0xFFD7BDE4)
val DarkOnTertiary = Color(0xFF3B2948)
val DarkTertiaryContainer = Color(0xFF523F5F)
val DarkOnTertiaryContainer = Color(0xFFF2DAFF)
val DarkBackground = Color(0xFF0F1218)
val DarkOnBackground = Color(0xFFE8EDF5)
val DarkSurface = Color(0xFF161B24)
val DarkOnSurface = Color(0xFFE8EDF5)
val DarkSurfaceVariant = Color(0xFF1F2937)
val DarkOnSurfaceVariant = Color(0xFFA6B0C0)
val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF7A1F1F)
val DarkOnErrorContainer = Color(0xFFFFDAD6)
val DarkOutline = Color(0xFF8D9199)
val DarkOutlineVariant = Color(0xFF273141)
val DarkInverseSurface = Color(0xFFE8EDF5)
val DarkInverseOnSurface = Color(0xFF1F2937)
val DarkInversePrimary = Color(0xFF3D7EFF)
val DarkScrim = Color(0xFF000000)

// ========== Status Colors ==========
val StatusPresent = Color(0xFF3DD68C)
val StatusPersonalLeave = Color(0xFFF2C55C)
val StatusSickLeave = Color(0xFFF2C55C)
val StatusAbsent = Color(0xFFF06B6B)
val StatusMilitary = Color(0xFFB08968)
val StatusIntern = Color(0xFF6BA3FF)
val StatusDayStudent = Color(0xFFB98AF0)
val StatusLateReturn = Color(0xFF4ECDC4)
val StatusLongSickLeave = Color(0xFFFF8A65)
val StatusUnknown = Color(0xFF93A0B5)
val StatusFemale = Color(0xFFF06292)
val StatusChecked = Color(0xFF3DD68C)
val StatusUnchecked = Color(0xFF5A6577)

fun statusColor(statusCode: Int?): Color = when (statusCode) {
    0 -> StatusUnknown
    1 -> StatusPresent
    2 -> StatusPersonalLeave
    3 -> StatusSickLeave
    4 -> StatusAbsent
    5 -> StatusMilitary
    6 -> StatusIntern
    7 -> StatusDayStudent
    8 -> StatusLateReturn
    9 -> StatusLongSickLeave
    else -> StatusUnknown
}
