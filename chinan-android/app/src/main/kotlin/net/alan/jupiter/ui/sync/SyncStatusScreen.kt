package net.alan.jupiter.ui.sync

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import net.alan.jupiter.domain.model.SyncState
import net.alan.jupiter.domain.sync.SyncPolicy
import net.alan.jupiter.ui.components.QnCard
import net.alan.jupiter.ui.components.QnEmpty
import net.alan.jupiter.ui.components.QnGhostButton
import net.alan.jupiter.ui.components.QnKeyValue
import net.alan.jupiter.ui.components.QnMono
import net.alan.jupiter.ui.components.QnPrimaryButton
import net.alan.jupiter.ui.components.QnRadius
import net.alan.jupiter.ui.components.QnScaffold
import net.alan.jupiter.ui.components.QnSectionLabel
import net.alan.jupiter.ui.components.QnStatus
import net.alan.jupiter.ui.components.QnTag
import net.alan.jupiter.ui.theme.QnAccent
import net.alan.jupiter.ui.theme.QnDanger
import net.alan.jupiter.ui.theme.QnLine
import net.alan.jupiter.ui.theme.QnPanel
import net.alan.jupiter.ui.theme.QnText
import net.alan.jupiter.ui.theme.QnTextDim
import net.alan.jupiter.ui.theme.QnTextMuted
import net.alan.jupiter.ui.theme.QnWarn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A10 缓存与同步：outbox 列表 + 立即同步 + 按 taskDate 时间段补交 + 清空数据。
 *
 * clientOpId 是业务幂等键（服务端 dedupe），和 TCP 的 requestId（多路复用）不是一回事。
 */
@Composable
fun SyncStatusScreen(
    onBackClick: () -> Unit,
    viewModel: SyncViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        viewModel.consumeMessage()
        snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
    }

    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        viewModel.consumeError()
        snackbarHostState.showSnackbar(message = error, duration = SnackbarDuration.Short)
    }

    QnScaffold(
        path = "/sync",
        status = when {
            uiState.isSyncing -> QnStatus.RUNNING
            uiState.failedCount > 0 -> QnStatus.FAILED
            uiState.pendingCount > 0 -> QnStatus.QUEUED
            else -> QnStatus.OK
        },
        statusDetail = if (uiState.pendingCount > 0) "${uiState.pendingCount}" else null,
        onBack = onBackClick
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 12.dp,
                    bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "storage") {
                    StorageCard(
                        records = uiState.records,
                        acked = uiState.acked,
                        pending = uiState.pendingCount,
                        failed = uiState.failedCount
                    )
                }

                item(key = "time_range") {
                    TimeRangeCard(
                        fromDate = uiState.fromDate,
                        toDate = uiState.toDate,
                        isSyncing = uiState.isSyncing,
                        onFromChange = viewModel::updateFromDate,
                        onToChange = viewModel::updateToDate,
                        onClearRange = viewModel::clearRange,
                        onSubmitRange = { viewModel.syncNow() }
                    )
                }

                item(key = "actions") {
                    ActionsCard(
                        isSyncing = uiState.isSyncing,
                        lastSyncAt = uiState.lastSyncAt,
                        onSyncNow = { viewModel.syncNow() },
                        onClearData = { showClearDialog = true }
                    )
                }

                item(key = "outbox_label") {
                    QnSectionLabel("\$ outbox · 上行队列 / ${uiState.outbox.size}")
                }

                if (uiState.outbox.isEmpty()) {
                    item(key = "outbox_empty") {
                        QnEmpty(
                            icon = Icons.Default.Sync,
                            title = "队列为空",
                            hint = "所有本地查寝记录都已 ACK，或还没有提交过",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    itemsIndexed(
                        uiState.outbox,
                        key = { _, row -> row.clientOpId }
                    ) { index, row ->
                        OutboxRowCard(index = index + 1, row = row)
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = QnPanel,
                    contentColor = QnText,
                    shape = QnRadius,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = QnPanel,
            shape = QnRadius,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = "清空本地数据",
                    color = QnText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "将删除本机所有账号的查寝记录、待提交队列、缓存快照与证明照片，删除后不可恢复。确定继续？",
                    color = QnTextMuted,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                QnPrimaryButton(
                    text = "确认清空",
                    onClick = {
                        showClearDialog = false
                        viewModel.clearLocalData()
                    }
                )
            },
            dismissButton = {
                QnGhostButton(
                    text = "取消",
                    onClick = { showClearDialog = false }
                )
            }
        )
    }
}

