package net.alan.jupiter.ui.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.alan.jupiter.domain.model.TaskSummary
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
                            "联系管理员分配任务后下拉刷新",
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
        border = null
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
    val isCompleted = task.checkedRooms >= task.totalRooms && task.totalRooms > 0
    val progressPercent = (progress * 100).roundToInt()
    val remaining = task.totalRooms - task.checkedRooms

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = MaterialTheme.shapes.small,
                        color = if (isCompleted)
                            StatusChecked.copy(alpha = 0.1f)
                        else
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ) {
                        Icon(
                            if (isCompleted) Icons.Default.CheckCircle else Icons.Default.Business,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(8.dp)
                                .fillMaxSize(),
                            tint = if (isCompleted) StatusChecked else MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = task.taskNo,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${task.buildingName} ${task.floorNumber}层",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            if (isCompleted) "已完成" else "$remaining 间待查",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            if (isCompleted) Icons.Default.CheckCircle else Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isCompleted) StatusChecked else MaterialTheme.colorScheme.primary
                        )
                    },
                    shape = MaterialTheme.shapes.small,
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (isCompleted)
                            StatusChecked.copy(alpha = 0.1f)
                        else
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(MaterialTheme.shapes.small),
                    color = if (isCompleted) StatusChecked else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "$progressPercent%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isCompleted) StatusChecked else MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = StatusChecked
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        "已查 ${task.checkedRooms}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = StatusUnchecked
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        "剩余 $remaining",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (task.deadlineTime != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Event,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            task.deadlineTime,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}