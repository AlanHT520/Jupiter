package net.alan.jupiter.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// 寝安 QIN'AN — Geek palette（暗色唯一主题）
// 底 #0B0E11 / 面板 #11161B / 细线 #222B32 / 文字 #E6EDF0
// 唯一强调色 #3DDC97 / 危险 #FF5C5C / 警告 #FFC857
// ============================================================

val QnBg = Color(0xFF0B0E11)
val QnPanel = Color(0xFF11161B)
val QnPanelAlt = Color(0xFF0E1418)
val QnLine = Color(0xFF222B32)
val QnLineStrong = Color(0xFF2E3A44)
val QnText = Color(0xFFE6EDF0)
val QnTextMuted = Color(0xFF8A97A0)
val QnTextDim = Color(0xFF5A666E)
val QnAccent = Color(0xFF3DDC97)
val QnAccentDeep = Color(0xFF2FB981)
val QnOnAccent = Color(0xFF06110C)
val QnAccentWash = Color(0xFF12241C)
val QnDanger = Color(0xFFFF5C5C)
val QnDangerWash = Color(0xFF2A1414)
val QnWarn = Color(0xFFFFC857)

// 兼容旧引用：模块里仍按 Light* 命名引用的地方统一映射到暗色令牌
val LightPrimary = QnAccent
val LightOnPrimary = QnOnAccent
val LightPrimaryContainer = QnAccentWash
val LightOnPrimaryContainer = QnAccent
val LightSecondary = QnTextMuted
val LightOnSecondary = QnBg
val LightSecondaryContainer = QnPanelAlt
val LightOnSecondaryContainer = QnText
val LightTertiary = QnAccent
val LightOnTertiary = QnOnAccent
val LightTertiaryContainer = QnAccentWash
val LightOnTertiaryContainer = QnText
val LightBackground = QnBg
val LightOnBackground = QnText
val LightSurface = QnPanel
val LightOnSurface = QnText
val LightSurfaceVariant = QnPanelAlt
val LightOnSurfaceVariant = QnTextMuted
val LightError = QnDanger
val LightOnError = QnBg
val LightErrorContainer = QnDangerWash
val LightOnErrorContainer = QnDanger
val LightOutline = QnLineStrong
val LightOutlineVariant = QnLine
val LightInverseSurface = QnText
val LightInverseOnSurface = QnBg
val LightInversePrimary = QnAccentDeep
val LightScrim = Color(0xFF000000)

// 暗色令牌
val DarkPrimary = QnAccent
val DarkOnPrimary = QnOnAccent
val DarkPrimaryContainer = QnAccentWash
val DarkOnPrimaryContainer = QnAccent
val DarkSecondary = QnTextMuted
val DarkOnSecondary = QnBg
val DarkSecondaryContainer = QnPanelAlt
val DarkOnSecondaryContainer = QnText
val DarkTertiary = QnAccent
val DarkOnTertiary = QnOnAccent
val DarkTertiaryContainer = QnAccentWash
val DarkOnTertiaryContainer = QnText
val DarkBackground = QnBg
val DarkOnBackground = QnText
val DarkSurface = QnPanel
val DarkOnSurface = QnText
val DarkSurfaceVariant = QnPanelAlt
val DarkOnSurfaceVariant = QnTextMuted
val DarkError = QnDanger
val DarkOnError = QnBg
val DarkErrorContainer = QnDangerWash
val DarkOnErrorContainer = QnDanger
val DarkOutline = QnLineStrong
val DarkOutlineVariant = QnLine
val DarkInverseSurface = QnText
val DarkInverseOnSurface = QnBg
val DarkInversePrimary = QnAccentDeep
val DarkScrim = Color(0xFF000000)

// 登录页（暗色）
val LoginBackground = QnBg
val LoginBackgroundDark = Color(0xFF070A0C)

// ========== 状态色（只用于状态灯与状态文字） ==========
val StatusPresent = QnAccent
val StatusPersonalLeave = QnWarn
val StatusSickLeave = QnWarn
val StatusAbsent = QnDanger
val StatusMilitary = Color(0xFFB08968)
val StatusIntern = Color(0xFF58A6FF)
val StatusDayStudent = Color(0xFFB98AF0)
val StatusLateReturn = Color(0xFF4ECDC4)
val StatusLongSickLeave = Color(0xFFFF8A65)
val StatusUnknown = QnTextDim
val StatusFemale = Color(0xFFF06292)
val StatusChecked = QnAccent
val StatusUnchecked = QnTextDim

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
