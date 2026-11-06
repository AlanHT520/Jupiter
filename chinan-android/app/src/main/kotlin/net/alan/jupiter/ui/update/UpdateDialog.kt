package net.alan.jupiter.ui.update

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.alan.jupiter.ui.components.QnGhostButton
import net.alan.jupiter.ui.components.QnMono
import net.alan.jupiter.ui.components.QnPrimaryButton
import net.alan.jupiter.ui.components.QnRadius
import net.alan.jupiter.ui.theme.QnAccent
import net.alan.jupiter.ui.theme.QnLine
import net.alan.jupiter.ui.theme.QnPanel
import net.alan.jupiter.ui.theme.QnText
import net.alan.jupiter.ui.theme.QnTextDim
import net.alan.jupiter.ui.theme.QnTextMuted

/**
 * 更新对话框：暗色细线（2dp 圆角、无阴影），按钮复用 QnPrimaryButton / QnGhostButton。
 */
@Composable
fun UpdateDialog(
    version: String,
    changelog: String,
    downloading: Boolean,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        containerColor = QnPanel,
        shape = QnRadius,
        tonalElevation = 0.dp,
        icon = {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .border(1.dp, QnLine, QnRadius),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdateAlt,
                    contentDescription = null,
                    tint = QnAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        title = {
            Text(
                text = "发现新版本",
                color = QnText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(color = QnLine, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "VERSION",
                        color = QnTextDim,
                        fontFamily = QnMono,
                        fontSize = 9.5.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = version.ifBlank { "--" },
                        color = QnAccent,
                        fontFamily = QnMono,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "CHANGELOG",
                    color = QnTextDim,
                    fontFamily = QnMono,
                    fontSize = 9.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .border(1.dp, QnLine, QnRadius)
                ) {
                    Text(
                        text = changelog.take(300).ifBlank { "无更新说明" },
                        color = QnTextMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        },
        confirmButton = {
            QnPrimaryButton(
                text = if (downloading) "下载中..." else "立即更新",
                onClick = onDownload,
                enabled = !downloading
            )
        },
        dismissButton = {
            QnGhostButton(
                text = "稍后提醒",
                onClick = onDismiss,
                enabled = !downloading
            )
        }
    )
}
