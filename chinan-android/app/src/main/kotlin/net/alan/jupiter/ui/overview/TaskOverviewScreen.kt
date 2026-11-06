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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import net.alan.jupiter.domain.model.FloorOverview
import net.alan.jupiter.domain.model.RoomSummary
import net.alan.jupiter.ui.components.QnAsciiProgress
import net.alan.jupiter.ui.components.QnCard
import net.alan.jupiter.ui.components.QnEmpty
import net.alan.jupiter.ui.components.QnError
import net.alan.jupiter.ui.components.QnMono
import net.alan.jupiter.ui.components.QnScaffold
import net.alan.jupiter.ui.components.QnSectionLabel
import net.alan.jupiter.ui.components.QnStat
import net.alan.jupiter.ui.components.QnStatus
import net.alan.jupiter.ui.components.QnTag
import net.alan.jupiter.ui.theme.QnAccent
import net.alan.jupiter.ui.theme.QnLine
import net.alan.jupiter.ui.theme.QnText
import net.alan.jupiter.ui.theme.QnTextDim
import net.alan.jupiter.ui.theme.QnTextMuted
import net.alan.jupiter.ui.theme.QnWarn

/**
 * 任务概览（/check/<building>）：进度卡（QnStat + ASCII 进度）+ 楼层/房间细线卡。
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

    val totalRooms = uiState.floors.sumOf { it.rooms.size }
    val checkedRooms = uiState.floors.sumOf { f -> f.rooms.count { it.checked } }
    val progress = if (totalRooms > 0) checkedRooms.toFloat() / totalRooms else 0f
    val path = "/check/${buildingName.ifBlank { taskNo.ifBlank { "--" } }}"

    val headerStatus = when {
        uiState.cacheOnly -> QnStatus.CACHED
        uiState.error != null -> QnStatus.FAILED
        uiState.isLoading && uiState.floors.isEmpty() -> QnStatus.RUNNING
        totalRooms > 0 && checkedRooms >= totalRooms -> QnStatus.OK
        checkedRooms > 0 -> QnStatus.SYNC
        else -> QnStatus.IDLE
    }
    val headerDetail = when {
        headerStatus == QnStatus.SYNC -> "$checkedRooms/$totalRooms"
        else -> null
    }

    QnScaffold(
        path = path,
        status = headerStatus,
        statusDetail = headerDetail,
        onBack = onBackClick,
        actions = {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = QnAccent
                )
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
                uiState.isLoading && uiState.floors.isEmpty() -> {
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
                            "// 正在加载房间列表",
                            color = QnTextDim,
                            fontFamily = QnMono,
                            fontSize = 11.sp
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
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 12.dp, end = 12.dp,
                            top = 12.dp, bottom = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item(key = "progress_header") {
                            ProgressHeaderCard(
                                totalRooms = totalRooms,
                                checkedRooms = checkedRooms,
                                progress = progress
                            )
                        }

                        if (uiState.cacheOnly) {
                            item(key = "cache_banner") {
                                QnCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        QnTag(text = "CACHED", color = QnTextMuted)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = uiState.error
                                                ?: "离线缓存，房间完成状态以本地记录为准",
                                            color = QnTextMuted,
                                            fontSize = 11.sp,
                                            maxLines = 2
                                        )
                                    }
                                }
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
private fun ProgressHeaderCard(totalRooms: Int, checkedRooms: Int, progress: Float) {
    val uncheckedRooms = (totalRooms - checkedRooms).coerceAtLeast(0)

    QnCard {
        QnSectionLabel("CHECK PROGRESS")
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            QnStat(label = "TOTAL", value = "$totalRooms")
            QnStat(label = "CHECKED", value = "$checkedRooms", valueColor = QnAccent)
            QnStat(
                label = "LEFT",
                value = "$uncheckedRooms",
                valueColor = if (uncheckedRooms > 0) QnWarn else QnText
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        QnAsciiProgress(progress = progress)
    }
}

@Composable
private fun FloorSection(floor: FloorOverview, onRoomClick: (String, String) -> Unit) {
    var expanded by remember { mutableStateOf(true) }
    val checkedCount = floor.rooms.count { it.checked }
    val totalCount = floor.rooms.size
    val floorProgress = if (totalCount > 0) checkedCount.toFloat() / totalCount else 0f
    val allChecked = totalCount > 0 && checkedCount >= totalCount

    QnCard(contentPadding = PaddingValues(0.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${floor.floorNumber}F",
                        color = QnAccent,
                        fontFamily = QnMono,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    QnTag(
                        text = if (allChecked) "DONE" else "OPEN",
                        color = if (allChecked) QnAccent else QnTextMuted
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$totalCount ROOMS · $checkedCount CHECKED",
                    color = QnTextDim,
                    fontFamily = QnMono,
                    fontSize = 9.5.sp
                )
            }
            QnAsciiProgress(progress = floorProgress, blocks = 6)
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = QnTextMuted,
                modifier = Modifier.size(18.dp)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                HorizontalDivider(color = QnLine, thickness = 1.dp)
                floor.rooms.forEach { room ->
                    RoomRow(room = room, onClick = { onRoomClick(room.roomId, room.roomNumber) })
                }
            }
        }
    }
}

@Composable
private fun RoomRow(room: RoomSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = room.roomNumber,
            color = QnText,
            fontFamily = QnMono,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(10.dp))
        QnTag(
            text = if (room.checked) "CHECKED" else "OPEN",
            color = if (room.checked) QnAccent else QnTextDim
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "${room.studentCount}P",
            color = QnTextMuted,
            fontFamily = QnMono,
            fontSize = 9.5.sp
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = QnTextDim,
            modifier = Modifier.size(16.dp)
        )
    }
}