@Composable
private fun StorageCard(records: Int, acked: Int, pending: Int, failed: Int) {
    QnCard {
        QnSectionLabel("STORAGE / Room · WAL")
        Spacer(modifier = Modifier.height(6.dp))
        QnKeyValue(label = "records", value = "$records（ACKED $acked）")
        QnKeyValue(label = "outbox", value = "$pending（FAILED $failed）")
        QnKeyValue(label = "retain", value = "永久保留，退出登录不删")
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "LOCAL_DRAFT → QUEUED → IN_FLIGHT → ACKED / FAILED",
            color = QnTextDim,
            fontFamily = QnMono,
            fontSize = 9.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "下行数据不覆盖本地 QUEUED/FAILED；ACK 后才以服务端为准",
            color = QnTextDim,
            fontSize = 9.5.sp
        )
    }
}

@Composable
private fun TimeRangeCard(
    fromDate: String,
    toDate: String,
    isSyncing: Boolean,
    onFromChange: (String) -> Unit,
    onToChange: (String) -> Unit,
    onClearRange: () -> Unit,
    onSubmitRange: () -> Unit
) {
    QnCard {
        QnSectionLabel("补交 / TASK DATE RANGE")
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "只提交该时间段内 QUEUED/FAILED 的记录；taskDate 不改写（服务端负责），已 ACK 不重复提交。",
            color = QnTextMuted,
            fontSize = 10.5.sp,
            lineHeight = 15.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = fromDate,
                onValueChange = onFromChange,
                modifier = Modifier.weight(1f),
                label = { Text("FROM", fontFamily = QnMono, fontSize = 9.sp) },
                placeholder = { Text("yyyy-MM-dd", fontFamily = QnMono, fontSize = 10.sp) },
                singleLine = true,
                textStyle = TextStyle(
                    color = QnText,
                    fontFamily = QnMono,
                    fontSize = 11.sp
                )
            )
            OutlinedTextField(
                value = toDate,
                onValueChange = onToChange,
                modifier = Modifier.weight(1f),
                label = { Text("TO", fontFamily = QnMono, fontSize = 9.sp) },
                placeholder = { Text("yyyy-MM-dd", fontFamily = QnMono, fontSize = 10.sp) },
                singleLine = true,
                textStyle = TextStyle(
                    color = QnText,
                    fontFamily = QnMono,
                    fontSize = 11.sp
                )
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QnPrimaryButton(
                text = if (isSyncing) "同步中..." else "按时间段补交",
                onClick = onSubmitRange,
                modifier = Modifier.weight(1f),
                enabled = !isSyncing
            )
            QnGhostButton(
                text = "清空范围",
                onClick = onClearRange,
                modifier = Modifier.weight(1f),
                enabled = !isSyncing
            )
        }
    }
}

@Composable
private fun ActionsCard(
    isSyncing: Boolean,
    lastSyncAt: Long?,
    onSyncNow: () -> Unit,
    onClearData: () -> Unit
) {
    QnCard {
        QnSectionLabel("ACTIONS")
        Spacer(modifier = Modifier.height(8.dp))
        QnPrimaryButton(
            text = if (isSyncing) "同步中..." else "▸ 立即同步",
            onClick = onSyncNow,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSyncing
        )
        Spacer(modifier = Modifier.height(8.dp))
        QnGhostButton(
            text = "清空本地数据",
            onClick = onClearData,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSyncing
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = lastSyncAt?.let { "LAST SYNC ${formatClock(it)}" } ?: "LAST SYNC --",
            color = QnTextDim,
            fontFamily = QnMono,
            fontSize = 9.sp
        )
    }
}

@Composable
private fun OutboxRowCard(index: Int, row: OutboxRow) {
    val color = stateColor(row.state)
    QnCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = index.toString().padStart(2, '0'),
                color = QnTextDim,
                fontFamily = QnMono,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SUBMIT_CHECK ${row.roomId}",
                    color = QnText,
                    fontFamily = QnMono,
                    fontSize = 11.5.sp,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "clientOpId ${row.shortOpId}… · retry ${row.retryCount}/${SyncPolicy.MAX_RETRY}" +
                        (row.taskDate?.let { " · $it" } ?: ""),
                    color = QnTextDim,
                    fontFamily = QnMono,
                    fontSize = 9.sp,
                    maxLines = 1
                )
                if (!row.lastError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = row.lastError,
                        color = QnDanger,
                        fontSize = 9.5.sp,
                        maxLines = 2
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            QnTag(text = row.state, color = color)
        }
        HorizontalDivider(
            color = QnLine,
            thickness = 1.dp,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "task ${row.taskId}",
            color = QnTextDim,
            fontFamily = QnMono,
            fontSize = 9.sp,
            maxLines = 1
        )
    }
}

private fun stateColor(state: String): Color = when (SyncState.fromName(state)) {
    SyncState.ACKED -> QnAccent
    SyncState.QUEUED, SyncState.IN_FLIGHT -> QnWarn
    SyncState.FAILED, SyncState.CONFLICT -> QnDanger
    SyncState.LOCAL_DRAFT -> QnTextMuted
}

private fun formatClock(millis: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))
