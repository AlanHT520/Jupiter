package net.alan.jupiter.ui.overview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import net.alan.jupiter.domain.model.FloorOverview
import net.alan.jupiter.domain.model.RoomSummary
import net.alan.jupiter.ui.components.QnAvatar
import net.alan.jupiter.ui.components.QnCard
import net.alan.jupiter.ui.components.QnChip
import net.alan.jupiter.ui.components.QnDivider
import net.alan.jupiter.ui.components.QnEmpty
import net.alan.jupiter.ui.components.QnError
import net.alan.jupiter.ui.components.QnGroupLabel
import net.alan.jupiter.ui.components.QnListItem
import net.alan.jupiter.ui.components.QnNoticeCard
import net.alan.jupiter.ui.components.QnProgress
import net.alan.jupiter.ui.components.QnScaffold
import net.alan.jupiter.ui.components.QnStat
import net.alan.jupiter.ui.components.QnStatRow
import net.alan.jupiter.ui.components.QnTopIcon
import net.alan.jupiter.ui.components.QnTonalButton
import net.alan.jupiter.ui.theme.QnMono
import net.alan.jupiter.ui.theme.QnTone
import kotlin.math.roundToInt

/**
 * 任务概览（/tasks/{id}/overview）：进度卡 + 楼层折叠卡 + 房间行。
 * 从查寝页返回自动刷新（LifecycleEventObserver），展开态用 rememberSaveable 保活。
 */
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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadOverview(taskId, taskNo, buildingName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val totalRooms = uiState.floors.sumOf { it.rooms.size }
    val checkedRooms = uiState.floors.sumOf { f -> f.rooms.count { it.checked } }
    val progress = if (totalRooms > 0) checkedRooms.toFloat() / totalRooms else 0f
    val allDone = totalRooms > 0 && checkedRooms >= totalRooms
    val title = buildingName.ifBlank { taskNo.ifBlank { "任务概览" } }

    QnScaffold(
        title = title,
        onBack = onBackClick,
        actions = {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
            } else {
                QnTopIcon(
                    icon = Icons.Default.Refresh,
                    label = "刷新",
                    onClick = { viewModel.loadOverview(taskId, taskNo, buildingName) }
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
                uiState.isLoading && uiState.floors.isEmpty() -> {
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
                            text = "正在加载房间列表…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                uiState.error != null && uiState.floors.isEmpty() -> {
                    QnError(
                        title = "加载失败",
                        message = uiState.error ?: "未知错误",
                        onRetry = { viewModel.loadOverview(taskId, taskNo, buildingName) },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        icon = Icons.Default.CloudOff
                    )
                }

                uiState.floors.isEmpty() -> {
                    QnEmpty(
                        icon = Icons.Default.MeetingRoom,
                        title = "暂无房间数据",
                        hint = "请确认任务是否已分配房间，或联系管理员重新分配",
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
                        item(key = "progress_header") {
                            ProgressHeaderCard(
                                taskNo = taskNo,
                                buildingName = buildingName,
                                totalRooms = totalRooms,
                                checkedRooms = checkedRooms,
                                progress = progress,
                                allDone = allDone
                            )
                        }

                        if (uiState.cacheOnly) {
                            item(key = "cache_banner") {
                                QnNoticeCard(
                                    title = "当前显示本地缓存",
                                    text = uiState.error
                                        ?: "房间状态以本机记录为准，联网后会自动刷新",
                                    tone = QnTone.Warning
                                )
                            }
                        }

                        uiState.floors.forEach { floor ->
                            item(key = "floor_${floor.floorId}") {
                                FloorSection(floor = floor, onRoomClick = onRoomClick)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressHeaderCard(
    taskNo: String,
    buildingName: String,
    totalRooms: Int,
    checkedRooms: Int,
    progress: Float,
    allDone: Boolean
) {
    val uncheckedRooms = (totalRooms - checkedRooms).coerceAtLeast(0)

    QnCard(contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "查寝进度",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = listOfNotNull(
                        buildingName.takeIf { it.isNotBlank() },
                        taskNo.takeIf { it.isNotBlank() }
                    ).joinToString(" · ").ifBlank { "本任务房间" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            QnChip(
                text = if (allDone) "已完成" else "进行中",
                tone = if (allDone) QnTone.Success else QnTone.Warning
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        QnStatRow {
            QnStat(label = "总房间", value = "$totalRooms", modifier = Modifier.weight(1f))
            QnStat(
                label = "已查",
                value = "$checkedRooms",
                modifier = Modifier.weight(1f),
                valueColor = MaterialTheme.colorScheme.primary
            )
            QnStat(
                label = "未查",
                value = "$uncheckedRooms",
                modifier = Modifier.weight(1f),
                valueColor = if (uncheckedRooms > 0) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        QnProgress(progress = progress)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "完成 ${(progress * 100).roundToInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FloorSection(floor: FloorOverview, onRoomClick: (String, String) -> Unit) {
    var expanded by rememberSaveable(floor.floorId) { mutableStateOf(true) }
    val checkedCount = floor.rooms.count { it.checked }
    val totalCount = floor.rooms.size
    val floorProgress = if (totalCount > 0) checkedCount.toFloat() / totalCount else 0f
    val allChecked = totalCount > 0 && checkedCount >= totalCount

    QnCard(contentPadding = PaddingValues(0.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QnAvatar(
                initial = "${floor.floorNumber}",
                size = 40.dp,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${floor.floorNumber}F",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                QnProgress(
                    progress = floorProgress,
                    modifier = Modifier.fillMaxWidth(0.72f),
                    color = if (allChecked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$totalCount 间 · 已查 $checkedCount 间",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            QnChip(
                text = if (allChecked) "已完成" else "进行中",
                tone = if (allChecked) QnTone.Success else QnTone.Neutral
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                QnDivider()
                QnGroupLabel("房间（${floor.rooms.size}）", modifier = Modifier.padding(horizontal = 16.dp))
                floor.rooms.forEach { room ->
                    RoomRow(room = room, onClick = { onRoomClick(room.roomId, room.roomNumber) })
                }
            }
        }
    }
}

@Composable
private fun RoomRow(room: RoomSummary, onClick: () -> Unit) {
    QnListItem(
        title = room.roomNumber,
        monoTitle = true,
        meta = "${room.studentCount} 人 · " + if (room.checked) "本次已查" else "未查",
        onClick = onClick,
        trailing = {
            QnChip(
                text = if (room.checked) "已查" else "未查",
                tone = if (room.checked) QnTone.Success else QnTone.Neutral
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    )
}
