package net.alan.jupiter.ui.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
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
import net.alan.jupiter.domain.summary.SummaryBucket
import net.alan.jupiter.domain.summary.SummaryCounts
import net.alan.jupiter.domain.summary.SummaryResult
import net.alan.jupiter.domain.summary.SummaryScope
import net.alan.jupiter.ui.components.QnCard
import net.alan.jupiter.ui.components.QnChip
import net.alan.jupiter.ui.components.QnEmpty
import net.alan.jupiter.ui.components.QnFilterChip
import net.alan.jupiter.ui.components.QnGroupLabel
import net.alan.jupiter.ui.components.QnKeyValue
import net.alan.jupiter.ui.components.QnListItem
import net.alan.jupiter.ui.components.QnNoticeCard
import net.alan.jupiter.ui.components.QnProgress
import net.alan.jupiter.ui.components.QnScaffold
import net.alan.jupiter.ui.components.QnStat
import net.alan.jupiter.ui.components.QnStatRow
import net.alan.jupiter.ui.components.QnTextButton
import net.alan.jupiter.ui.components.QnTopIcon
import net.alan.jupiter.ui.theme.QnMono
import net.alan.jupiter.ui.theme.QnTone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 总汇总（/summary）：本地聚合的应到 / 实到 / 请假 / 旷宿 + 按楼栋 / 按班级，
 * 有未同步记录时标注「含 N 条待同步，为本地预估」，快照信息收进折叠的「数据说明」。
 */
@Composable
fun GlobalSummaryScreen(
    onBackClick: () -> Unit,
    onSyncClick: () -> Unit,
    viewModel: SummaryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val result = uiState.result
    val counts = result?.counts ?: SummaryCounts()
    var showSnapshot by remember { mutableStateOf(false) }

    QnScaffold(
        title = "汇总",
        onBack = onBackClick,
        actions = {
            QnTopIcon(
                icon = Icons.Default.Refresh,
                label = "刷新汇总",
                onClick = { viewModel.refresh() }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading && result == null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "正在汇总本地快照…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                result == null -> {
                    QnEmpty(
                        icon = Icons.Default.Assessment,
                        title = "暂无本地汇总数据",
                        hint = "先刷新任务列表并进入房间查寝，本地快照会自动累积",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    )
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item(key = "scope") {
                            ScopeCard(
                                scope = uiState.scope,
                                onSelect = { viewModel.selectScope(it) }
                            )
                        }

                        item(key = "counts") {
                            CountsCard(counts = counts)
                        }

                        if (result.pending > 0) {
                            item(key = "pending") {
                                QnNoticeCard(
                                    title = "含 ${result.pending} 条待同步，为本地预估",
                                    text = "同步后数字会以服务端为准",
                                    tone = QnTone.Warning,
                                    actionLabel = "去同步",
                                    onAction = onSyncClick
                                )
                            }
                        }

                        item(key = "buildings") {
                            BucketCard(title = "按楼栋", buckets = result.buildings)
                        }

                        item(key = "classes") {
                            BucketCard(title = "按班级", buckets = result.classes)
                        }

                        item(key = "snapshot_toggle") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                QnTextButton(
                                    text = if (showSnapshot) "收起数据说明" else "数据说明",
                                    onClick = { showSnapshot = !showSnapshot },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (showSnapshot) {
                            item(key = "snapshot") {
                                SnapshotCard(result = result)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScopeCard(scope: SummaryScope, onSelect: (SummaryScope) -> Unit) {
    QnCard(contentPadding = PaddingValues(18.dp)) {
        QnGroupLabel("汇总范围", modifier = Modifier.padding(top = 0.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryScope.entries.forEach { item ->
                QnFilterChip(
                    text = item.label,
                    selected = item == scope,
                    onClick = { onSelect(item) }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "按任务日期归集，不改写服务端发布日期",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CountsCard(counts: SummaryCounts) {
    QnCard(contentPadding = PaddingValues(18.dp)) {
        Text(
            text = "应到 / 实到",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        QnStatRow {
            QnStat(label = "应到", value = "${counts.should}", modifier = Modifier.weight(1f))
            QnStat(
                label = "实到",
                value = "${counts.actual}",
                modifier = Modifier.weight(1f),
                valueColor = MaterialTheme.colorScheme.primary
            )
            QnStat(
                label = "请假",
                value = "${counts.leave}",
                modifier = Modifier.weight(1f),
                valueColor = if (counts.leave > 0) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            QnStat(
                label = "旷宿",
                value = "${counts.absent}",
                modifier = Modifier.weight(1f),
                valueColor = if (counts.absent > 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        QnProgress(progress = counts.completionRate)
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QnChip(
                text = "完成率 ${formatRate(counts.completionRate)}",
                tone = QnTone.Success
            )
            QnChip(text = "未查 ${counts.unchecked}", tone = QnTone.Neutral)
        }
    }
}

@Composable
private fun BucketCard(title: String, buckets: List<SummaryBucket>) {
    QnCard(contentPadding = PaddingValues(vertical = 8.dp)) {
        QnGroupLabel(title, modifier = Modifier.padding(horizontal = 16.dp))
        if (buckets.isEmpty()) {
            Text(
                text = "暂无数据",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            return@QnCard
        }
        buckets.forEach { bucket ->
            QnListItem(
                title = bucket.key,
                meta = "实到 ${bucket.actual} / 应到 ${bucket.should}",
                trailing = {
                    Text(
                        text = formatRate(bucket.rate),
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = QnMono,
                        color = if (bucket.rate >= 0.95f) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun SnapshotCard(result: SummaryResult) {
    QnCard(contentPadding = PaddingValues(18.dp)) {
        QnGroupLabel("数据说明", modifier = Modifier.padding(top = 0.dp))
        QnKeyValue(label = "统计时间", value = formatTime(result.asOf), mono = true)
        QnKeyValue(label = "数据来源", value = "本机快照（本地缓存）")
        QnKeyValue(label = "任务数", value = "${result.taskCount}", mono = true)
        QnKeyValue(label = "学生数", value = "${result.studentCount}", mono = true)
        QnKeyValue(label = "待同步", value = "${result.pending}", mono = true)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "本地聚合：任务快照 + 学生快照 + 本地查寝记录，不含服务端全量明细。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))

private fun formatRate(rate: Float): String {
    val percent = (rate * 100f * 10f).roundToInt() / 10f
    return String.format(Locale.US, "%.1f%%", percent)
}
