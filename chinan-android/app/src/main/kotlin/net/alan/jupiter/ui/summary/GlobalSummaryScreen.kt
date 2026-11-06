package net.alan.jupiter.ui.summary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import net.alan.jupiter.domain.summary.SummaryBucket
import net.alan.jupiter.domain.summary.SummaryCounts
import net.alan.jupiter.domain.summary.SummaryResult
import net.alan.jupiter.domain.summary.SummaryScope
import net.alan.jupiter.ui.components.QnAsciiProgress
import net.alan.jupiter.ui.components.QnCard
import net.alan.jupiter.ui.components.QnEmpty
import net.alan.jupiter.ui.components.QnGhostButton
import net.alan.jupiter.ui.components.QnKeyValue
import net.alan.jupiter.ui.components.QnMono
import net.alan.jupiter.ui.components.QnRadius
import net.alan.jupiter.ui.components.QnScaffold
import net.alan.jupiter.ui.components.QnSectionLabel
import net.alan.jupiter.ui.components.QnStat
import net.alan.jupiter.ui.components.QnStatus
import net.alan.jupiter.ui.components.QnTag
import net.alan.jupiter.ui.theme.QnAccent
import net.alan.jupiter.ui.theme.QnLine
import net.alan.jupiter.ui.theme.QnPanelAlt
import net.alan.jupiter.ui.theme.QnText
import net.alan.jupiter.ui.theme.QnTextDim
import net.alan.jupiter.ui.theme.QnTextMuted
import net.alan.jupiter.ui.theme.QnWarn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A09 总汇总：本地聚合（应到/实到/请假/旷宿/完成率 + 按楼栋/班级），
 * 右上角固定 `● CACHED asOf`，有未同步记录时标注“含 N 条待同步，为本地预估”。
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

    QnScaffold(
        path = "/summary",
        status = when {
            uiState.isLoading && result == null -> QnStatus.RUNNING
            result == null -> QnStatus.IDLE
            else -> QnStatus.CACHED
        },
        statusDetail = result?.asOf?.let { formatTime(it) },
        onBack = onBackClick,
        actions = {
            IconButton(
                onClick = { viewModel.refresh() },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "刷新汇总",
                    tint = QnTextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
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
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(34.dp),
                            strokeWidth = 2.dp,
                            color = QnAccent
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            "// 正在聚合本地快照",
                            color = QnTextDim,
                            fontFamily = QnMono,
                            fontSize = 11.sp
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
                            start = 12.dp,
                            end = 12.dp,
                            top = 12.dp,
                            bottom = 16.dp
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
                                PendingCard(
                                    pending = result.pending,
                                    onSyncClick = onSyncClick
                                )
                            }
                        }

                        item(key = "buildings") {
                            BucketCard(title = "按楼栋 BUILDING", buckets = result.buildings)
                        }

                        item(key = "classes") {
                            BucketCard(title = "按班级 CLASS", buckets = result.classes)
                        }

                        item(key = "snapshot") {
                            SnapshotCard(result = result)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScopeCard(scope: SummaryScope, onSelect: (SummaryScope) -> Unit) {
    QnCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        QnSectionLabel("SCOPE")
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryScope.entries.forEach { item ->
                val selected = item == scope
                Surface(
                    modifier = Modifier.clickable { onSelect(item) },
                    shape = QnRadius,
                    color = if (selected) QnPanelAlt else androidx.compose.ui.graphics.Color.Transparent,
                    border = BorderStroke(
                        1.dp,
                        if (selected) QnAccent.copy(alpha = 0.65f) else QnLine
                    )
                ) {
                    Text(
                        text = item.label,
                        color = if (selected) QnAccent else QnTextMuted,
                        fontFamily = QnMono,
                        fontSize = 10.5.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "按 taskDate 归集 · 不改写服务端发布日期",
            color = QnTextDim,
            fontFamily = QnMono,
            fontSize = 9.sp
        )
    }
}

@Composable
private fun CountsCard(counts: SummaryCounts) {
    QnCard {
        QnSectionLabel("SUMMARY")
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            QnStat(label = "SHOULD", value = "${counts.should}")
            QnStat(label = "ACTUAL", value = "${counts.actual}", valueColor = QnAccent)
            QnStat(
                label = "LEAVE",
                value = "${counts.leave}",
                valueColor = if (counts.leave > 0) QnWarn else QnText
            )
            QnStat(
                label = "ABSENT",
                value = "${counts.absent}",
                valueColor = if (counts.absent > 0) QnText else QnTextMuted
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        QnAsciiProgress(progress = counts.completionRate)
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            QnTag(text = "RATE ${formatRate(counts.completionRate)}", color = QnAccent)
            Spacer(modifier = Modifier.width(10.dp))
            QnTag(text = "UNCHECKED ${counts.unchecked}", color = QnTextMuted)
        }
    }
}

@Composable
private fun PendingCard(pending: Int, onSyncClick: () -> Unit) {
    QnCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            QnTag(text = "QUEUED $pending", color = QnWarn)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "含 $pending 条待同步，为本地预估",
                color = QnTextMuted,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f)
            )
            QnGhostButton(text = "去同步", onClick = onSyncClick)
        }
    }
}

@Composable
private fun BucketCard(title: String, buckets: List<SummaryBucket>) {
    QnCard {
        QnSectionLabel(title)
        Spacer(modifier = Modifier.height(8.dp))
        if (buckets.isEmpty()) {
            Text(
                text = "-- 无数据 --",
                color = QnTextDim,
                fontFamily = QnMono,
                fontSize = 10.sp
            )
            return@QnCard
        }
        HorizontalDivider(color = QnLine, thickness = 1.dp)
        buckets.forEach { bucket ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = bucket.key,
                    color = QnText,
                    fontFamily = QnMono,
                    fontSize = 11.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${bucket.actual}/${bucket.should}",
                    color = QnTextMuted,
                    fontFamily = QnMono,
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = formatRate(bucket.rate),
                    color = if (bucket.rate >= 0.95f) QnAccent else QnWarn,
                    fontFamily = QnMono,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            HorizontalDivider(color = QnLine, thickness = 1.dp)
        }
    }
}

@Composable
private fun SnapshotCard(result: SummaryResult) {
    QnCard {
        QnSectionLabel("SNAPSHOT")
        Spacer(modifier = Modifier.height(6.dp))
        QnKeyValue(label = "asOf", value = formatTime(result.asOf))
        QnKeyValue(label = "status", value = "● CACHED")
        QnKeyValue(label = "tasks", value = "${result.taskCount}")
        QnKeyValue(label = "students", value = "${result.studentCount}")
        QnKeyValue(label = "pending", value = "${result.pending}")
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "本地聚合：TaskSnapshot + StudentSnapshot + CheckRecordLocal（不含服务端全量明细）",
            color = QnTextDim,
            fontSize = 9.5.sp,
            lineHeight = 14.sp
        )
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))

private fun formatRate(rate: Float): String {
    val percent = (rate * 100f * 10f).roundToInt() / 10f
    return String.format(Locale.US, "%.1f%%", percent)
}
