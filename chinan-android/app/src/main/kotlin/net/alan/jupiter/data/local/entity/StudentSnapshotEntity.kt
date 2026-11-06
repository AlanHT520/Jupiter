package net.alan.jupiter.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import net.alan.jupiter.domain.model.CheckStatus
import net.alan.jupiter.domain.model.StudentDetail

/**
 * 学生快照：按任务 + 房间预取（不拉全楼），离线也能打开房间页。
 *
 * 主键含 `ownerId` + `taskId`：同一个学生在不同任务下各存一份，
 * 这样下行数据按任务覆盖时不会互相污染。
 */
@Entity(
    tableName = "student_snapshots",
    primaryKeys = ["ownerId", "taskId", "studentId"],
    indices = [
        Index(value = ["ownerId", "taskId"], name = "index_student_snapshots_owner_task"),
        Index(value = ["ownerId", "roomId"], name = "index_student_snapshots_owner_room")
    ]
)
data class StudentSnapshotEntity(
    val ownerId: String,
    val taskId: String,
    val studentId: String,
    val studentNo: String,
    val name: String,
    val gender: String,
    val className: String?,
    val roomId: String,
    val roomNumber: String,
    val bedNumber: String,
    val presetStatusCode: Int?,
    val statusCode: Int?,
    val statusSource: String?,
    val remark: String?,
    val proofFilePath: String?,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): StudentDetail = StudentDetail(
        id = studentId,
        studentNo = studentNo,
        name = name,
        gender = gender,
        roomNumber = roomNumber,
        bedNumber = bedNumber,
        className = className,
        presetStatus = CheckStatus.fromCode(presetStatusCode),
        currentStatus = CheckStatus.fromCode(statusCode),
        presetStatusFrom = statusSource,
        proofFilePath = proofFilePath,
        remark = remark
    )

    companion object {
        fun from(
            ownerId: String,
            taskId: String,
            roomId: String,
            student: StudentDetail,
            updatedAt: Long = System.currentTimeMillis()
        ) = StudentSnapshotEntity(
            ownerId = ownerId,
            taskId = taskId,
            studentId = student.id,
            studentNo = student.studentNo,
            name = student.name,
            gender = student.gender,
            className = student.className,
            roomId = roomId,
            roomNumber = student.roomNumber,
            bedNumber = student.bedNumber,
            presetStatusCode = student.presetStatus?.code,
            statusCode = student.currentStatus?.code,
            statusSource = student.presetStatusFrom,
            remark = student.remark,
            proofFilePath = student.proofFilePath,
            updatedAt = updatedAt
        )
    }
}
