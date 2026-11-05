package net.alan.jupiter.ui.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.alan.jupiter.domain.model.TaskSummary
import net.alan.jupiter.ui.components.ChinanCard
import net.alan.jupiter.ui.components.StatusChip
import net.alan.jupiter.ui.theme.StatusChecked
import net.alan.jupiter.ui.theme.StatusUnchecked
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    onTaskClick: (String, String, String) -> Unit,
    onSettingsClick: () -> Unit,
    onProfileClick: () -> Unit,
    onLogout: () -> Unit,
    viewModel: TaskListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showRefreshHint by remember { mutableStateOf(false) }

    val totalChecked = uiState.tasks.sumOf { it.checkedRooms }
    val totalRooms = uiState.tasks.sumOf { it.totalRooms }
    val overallProgress = if (totalRooms > 0) totalChecked.toFloat() / totalRooms else 0f

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("查寝任务", fontWeight = FontWeight.ExtraBold)
                        Text(
                            "${uiState.tasks.size} 个任务 | 已查 $totalChecked/$totalRooms 间",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = { viewModel.loadTasks(); showRefreshHint = false }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = onProfileClick) {
                        Icon(Icons.Default.Person, contentDescription = "账户")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        floatingActionButton = {
            var fabExpanded by remember { mutableStateOf(false) }
            Box {
                AnimatedVisibility(
                    visible = fabExpanded,
                    enter = fadeIn() + androidx.compose.animation.scaleIn(),
                    exit = fadeOut() + androidx.compose.animation.scaleOut()
                ) {
                    Surface(
                        modifier = Modifier
                            .padding(bottom = 64.dp, end = 4.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "共 ${uiState.tasks.size} 个任务",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            LinearProgressIndicator(
                                progress = { overallProgress },
                                modifier = Modifier
                                    .width(60.dp)
                                    .height(6.dp),
                                color = StatusChecked,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "${(overallProgress * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = StatusChecked
                            )
                        }
                    }
                }

                FloatingActionButton(
                    onClick = { fabExpanded = !fabExpanded },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        if (fabExpanded) Icons.Default.Close else Icons.Default.BarChart,
                        contentDescription = "统计"
                    )
                }
            }
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
                uiState.isLoading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "加载任务列表中...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                uiState.error != null -> {
                    val errorMsg = uiState.error ?: "未知错误"
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier.size(64.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        ) {
                            Icon(
                                Icons.Default.CloudOff,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(14.dp)
                                    .fillMaxSize(),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            "连接失败",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            errorMsg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        FilledTonalButton(onClick = viewModel::loadTasks) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("重试")
                        }
                    }
                }
                uiState.tasks.isEmpty() && !uiState.isLoading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Icon(
                                Icons.Default.Assignment,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxSize(),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "暂无查寝任务",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "联系管理员分配任务后点击右上角刷新",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        FilledTonalButton(onClick = viewModel::loadTasks) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("刷新")
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item(key = "overall_progress") {
                            OverallProgressCard(
                                tasks = uiState.tasks,
                                progress = overallProgress
                            )
                        }

                        items(uiState.tasks, key = { it.id }) { task ->
                            TaskCard(task = task, onClick = {
                                onTaskClick(task.id, task.taskNo, task.buildingName)
                            })
                        }

                        item(key = "bottom_spacer") {
                            Spacer(modifier = Modifier.height(72.dp))
                        }
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
                title = { Text("登录失效") },
                text = { Text("数据变动，需要重新登录") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.clearReauth()
                        onLogout()
                    }) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        viewModel.clearReauth()
                        onLogout()
                    }) {
                        Text("返回登录")
                    }
                }
            )
        }
    }
}

@Composable
private fun OverallProgressCard(
    tasks: List<TaskSummary>,
    progress: Float
) {
    val completeTasks = tasks.count { it.checkedRooms >= it.totalRooms && it.totalRooms > 0 }
    val inProgressTasks = tasks.size - completeTasks

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "总体进度",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(StatusChecked)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "完成 $completeTasks",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "进行中 $inProgressTasks",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Box(
                modifier = Modifier.size(68.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 5.dp,
                    color = StatusChecked,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    "${(progress * 100).roundToInt()}%",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun TaskCard(task: TaskSummary, onClick: () -> Unit) {
    val progress = if (task.totalRooms > 0) task.checkedRooms.toFloat() / task.totalRooms else 0f
    val isCompleted = task.totalRooms > 0 && task.checkedRooms >= task.totalRooms
    val remaining = (task.totalRooms - task.checkedRooms).coerceAtLeast(0)
    val accent = if (isCompleted) StatusChecked else MaterialTheme.colorScheme.primary

    ChinanCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.12f),
                contentColor = accent
            ) {
                Icon(
                    if (isCompleted) Icons.Default.CheckCircle else Icons.Default.Business,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title.ifBlank { task.taskNo },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val meta = listOfNotNull(
                    task.buildingName.takeIf { it.isNotBlank() },
                    (task.floorNumber + "层").takeIf { task.floorNumber.isNotBlank() },
                    task.taskDate
                ).joinToString(" · ")
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            StatusChip(
                text = if (isCompleted) "已完成" else "待查 $remaining 间",
                color = accent,
                icon = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.Schedule
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(50)),
                color = accent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "${(progress * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "已查 ${task.checkedRooms}/${task.totalRooms}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!task.deadlineTime.isNullOrBlank()) {
                Text(
                    "截止 ${task.deadlineTime}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
        }
    }
}