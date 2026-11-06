package net.alan.jupiter.ui.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import net.alan.jupiter.domain.model.TaskSummary
import net.alan.jupiter.ui.components.QnAsciiProgress
import net.alan.jupiter.ui.components.QnCard
import net.alan.jupiter.ui.components.QnEmpty
import net.alan.jupiter.ui.components.QnError
import net.alan.jupiter.ui.components.QnGhostButton
import net.alan.jupiter.ui.components.QnMono
import net.alan.jupiter.ui.components.QnPrimaryButton
import net.alan.jupiter.ui.components.QnRadius
import net.alan.jupiter.ui.components.QnScaffold
import net.alan.jupiter.ui.components.QnSectionLabel
import net.alan.jupiter.ui.components.QnStat
import net.alan.jupiter.ui.components.QnStatus
import net.alan.jupiter.ui.components.QnTag
import net.alan.jupiter.ui.theme.QnAccent
import net.alan.jupiter.ui.theme.QnLine
import net.alan.jupiter.ui.theme.QnPanel
import net.alan.jupiter.ui.theme.QnText
import net.alan.jupiter.ui.theme.QnTextDim
import net.alan.jupiter.ui.theme.QnTextMuted
import net.alan.jupiter.ui.theme.QnWarn

/**
 * 任务列表（/tasks）：极客壳 + 统计块 + 「编号/标题/元信息/ASCII 进度」任务卡。
 * 业务逻辑与 ViewModel 完全沿用，只替换视觉外壳。
 */
@Composable
fun TaskListScreen(
    onTaskClick: (String, String, String) -> Unit,
    onSettingsClick: () -> Unit,
    onProfileClick: () -> Unit,
    onLogout: () -> Unit,
    onSummaryClick: () -> Unit = {},
    onSyncClick: () -> Unit = {},
    viewModel: TaskListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var fabExpanded by remember { mutableStateOf(false) }

    val totalChecked = uiState.tasks.sumOf { it.checkedRooms }
    val totalRooms = uiState.tasks.sumOf { it.totalRooms }
    val overallProgress = if (totalRooms > 0) totalChecked.toFloat() / totalRooms else 0f
    val openTasks = uiState.tasks.count { task ->
        val completed = task.totalRooms > 0 && task.checkedRooms >= task.totalRooms
        !completed && !task.status.equals("CANCELLED", ignoreCase = true)
    }

    val headerStatus = when {
        uiState.pendingTotal > 0 -> QnStatus.SYNC
        uiState.cacheOnly -> QnStatus.CACHED
        uiState.error != null && uiState.tasks.isEmpty() -> QnStatus.OFFLINE
        uiState.isLoading && uiState.tasks.isEmpty() -> QnStatus.RUNNING
        openTasks > 0 -> QnStatus.SYNC
        else -> QnStatus.OK
    }
    val headerDetail = when (headerStatus) {
        QnStatus.SYNC -> if (uiState.pendingTotal > 0) "${uiState.pendingTotal}" else "$openTasks"
        else -> null
    }

    QnScaffold(
        path = "/tasks",
        status = headerStatus,
        statusDetail = headerDetail,
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
                icon = Icons.Default.Person,
                label = "账户",
                onClick = onProfileClick
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
            val listState = rememberSaveable(saver = LazyListState.Saver) {
                LazyListState()
            }
            when {
                uiState.isLoading && uiState.tasks.isEmpty() -> {
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
                            "// 正在同步任务列表",
                            color = QnTextDim,
                            fontFamily = QnMono,
                            fontSize = 11.sp
                        )
                    }
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
                        icon = Icons.Default.Assignment,
                        title = "暂无查寝任务",
                        hint = "联系管理员分配任务后点击右上角刷新",
                        actionLabel = "刷新",
                        onAction = { viewModel.loadTasks() },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 12.dp, end = 12.dp,
                            top = 12.dp, bottom = 84.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item(key = "overall_progress") {
                            OverallStatsCard(
                                tasks = uiState.tasks,
                                progress = overallProgress,
                                checkedRooms = totalChecked,
                                totalRooms = totalRooms
                            )
                        }

                        if (uiState.cacheOnly || uiState.pendingTotal > 0) {
                            item(key = "local_state_banner") {
                                LocalStateBanner(
                                    pendingCount = uiState.pendingTotal,
                                    cacheMessage = uiState.error,
                                    onSyncClick = onSyncClick
                                )
                            }
                        }

                        item(key = "tasks_label") {
                            QnSectionLabel("TASKS / ${uiState.tasks.size}")
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

            // 统计入口（保留原 FAB 的「展开统计」行为，改成 2dp 细线方块）
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(14.dp),
                horizontalAlignment = Alignment.End
            ) {
                AnimatedVisibility(
                    visible = fabExpanded,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    Surface(
                        shape = QnRadius,
                        color = QnPanel,
                        border = BorderStroke(1.dp, QnLine),
                        shadowElevation = 0.dp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "TASKS ${uiState.tasks.size}",
                                color = QnTextMuted,
                                fontFamily = QnMono,
                                fontSize = 9.5.sp
                            )
                            QnAsciiProgress(progress = overallProgress, blocks = 8)
                            Text(
                                "CHECKED $totalChecked/$totalRooms",
                                color = QnTextDim,
                                fontFamily = QnMono,
                                fontSize = 9.5.sp
                            )
                        }
                    }
                }
                Surface(
                    shape = QnRadius,
                    color = QnPanel,
                    border = BorderStroke(1.dp, QnLine),
                    shadowElevation = 0.dp
                ) {
                    IconButton(
                        onClick = { fabExpanded = !fabExpanded },
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = if (fabExpanded) Icons.Default.Close else Icons.Default.BarChart,
                            contentDescription = "统计",
                            tint = QnAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        if (uiState.needsReauth) {
            AlertDialog(
                onDismissRequest = {
                    viewModel.clearReauth()
                    onLogout()
                },
                containerColor = QnPanel,
                shape = QnRadius,
                tonalElevation = 0.dp,
                title = {
                    Text(
                        "登录失效",
                        color = QnText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        "数据变动，需要重新登录",
                        color = QnTextMuted,
                        fontSize = 12.sp
                    )
                },
                confirmButton = {
                    QnPrimaryButton(
                        text = "确定",
                        onClick = {
                            viewModel.clearReauth()
                            onLogout()
                        }
                    )
                },
                dismissButton = {
                    QnGhostButton(
                        text = "返回登录",
                        onClick = {
                            viewModel.clearReauth()
                            onLogout()
                        }
                    )
                }
            )
        }
    }
}

/** 顶栏小图标按钮：统一 2dp 方块、无底色、暗色图标。 */
@Composable
private fun QnTopIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = QnTextMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** 本地优先提示条：有缓存/待同步时告诉用户“这些数字不完全来自服务端”。 */
@Composable
private fun LocalStateBanner(
    pendingCount: Int,
    cacheMessage: String?,
    onSyncClick: () -> Unit
) {
    val message = when {
        pendingCount > 0 && !cacheMessage.isNullOrBlank() -> "$cacheMessage · 含 $pendingCount 条待同步"
        pendingCount > 0 -> "含 $pendingCount 条待同步，为本地预估"
        else -> cacheMessage ?: "离线缓存"
    }
    QnCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (pendingCount > 0) {
                QnTag(text = "QUEUED $pendingCount", color = QnWarn)
            } else {
                QnTag(text = "CACHED", color = QnTextMuted)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                color = QnTextMuted,
                fontSize = 11.sp,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
            QnGhostButton(text = "同步", onClick = onSyncClick)
        }
    }
}

