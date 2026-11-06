package net.alan.jupiter.ui.tasks

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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.alan.jupiter.domain.model.TaskSummary
import net.alan.jupiter.ui.components.QnCard
import net.alan.jupiter.ui.components.QnChip
import net.alan.jupiter.ui.components.QnEmpty
import net.alan.jupiter.ui.components.QnError
import net.alan.jupiter.ui.components.QnGroupLabel
import net.alan.jupiter.ui.components.QnHeroCard
import net.alan.jupiter.ui.components.QnNoticeCard
import net.alan.jupiter.ui.components.QnPrimaryButton
import net.alan.jupiter.ui.components.QnProgress
import net.alan.jupiter.ui.components.QnScaffold
import net.alan.jupiter.ui.components.QnStat
import net.alan.jupiter.ui.components.QnStatRow
import net.alan.jupiter.ui.components.QnTopIcon
import net.alan.jupiter.ui.theme.QnMono
import net.alan.jupiter.ui.theme.QnTone
import kotlin.math.roundToInt

/**
 * 今日任务（/tasks，登录后的主页）。
 *
 * 顶栏 4 个操作（刷新 / 汇总 / 同步 / 设置）+ 概览大卡 + 待同步提示 + 任务行。
 * 同步状态只表达「还没上传的记录」，房间进度一律用中文（待查 N 间 / 已完成）。
 */
