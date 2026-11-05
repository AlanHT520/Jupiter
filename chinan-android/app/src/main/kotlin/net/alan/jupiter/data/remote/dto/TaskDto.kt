package net.alan.jupiter.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PageResult<T>(
    val records: List<T> = emptyList(),
    val total: Long = 0,
    val page: Int = 0,
    val size: Int = 0,
    val totalPages: Int = 0
)

@Serializable
data class TaskDto(
    val id: String = "",
    val taskNo: String = "",
    val title: String = "",
    val status: String = "",
    val taskDate: String? = null,
    val buildingId: String = "",
    val buildingName: String = "",
    val floorId: String = "",
    val floorNumber: String = "",
    val roomNumbers: String? = null,
    val assignedCounselorId: String = "",
    val assignedCounselorName: String = "",
    val totalRooms: Int = 0,
    val checkedRooms: Int = 0,
    val deadlineTime: String? = null,
    val startTime: String? = null,
    val finishTime: String? = null,
    val createdAt: String? = null
)

@Serializable
data class TaskOverviewDto(
    val task: TaskDto? = null,
    val floor: FloorInfo? = null
)

@Serializable
data class FloorInfo(
    val floorId: String = "",
    val floorNumber: Int = 0,
    val floorName: String = "",
    val roomCount: Int = 0,
    val checkedRoomCount: Int = 0,
    val rooms: List<RoomInfo> = emptyList()
)

@Serializable
data class RoomInfo(
    val roomId: String = "",
    val roomNumber: String = "",
    val studentCount: Int = 0,
    val checked: Boolean = false
)