package net.alan.jupiter.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.alan.jupiter.ui.theme.QnAccent
import net.alan.jupiter.ui.theme.QnBg
import net.alan.jupiter.ui.theme.QnDanger
import net.alan.jupiter.ui.theme.QnLine
import net.alan.jupiter.ui.theme.QnPanel
import net.alan.jupiter.ui.theme.QnPanelAlt
import net.alan.jupiter.ui.theme.QnText
import net.alan.jupiter.ui.theme.QnTextDim
import net.alan.jupiter.ui.theme.QnTextMuted
import net.alan.jupiter.ui.theme.QnWarn
import kotlin.math.roundToInt

val QnRadius = RoundedCornerShape(2.dp)
val QnMono = FontFamily.Monospace

/** 机器状态：颜色只允许出现在状态灯与状态文字上。 */
enum class QnStatus(val label: String, val color: Color) {
    OK("● 正常", QnAccent),
    SYNC("● 待同步", QnWarn),
    QUEUED("● 待提交", QnWarn),
    RUNNING("● 运行中", QnAccent),
    OFFLINE("● 离线", QnDanger),
    CACHED("● 缓存", QnTextMuted),
    IDLE("● 空闲", QnTextDim),
    FAILED("● 失败", QnDanger);

    fun text(detail: String? = null): String = if (detail.isNullOrBlank()) label else "$label $detail"
}

/**
 * 寝安外壳：顶部路径（QIN'AN /path + 状态灯），底部状态栏。
 * 所有页面都应使用它，保证 14 屏视觉一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QnScaffold(
    path: String,
    status: QnStatus = QnStatus.OK,
    statusDetail: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            Surface(color = QnPanelAlt) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp)
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (onBack != null) {
                            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "返回",
                                    tint = QnText
                                )
                            }
                        }
                        Text(
                            "寝安",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = QnText
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        actions()
                        QnStatusTag(status = status, detail = statusDetail)
                    }
                    HorizontalDivider(color = QnLine, thickness = 1.dp)
                }
            }
        },
        bottomBar = {
            QnBottomBar(status = status, detail = statusDetail)
            bottomBar()
        },
        containerColor = QnBg,
        content = content
    )
}

/** 顶部状态灯（机器标签，不是胶囊）。 */
@Composable
fun QnStatusTag(
    status: QnStatus,
    modifier: Modifier = Modifier,
    detail: String? = null
) {
    Text(
        text = status.text(detail),
        modifier = modifier,
        color = status.color,
        fontFamily = QnMono,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1
    )
}

/** 底部状态栏：版本 / 端口 / 时间。 */
@Composable
fun QnBottomBar(
    status: QnStatus,
    detail: String? = null,
    right: String = ""
) {
    Surface(color = QnPanelAlt) {
        Column {
            HorizontalDivider(color = QnLine, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = status.text(detail),
                    color = status.color,
                    fontFamily = QnMono,
                    fontSize = 9.5.sp
                )
                if (right.isNotBlank()) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = right,
                        color = QnTextDim,
                        fontFamily = QnMono,
                        fontSize = 9.5.sp
                    )
                }
            }
        }
    }
}

/** 极客卡片：1dp 细线 + 2dp 圆角 + 零阴影。 */
@Composable
fun QnCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val base = modifier
        .fillMaxWidth()
        .background(QnPanel, QnRadius)
        .border(1.dp, QnLine, QnRadius)
    val clickable = if (onClick != null) base.clickable(onClick = onClick) else base
    Column(modifier = clickable.padding(contentPadding), content = content)
}

/** 兼容旧调用：ChinanCard 现在等价于 QnCard。 */
@Composable
fun ChinanCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) = QnCard(modifier = modifier, onClick = onClick, content = content)

/** 状态标签：小圆点 + 等宽文字，无底色。 */
@Composable
fun QnTag(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, RoundedCornerShape(50))
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = text,
            color = color,
            fontFamily = QnMono,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** 兼容旧调用：StatusChip → QnTag。 */
@Composable
fun StatusChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = color)
            Spacer(modifier = Modifier.width(4.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color, RoundedCornerShape(50))
            )
            Spacer(modifier = Modifier.width(5.dp))
        }
        Text(text, color = color, fontFamily = QnMono, fontSize = 10.5.sp)
    }
}

