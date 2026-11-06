package net.alan.jupiter.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.alan.jupiter.ui.components.QnCard
import net.alan.jupiter.ui.components.QnDivider
import net.alan.jupiter.ui.components.QnFilterChip
import net.alan.jupiter.ui.components.QnGroupLabel
import net.alan.jupiter.ui.components.QnHeroCard
import net.alan.jupiter.ui.components.QnNoticeCard
import net.alan.jupiter.ui.components.QnOutlinedButton
import net.alan.jupiter.ui.components.QnPrimaryButton
import net.alan.jupiter.ui.components.QnProgress
import net.alan.jupiter.ui.components.QnScaffold
import net.alan.jupiter.ui.components.QnStat
import net.alan.jupiter.ui.components.QnTextButton
import net.alan.jupiter.ui.components.QnTonalButton
import net.alan.jupiter.ui.theme.QnMono
import net.alan.jupiter.ui.theme.QnTone

/**
 * 同步与上传（/sync）。
 *
 * 一个主按钮完成同步，日期范围用 FilterChip 选；按钮上的数字、结果卡与「还剩 N 条」
 * 全部按当前范围联动。outbox / clientOpId / 重试次数收进「高级 / 诊断」。
 */
@Composable
fun SyncStatusScreen(onBackClick: () -> Unit, viewModel: SyncViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var scope by remember { mutableStateOf(ResubmitScope.ALL) }
    var advanced by remember { mutableStateOf(false) }

    val today = java.time.LocalDate.now()
    val scopeRows = uiState.outbox.filter { row ->
        (row.state == "QUEUED" || row.state == "IN_FLIGHT" || row.state == "FAILED") &&
            when (scope) {
                ResubmitScope.ALL -> true
                ResubmitScope.YESTERDAY ->
                    row.taskDate.isNullOrBlank() || row.taskDate == today.minusDays(1).toString()
                ResubmitScope.LAST_7_DAYS ->
                    row.taskDate.isNullOrBlank() ||
                        (row.taskDate >= today.minusDays(6).toString() &&
                            row.taskDate <= today.toString())
            }
    }
    val pending = scopeRows.size
    val message = uiState.error ?: uiState.message

    QnScaffold(title = "同步与上传", onBack = onBackClick) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "hero") {
                QnHeroCard {
                    Text(
                        text = "还没上传",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "$pending",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "条",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        text = "把手机上还没上传的查寝记录同步到服务器",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "已上传 ${uiState.acked} 条 · 本机记录 ${uiState.records} 条",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = QnMono,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            item(key = "actions") {
                QnCard(contentPadding = PaddingValues(18.dp)) {
                    Text(
                        text = if (pending > 0) "$pending 条查寝记录还没上传" else "所选范围没有待上传记录",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "断网时记录会先存在手机里，联网后也会自动上传。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    QnGroupLabel("补哪几天")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QnFilterChip(
                            text = "昨天",
                            selected = scope == ResubmitScope.YESTERDAY,
                            onClick = { scope = ResubmitScope.YESTERDAY }
                        )
                        QnFilterChip(
                            text = "近 7 天",
                            selected = scope == ResubmitScope.LAST_7_DAYS,
                            onClick = { scope = ResubmitScope.LAST_7_DAYS }
                        )
                        QnFilterChip(
                            text = "全部",
                            selected = scope == ResubmitScope.ALL,
                            onClick = { scope = ResubmitScope.ALL }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    QnPrimaryButton(
                        text = when {
                            uiState.isSyncing -> "正在同步…"
                            pending > 0 -> "同步全部（$pending）"
                            else -> "同步全部"
                        },
                        enabled = !uiState.isSyncing && pending > 0,
                        onClick = { viewModel.resubmitAll(scope) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (uiState.isSyncing) {
                        Spacer(modifier = Modifier.height(14.dp))
                        QnProgress(progress = 0.6f)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "正在上传，请保持网络连接",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (message != null) {
                item(key = "result") {
                    if (uiState.error != null) {
                        QnNoticeCard(
                            title = "上传失败",
                            text = message + "\n下一步：检查网络或重新登录后再试，本地记录不会丢。",
                            tone = QnTone.Danger
                        )
                    } else {
                        QnCard(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentPadding = PaddingValues(18.dp)
                        ) {
                            Text(
                                text = "同步结果",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            if (uiState.error != null) {
                item(key = "retry") {
                    QnTonalButton(
                        text = "再试一次",
                        onClick = { viewModel.resubmitAll(scope) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isSyncing
                    )
                }
            }

            item(key = "advanced_toggle") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    QnTextButton(
                        text = if (advanced) "收起诊断" else "高级 / 诊断",
                        onClick = { advanced = !advanced },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (advanced) {
                item(key = "diag_header") {
                    QnCard(contentPadding = PaddingValues(18.dp)) {
                        Text(
                            text = "待上传明细",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "含 clientOpId（服务端幂等键）与重试次数，仅在排查问题时需要。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (uiState.outbox.isEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "没有待上传记录",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                items(uiState.outbox) { row ->
                    QnCard(contentPadding = PaddingValues(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            QnStat(
                                label = "房间",
                                value = row.roomId.take(6),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = when (row.state) {
                                    "QUEUED" -> "待同步"
                                    "FAILED" -> "失败，可重试"
                                    "ACKED" -> "已上传"
                                    else -> row.state
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        QnDivider()
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "任务日期 ${row.taskDate ?: "-"} · 第 ${row.retryCount}/3 次重试",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "clientOpId ${row.shortOpId}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = QnMono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        row.lastError?.let {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                item(key = "retry_all") {
                    QnOutlinedButton(
                        text = "立即重试全部（忽略筛选）",
                        enabled = !uiState.isSyncing,
                        onClick = { viewModel.retryAll() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item(key = "tip") {
                QnNoticeCard(
                    text = "清空本地数据不在这里，已移到「设置 → 本地数据」，且需要二次确认。",
                    tone = QnTone.Neutral
                )
            }
        }
    }
}