@Composable
fun TaskListScreen(
    onTaskClick: (String, String, String) -> Unit,
    onSettingsClick: () -> Unit,
    onLogout: () -> Unit,
    onSummaryClick: () -> Unit = {},
    onSyncClick: () -> Unit = {},
    viewModel: TaskListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val totalChecked = uiState.tasks.sumOf { it.checkedRooms }
    val totalRooms = uiState.tasks.sumOf { it.totalRooms }
    val overallProgress = if (totalRooms > 0) totalChecked.toFloat() / totalRooms else 0f
    val doneTasks = uiState.tasks.count { task ->
        task.totalRooms > 0 && task.checkedRooms >= task.totalRooms
    }
    val openTasks = uiState.tasks.count { task ->
        val completed = task.totalRooms > 0 && task.checkedRooms >= task.totalRooms
        !completed && !task.status.equals("CANCELLED", ignoreCase = true)
    }

    QnScaffold(
        title = "今日任务",
        actions = {
            QnTopIcon(
                icon = Icons.Default.Refresh,
                label = "刷新",
                onClick = { viewModel.loadTasks() }
            )
            QnTopIcon(
                icon = Icons.Default.Assessment,
                label = "汇总",
                onClick = onSummaryClick
            )
            QnTopIcon(
                icon = Icons.Default.Sync,
                label = "同步",
                onClick = onSyncClick
            )
            QnTopIcon(
                icon = Icons.Default.Settings,
                label = "设置",
                onClick = onSettingsClick
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading && uiState.tasks.isEmpty() -> {
                    LoadingBlock()
                }

                uiState.error != null && uiState.tasks.isEmpty() -> {
                    QnError(
                        title = "连接失败",
                        message = uiState.error ?: "未知错误",
                        onRetry = { viewModel.loadTasks() },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        icon = Icons.Default.CloudOff
                    )
                }

                uiState.tasks.isEmpty() -> {
                    QnEmpty(
                        icon = Icons.AutoMirrored.Filled.Assignment,
                        title = "今天还没有查寝任务",
                        hint = "联系管理员分配任务后，点右上角刷新",
                        actionLabel = "刷新",
                        onAction = { viewModel.loadTasks() },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    )
                }

                else -> {
                    val listState = rememberSaveable(saver = LazyListState.Saver) {
                        LazyListState()
                    }
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item(key = "overview") {
                            OverviewHero(
                                openTasks = openTasks,
                                doneTasks = doneTasks,
                                pendingTotal = uiState.pendingTotal,
                                checkedRooms = totalChecked,
                                totalRooms = totalRooms,
                                progress = overallProgress
                            )
                        }

                        if (uiState.pendingTotal > 0) {
                            item(key = "pending_banner") {
                                QnNoticeCard(
                                    title = "还有 ${uiState.pendingTotal} 条记录没上传",
                                    text = "含 ${uiState.pendingTotal} 条待同步，为本地预估",
                                    tone = QnTone.Warning,
                                    icon = Icons.Outlined.CloudUpload,
                                    actionLabel = "去同步",
                                    onAction = onSyncClick
                                )
                            }
                        } else if (uiState.cacheOnly) {
                            item(key = "cache_banner") {
                                QnNoticeCard(
                                    title = "当前显示本地缓存",
                                    text = uiState.error ?: "网络不可用，任务数据来自本机快照",
                                    tone = QnTone.Neutral
                                )
                            }
                        }

                        item(key = "tasks_label") {
                            QnGroupLabel("今日任务 · ${uiState.tasks.size} 项")
                        }

                        items(uiState.tasks, key = { it.id }) { task ->
                            TaskCard(
                                task = task,
                                pending = uiState.pendingByTask[task.id] ?: 0,
                                onClick = {
                                    onTaskClick(task.id, task.taskNo, task.buildingName)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.needsReauth) {
        AlertDialog(
            onDismissRequest = { viewModel.clearReauth() },
            title = { Text("登录已失效") },
            text = { Text("数据发生变动，需要重新登录才能继续同步。本地记录不会丢失。") },
            confirmButton = {
                QnPrimaryButton(
                    text = "重新登录",
                    onClick = {
                        viewModel.clearReauth()
                        onLogout()
                    }
                )
            }
        )
    }
}

@Composable
private fun LoadingBlock() {
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
            text = "正在同步任务列表…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OverviewHero(
    openTasks: Int,
    doneTasks: Int,
    pendingTotal: Int,
    checkedRooms: Int,
    totalRooms: Int,
    progress: Float
) {
    QnHeroCard {
        Text(
            text = "今日概览",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(modifier = Modifier.height(6.dp))
        QnStatRow {
            QnStat(
                label = "待查任务",
                value = "$openTasks",
                modifier = Modifier.weight(1f),
                valueColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
            QnStat(
                label = "已完成",
                value = "$doneTasks",
                modifier = Modifier.weight(1f),
                valueColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
            QnStat(
                label = "待同步",
                value = "$pendingTotal",
                modifier = Modifier.weight(1f),
                valueColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        QnProgress(
            progress = progress,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "总进度 ${(progress * 100).roundToInt()}% · 已查 $checkedRooms/$totalRooms 间",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun TaskCard(task: TaskSummary, pending: Int, onClick: () -> Unit) {
    val total = task.totalRooms
    val checked = task.checkedRooms
    val progress = if (total > 0) checked.toFloat() / total else 0f
    val isCompleted = total > 0 && checked >= total
    val remaining = (total - checked).coerceAtLeast(0)
    val isCancelled = task.status.equals("CANCELLED", ignoreCase = true)

    val chipText: String
    val chipTone: QnTone
    val chipIcon: ImageVector
    when {
        isCancelled -> {
            chipText = "已取消"
            chipTone = QnTone.Neutral
            chipIcon = Icons.AutoMirrored.Filled.Assignment
        }
        pending > 0 -> {
            chipText = "待同步 $pending"
            chipTone = QnTone.Warning
            chipIcon = Icons.Outlined.CloudUpload
        }
        isCompleted -> {
            chipText = "已完成"
            chipTone = QnTone.Success
            chipIcon = Icons.Default.Assessment
        }
        checked == 0 -> {
            chipText = "待查 $remaining 间"
            chipTone = QnTone.Warning
            chipIcon = Icons.AutoMirrored.Filled.Assignment
        }
        else -> {
            chipText = "进行中"
            chipTone = QnTone.Neutral
            chipIcon = Icons.AutoMirrored.Filled.Assignment
        }
    }

    val meta = listOfNotNull(
        task.taskNo.takeIf { it.isNotBlank() },
        task.buildingName.takeIf { it.isNotBlank() },
        task.floorNumber.takeIf { it.isNotBlank() }?.let { "${it}F" },
        if (total > 0) "房间 $checked/$total" else null,
        task.deadlineTime?.takeIf { it.isNotBlank() }?.let { "截止 $it" }
    ).joinToString(" · ")

    QnCard(onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title.ifBlank { task.taskNo },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = meta.ifBlank { "--" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                QnChip(text = chipText, tone = chipTone, leadingIcon = chipIcon)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "$checked/$total",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = QnMono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        QnProgress(
            progress = progress,
            color = if (isCompleted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.tertiary
            }
        )
    }
}
