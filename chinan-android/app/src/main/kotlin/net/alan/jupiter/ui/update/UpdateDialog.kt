package net.alan.jupiter.ui.update

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.alan.jupiter.ui.components.QnChip
import net.alan.jupiter.ui.components.QnPrimaryButton
import net.alan.jupiter.ui.theme.QnTone

/**
 * 更新对话框（全局）。
 *
 * M3 AlertDialog 默认 28dp 圆角；版本号用 AssistChip；更新说明放在可滚动容器里。
 * 点「立即更新」后由系统 DownloadManager 在通知栏下载，因此这里不再需要 downloading 态。
 */
@Composable
fun UpdateDialog(
    version: String,
    changelog: String,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.SystemUpdateAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "发现新版本",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    QnChip(
                        text = if (version.isBlank()) "最新版本" else "v$version",
                        tone = QnTone.Success
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "更新说明",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = changelog.ifBlank { "本次更新包含体验与稳定性改进。" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            QnPrimaryButton(
                text = "立即更新",
                onClick = onDownload
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("稍后提醒") }
        }
    )
}