/** 统计块：标签在上（等宽小字），值在下（等宽大号）。 */
@Composable
fun QnStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = QnText,
    hint: String? = null
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = QnTextDim,
            fontFamily = QnMono,
            fontSize = 9.5.sp,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            color = valueColor,
            fontFamily = QnMono,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        if (hint != null) {
            Text(
                text = hint,
                color = QnTextDim,
                fontFamily = QnMono,
                fontSize = 9.sp
            )
        }
    }
}

/** 键值行：左标签等宽暗色，右值等宽主色。 */
@Composable
fun QnKeyValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = QnText
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = QnTextDim, fontFamily = QnMono, fontSize = 10.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            value,
            color = valueColor,
            fontFamily = QnMono,
            fontSize = 11.sp,
            maxLines = 2,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 分区标题：去掉注释风格的 //，常见英文自动转中文。 */
@Composable
fun QnSectionLabel(text: String, modifier: Modifier = Modifier) {
    val raw = text.removePrefix("//").trim()
    val label = when (raw) {
        "TASKS" -> "任务"
        "PROFILE" -> "账号信息"
        "ACCOUNT" -> "账户"
        "LICENSE" -> "版权信息"
        "UPDATE" -> "版本更新"
        "LOCAL DATA" -> "本地数据"
        "SERVER" -> "服务器"
        "PASSWORD" -> "密码"
        "IDENTITY" -> "身份信息"
        "COMMANDS" -> "操作"
        "PENDING" -> "待审核"
        "HISTORY" -> "历史记录"
        "LIVE" -> "实时数据"
        "EVENT STREAM" -> "事件流"
        "RECENT TASKS" -> "近期任务"
        else -> raw
    }
    if (label.isBlank()) return
    Text(
        text = label,
        modifier = modifier,
        color = QnTextDim,
        fontFamily = QnMono,
        fontSize = 9.5.sp
    )
}

/** ASCII 进度条：████░░░░ 42%。 */
@Composable
fun QnAsciiProgress(progress: Float, modifier: Modifier = Modifier, blocks: Int = 10) {
    val ratio = progress.coerceIn(0f, 1f)
    val filled = (ratio * blocks).roundToInt()
    val bar = "█".repeat(filled) + "░".repeat(blocks - filled)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(bar, color = QnAccent, fontFamily = QnMono, fontSize = 11.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "${(ratio * 100).roundToInt()}%",
            color = QnTextMuted,
            fontFamily = QnMono,
            fontSize = 10.sp
        )
    }
}

/** 主按钮：实心强调色，黑字。 */
@Composable
fun QnPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 40.dp),
        enabled = enabled,
        shape = QnRadius,
        colors = ButtonDefaults.buttonColors(
            containerColor = QnAccent,
            contentColor = QnBg,
            disabledContainerColor = QnLine,
            disabledContentColor = QnTextDim
        )
    ) {
        Text(text, fontFamily = QnMono, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** 次按钮：透明底 + 细线。 */
@Composable
fun QnGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 40.dp),
        enabled = enabled,
        shape = QnRadius,
        border = BorderStroke(1.dp, QnLineStrongColor()),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = QnText)
    ) {
        Text(text, fontFamily = QnMono, fontSize = 12.sp)
    }
}

private fun QnLineStrongColor(): Color = Color(0xFF2E3A44)

/** 空状态：虚线感（细线框）+ 机器提示。 */
@Composable
fun QnEmpty(
    icon: ImageVector,
    title: String,
    hint: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .border(1.dp, QnLine, QnRadius)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp), tint = QnAccent)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            title,
            color = QnText,
            fontFamily = QnMono,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        if (hint != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                hint,
                color = QnTextDim,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(14.dp))
            QnGhostButton(text = actionLabel, onClick = onAction)
        }
    }
}

/** 兼容旧调用。 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) = QnEmpty(
    icon = icon,
    title = title,
    hint = hint,
    actionLabel = actionLabel,
    onAction = onAction,
    modifier = modifier
)

/** 错误状态：红色状态灯 + 详情 + 重试。 */
@Composable
fun QnError(
    title: String,
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Refresh
) {
    Column(
        modifier = modifier
            .border(1.dp, QnDanger.copy(alpha = 0.55f), QnRadius)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = QnDanger)
        Spacer(modifier = Modifier.height(10.dp))
        Text(title, color = QnDanger, fontFamily = QnMono, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            message,
            color = QnTextMuted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(14.dp))
        QnGhostButton(text = "重试", onClick = onRetry)
    }
}

/** 兼容旧调用。 */
@Composable
fun ErrorState(
    title: String,
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector
) = QnError(title = title, message = message, onRetry = onRetry, modifier = modifier, icon = icon)