@Composable
private fun OverallStatsCard(
    tasks: List<TaskSummary>,
    progress: Float,
    checkedRooms: Int,
    totalRooms: Int
) {
    val completeTasks = tasks.count { it.totalRooms > 0 && it.checkedRooms >= it.totalRooms }
    val remainingRooms = (totalRooms - checkedRooms).coerceAtLeast(0)

    QnCard {
        QnSectionLabel("OVERVIEW")
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            QnStat(label = "TASKS", value = "${tasks.size}")
            QnStat(label = "DONE", value = "$completeTasks", valueColor = QnAccent)
            QnStat(
                label = "LEFT",
                value = "$remainingRooms",
                valueColor = if (remainingRooms > 0) QnWarn else QnText
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        QnAsciiProgress(progress = progress)
    }
}

@Composable
private fun TaskCard(task: TaskSummary, pending: Int, onClick: () -> Unit) {
    val progress = if (task.totalRooms > 0) task.checkedRooms.toFloat() / task.totalRooms else 0f
    val isCompleted = task.totalRooms > 0 && task.checkedRooms >= task.totalRooms
    val remaining = (task.totalRooms - task.checkedRooms).coerceAtLeast(0)
    val isCancelled = task.status.equals("CANCELLED", ignoreCase = true)

    val stateText = when {
        isCancelled -> "CANCELLED"
        pending > 0 -> "SYNC $pending"
        isCompleted -> "DONE"
        task.checkedRooms > 0 -> "SYNC $remaining"
        else -> "OPEN"
    }
    val stateColor = when {
        isCancelled -> QnTextDim
        pending > 0 -> QnWarn
        isCompleted -> QnAccent
        task.checkedRooms > 0 -> QnWarn
        else -> QnTextMuted
    }

    QnCard(onClick = onClick, contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = task.taskNo.ifBlank { "--" },
                color = QnAccent,
                fontFamily = QnMono,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(8.dp))
            QnTag(text = stateText, color = stateColor)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${task.checkedRooms}/${task.totalRooms}",
                color = QnTextMuted,
                fontFamily = QnMono,
                fontSize = 10.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = task.title.ifBlank { task.taskNo },
            color = QnText,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        val meta = listOfNotNull(
            task.buildingName.takeIf { it.isNotBlank() },
            (task.floorNumber + "F").takeIf { task.floorNumber.isNotBlank() },
            task.taskDate?.takeIf { it.isNotBlank() }
        ).joinToString(" · ")
        Text(
            text = meta.ifBlank { "--" },
            color = QnTextDim,
            fontFamily = QnMono,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            QnAsciiProgress(progress = progress, blocks = 8)
            Spacer(modifier = Modifier.weight(1f))
            if (!task.deadlineTime.isNullOrBlank()) {
                Text(
                    text = "ETA ${task.deadlineTime}",
                    color = QnTextDim,
                    fontFamily = QnMono,
                    fontSize = 9.5.sp,
                    maxLines = 1
                )
            }
        }
    }
}
