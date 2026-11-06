package net.alan.jupiter.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import net.alan.jupiter.domain.model.TaskSummary

/**
 * 任务快照：收到任务即落库，离线可查。
 *
 * 主键带上 `ownerId`：本地数据按 userId 分区，换账号登录看不到别人的任务。
 * `taskDate` 是服务端发布/查寝日期，客户端任何路径都不改写它。
 */
@Entity(
    tableName = "task_snapshots",
    primaryKeys = ["ownerId", "taskId"],
    indices = [
        Index(value = ["ownerId", "taskDate"], name = "index_task_snapshots_owner_date")
    ]
)
data class TaskSnapshotEntity(
    val ownerId: String,
    val taskId: String,
    val taskNo: String,
    val title: String,
    val buildingName: String,
    val floorNumber: String,
    val taskDate: String?,
    val deadlineTime: String?,
    val status: String,
    val totalRooms: Int,
    val checkedRooms: Int,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): TaskSummary = TaskSummary(
        id = taskId,
        taskNo = taskNo,
        title = title,
        buildingName = buildingName,
        floorNumber = floorNumber,
        totalRooms = totalRooms,
        checkedRooms = checkedRooms,
        status = status,
        taskDate = taskDate,
        deadlineTime = deadlineTime
    )

    companion object {
        fun from(ownerId: String, task: TaskSummary, updatedAt: Long = System.currentTimeMillis()) =
            TaskSnapshotEntity(
                ownerId = ownerId,
                taskId = task.id,
                taskNo = task.taskNo,
                title = task.title,
                buildingName = task.buildingName,
                floorNumber = task.floorNumber,
                taskDate = task.taskDate,
                deadlineTime = task.deadlineTime,
                status = task.status,
                totalRooms = task.totalRooms,
                checkedRooms = task.checkedRooms,
                updatedAt = updatedAt
            )
    }
}
