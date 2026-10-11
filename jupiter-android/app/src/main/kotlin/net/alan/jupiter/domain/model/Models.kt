package net.alan.jupiter.domain.model

data class User(
    val username: String,
    val role: String,
    val token: String
)

data class TaskSummary(
    val id: String,
    val taskNo: String,
    val title: String,
    val buildingName: String,
    val floorNumber: String,
    val totalRooms: Int,
    val checkedRooms: Int,
    val status: String,
    val taskDate: String?,
    val deadlineTime: String?
)

data class TaskOverview(
    val taskId: String,
    val taskNo: String,
    val buildingName: String,
    val floors: List<FloorOverview>
)

data class FloorOverview(
    val floorId: String,
    val floorNumber: String,
    val rooms: List<RoomSummary>
)

data class RoomSummary(
    val roomId: String,
    val roomNumber: String,
    val studentCount: Int,
    val checked: Boolean
)

data class RoomDetail(
    val roomId: String,
    val roomNumber: String,
    val floorNumber: String,
    val students: List<StudentDetail>
)

data class StudentDetail(
    val id: String,
    val studentNo: String,
    val name: String,
    val gender: String,
    val roomNumber: String,
    val bedNumber: String,
    val className: String?,
    val presetStatus: CheckStatus?,
    val currentStatus: CheckStatus?,
    val presetStatusFrom: String?,
    val proofFilePath: String?,
    val remark: String?
)