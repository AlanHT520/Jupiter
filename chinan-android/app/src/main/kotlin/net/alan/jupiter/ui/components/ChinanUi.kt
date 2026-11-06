package net.alan.jupiter.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.alan.jupiter.ui.theme.QnMono
import net.alan.jupiter.ui.theme.QnTone
import net.alan.jupiter.ui.theme.accent
import net.alan.jupiter.ui.theme.container
import net.alan.jupiter.ui.theme.content

/**
 * 寝安的 MD3 组件层。
 *
 * 约定：
 * - 形状一律取 [MaterialTheme.shapes]（卡片 large 16dp / Chip small 8dp / 输入框 medium 12dp / 对话框 extraLarge 28dp）；
 * - 颜色一律取色角色，屏幕里不出现固定色值；
 * - 状态文案是中文业务语言；等宽字体只用于数字、日期、ID、房间号；
 * - 一屏只有一个主操作（[QnPrimaryButton]）。
 */

// =====================================================================
// 外壳与顶栏
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QnScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            if (bottomBar != null) {
                Column(modifier = Modifier.navigationBarsPadding()) { bottomBar() }
            }
        },
        snackbarHost = {
            if (snackbarHostState != null) {
                SnackbarHost(hostState = snackbarHostState)
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        content = content
    )
}

@Composable
fun QnTopIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
    }
}

// =====================================================================
// 卡片
// =====================================================================

@Composable
fun QnCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val base = modifier.fillMaxWidth()
    val clickable = if (onClick != null) base.clickable(onClick = onClick) else base
    Card(
        modifier = clickable,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/** 主视觉卡：primaryContainer + 28dp 大圆角，一屏最多一张。 */
@Composable
fun QnHeroCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    QnCard(
        modifier = modifier,
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        content = content
    )
}

/** 提示卡：成功 / 警示 / 错误 / 中性，全部走色角色。 */
@Composable
fun QnNoticeCard(
    text: String,
    tone: QnTone = QnTone.Neutral,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    QnCard(
        modifier = modifier,
        containerColor = tone.container(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tone.content(),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = tone.content()
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = tone.content()
                )
            }
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.width(10.dp))
                FilledTonalButton(
                    onClick = onAction,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = tone.content(),
                        contentColor = tone.container()
                    )
                ) {
                    Text(actionLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

// =====================================================================
// 标签 / 状态
// =====================================================================

@Composable
fun QnChip(
    text: String,
    tone: QnTone = QnTone.Neutral,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null
) {
    val label: @Composable () -> Unit = {
        Text(text = text, style = MaterialTheme.typography.labelMedium)
    }
    val leading: (@Composable () -> Unit)? = leadingIcon?.let { vector ->
        {
            Icon(
                imageVector = vector,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    }

    if (onClick != null) {
        AssistChip(
            onClick = onClick,
            label = label,
            modifier = modifier,
            leadingIcon = leading,
            shape = MaterialTheme.shapes.small,
            colors = AssistChipDefaults.assistChipColors(
                containerColor = tone.container(),
                labelColor = tone.content(),
                leadingIconContentColor = tone.content()
            ),
            border = null
        )
    } else {
        AssistChip(
            onClick = {},
            enabled = false,
            label = label,
            modifier = modifier,
            leadingIcon = leading,
            shape = MaterialTheme.shapes.small,
            colors = AssistChipDefaults.assistChipColors(
                disabledContainerColor = tone.container(),
                disabledLabelColor = tone.content(),
                disabledLeadingIconContentColor = tone.content()
            ),
            border = null
        )
    }
}

@Composable
fun QnFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, style = MaterialTheme.typography.labelLarge) },
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = null
    )
}

// =====================================================================
// 统计 / 键值 / 分组
// =====================================================================

