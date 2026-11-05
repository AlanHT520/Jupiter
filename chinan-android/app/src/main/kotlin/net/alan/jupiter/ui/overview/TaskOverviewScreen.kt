package net.alan.jupiter.ui.overview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.alan.jupiter.domain.model.FloorOverview
import net.alan.jupiter.domain.model.RoomSummary
import net.alan.jupiter.ui.components.EmptyState
import net.alan.jupiter.ui.components.ErrorState
import net.alan.jupiter.ui.theme.StatusChecked
import net.alan.jupiter.ui.theme.StatusUnchecked
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskOverviewScreen(
    taskId: String,
    taskNo: String,
    buildingName: String,
    onRoomClick: (String, String) -> Unit,
    onBackClick: () -> Unit,
    refreshTrigger: Int = 0,
    viewModel: TaskOverviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(taskId, refreshTrigger) {
        viewModel.loadOverview(taskId, taskNo, buildingName)
    }

    val totalRooms = uiState.floors.sumOf { it.rooms.size }
    val checkedRooms = uiState.floors.sumOf { f -> f.rooms.count { it.checked } }
    val progress = if (totalRooms > 0) checkedRooms.toFloat() / totalRooms else 0f

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(buildingName, fontWeight = FontWeight.ExtraBold)
                        Text(
                            "任务 $taskNo | 总进度 ${(progress * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
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
                            "加载房间列表...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                uiState.error != null -> {
                    ErrorState(
                        title = "加载失败",
                        message = uiState.error ?: "未知错误",
                        onRetry = { viewModel.loadOverview(taskId, taskNo, buildingName) },
                        modifier = Modifier.align(Alignment.Center),
                        icon = Icons.Default.CloudOff
                    )
                }
                uiState.floors.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Default.MeetingRoom,
                        title = "暂无房间数据",
                        hint = "请确认任务是否已分配房间，或联系管理员重新分配",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "progress_header") {
                            ProgressHeaderCard(
                                totalRooms = totalRooms,
                                checkedRooms = checkedRooms,
                                progress = progress
                            )
                        }

                        uiState.floors.forEach { floor ->
                            item(key = "floor_${floor.floorId}") {
                                FloorSection(floor = floor, onRoomClick = onRoomClick)
                            }
                        }

                        item(key = "bottom_spacer") {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressHeaderCard(totalRooms: Int, checkedRooms: Int, progress: Float) {
    val uncheckedRooms = totalRooms - checkedRooms

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant
        )
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
                    "检查进度",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    StatChip(
                        icon = Icons.Default.CheckCircle,
                        label = "已查 $checkedRooms",
                        tint = StatusChecked,
                        bgAlpha = 0.12f
                    )
                    StatChip(
                        icon = Icons.Default.RadioButtonUnchecked,
                        label = "未查 $uncheckedRooms",
                        tint = StatusUnchecked,
                        bgAlpha = 0.08f
                    )
                }
            }

            Box(
                modifier = Modifier.size(72.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 6.dp,
                    color = StatusChecked,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${(progress * 100).roundToInt()}%",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "$checkedRooms/$totalRooms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    bgAlpha: Float
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = tint.copy(alpha = bgAlpha)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = tint
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = tint
            )
        }
    }
}

@Composable
private fun FloorSection(floor: FloorOverview, onRoomClick: (String, String) -> Unit) {
    var expanded by remember { mutableStateOf(true) }
    val checkedCount = floor.rooms.count { it.checked }
    val totalCount = floor.rooms.size
    val floorProgress = if (totalCount > 0) checkedCount.toFloat() / totalCount else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = null
    ) {
        Column {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                shape = MaterialTheme.shapes.large
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ) {
                            Icon(
                                Icons.Default.Layers,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(7.dp)
                                    .fillMaxSize(),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "${floor.floorNumber}层",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "${totalCount} 间 | 已查 $checkedCount 间",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { floorProgress },
                            modifier = Modifier
                                .width(60.dp)
                                .height(5.dp)
                                .clip(MaterialTheme.shapes.small),
                            color = if (floorProgress >= 1f) StatusChecked else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(
                            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = if (expanded) "收起" else "展开",
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    floor.rooms.forEach { room ->
                        RoomCard(room = room, onClick = { onRoomClick(room.roomId, room.roomNumber) })
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun RoomCard(room: RoomSummary, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (room.checked)
            StatusChecked.copy(alpha = 0.04f)
        else
            MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = if (room.checked)
                        StatusChecked.copy(alpha = 0.1f)
                    else
                        StatusUnchecked.copy(alpha = 0.1f)
                ) {
                    Icon(
                        if (room.checked) Icons.Default.CheckCircle else Icons.Default.MeetingRoom,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxSize(),
                        tint = if (room.checked) StatusChecked else StatusUnchecked
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = room.roomNumber,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            "${room.studentCount} 人",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (room.checked) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                "已检查",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = StatusChecked
                            )
                        },
                        shape = MaterialTheme.shapes.small,
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = StatusChecked.copy(alpha = 0.1f)
                        )
                    )
                } else {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}