/** 大数字 + 中文标签（数字等宽，标签 sans）。 */
@Composable
fun QnStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    hint: String? = null
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            color = valueColor,
            fontFamily = QnMono,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1
        )
        if (hint != null) {
            Text(
                text = hint,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

/** 等分的一行统计块。 */
@Composable
fun QnStatRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

/** 标准键值行（列表项风格，不用等宽标签）。 */
@Composable
fun QnKeyValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    mono: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(84.dp)
        )
        Text(
            text = value,
            color = valueColor,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (mono) QnMono else null,
            maxLines = 2,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 中文分组标题：13sp / 600 / primary，替代大写等宽小标签。 */
@Composable
fun QnGroupLabel(text: String, modifier: Modifier = Modifier) {
    if (text.isBlank()) return
    Text(
        text = text,
        modifier = modifier.padding(top = 8.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.primary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun QnDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

// =====================================================================
// 进度
// =====================================================================

/** 唯一的进度表达方式：M3 线性进度条（不再有 ████░░）。 */
@Composable
fun QnProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp),
        color = color,
        trackColor = trackColor
    )
}

// =====================================================================
// 按钮
// =====================================================================

@Composable
fun QnPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun QnTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        enabled = enabled
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun QnOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        enabled = enabled
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun QnTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary
) {
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/** 危险操作按钮：errorContainer 底，与「取消」明显区分。 */
@Composable
fun QnDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** 对话框里的危险确认：纯文字 + error 色。 */
@Composable
fun QnDangerTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error
        )
    }
}

// =====================================================================
// 头像 / 空态 / 错误态
// =====================================================================

@Composable
fun QnAvatar(
    initial: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Box(
        modifier = modifier
            .size(size)
            .background(containerColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial.take(1).ifBlank { "?" },
            color = contentColor,
            fontSize = (size.value * 0.4f).sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun QnEmpty(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (hint != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(16.dp))
            QnTonalButton(text = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun QnError(
    title: String,
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Refresh
) {
    QnCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentPadding = PaddingValues(18.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(26.dp),
            tint = MaterialTheme.colorScheme.onErrorContainer
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
        Spacer(modifier = Modifier.height(14.dp))
        QnTonalButton(
            text = "重试",
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// =====================================================================
// 输入
// =====================================================================

@Composable
fun QnTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    supportingText: String? = null,
    maxLines: Int = if (singleLine) 1 else 4,
    shape: Shape = MaterialTheme.shapes.small
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        readOnly = readOnly,
        isError = isError,
        label = label?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
        placeholder = placeholder?.let {
            { Text(it, style = MaterialTheme.typography.bodyMedium) }
        },
        leadingIcon = leadingIcon?.let { vector ->
            {
                Icon(
                    imageVector = vector,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        trailingIcon = trailingIcon,
        supportingText = supportingText?.let {
            { Text(it, style = MaterialTheme.typography.bodySmall) }
        },
        singleLine = singleLine,
        maxLines = maxLines,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = shape,
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

// =====================================================================
// 文案工具
// =====================================================================

/** 角色文案：全仓统一一处，避免设置页写「工作人员」而我的页写英文。 */
fun roleLabel(role: String?): String = when (role?.uppercase()) {
    "ADMIN" -> "管理员"
    else -> "工作人员"
}

/** 手机号脱敏：138****1111（资料展示用）。 */
fun maskPhone(phone: String?): String {
    val value = phone.orEmpty().trim()
    if (value.length != 11) return value.ifBlank { "-" }
    return value.substring(0, 3) + "****" + value.substring(7)
}

// =====================================================================
// 列表行
// =====================================================================

/**
 * 标准列表行：左侧头像位 / 标题 / 副标题 / 右侧尾随内容。
 * 房间号、学号等用 [mono] 走等宽。
 */
@Composable
fun QnListItem(
    title: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    monoTitle: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val base = modifier
        .fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 16.dp, vertical = 12.dp)
    Row(modifier = base, verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = if (monoTitle) QnMono else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (meta != null) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(10.dp))
            trailing()
        }
    }
}
