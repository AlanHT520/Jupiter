package net.alan.jupiter.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import net.alan.jupiter.domain.model.CheckStatus

@Entity(tableName = "pending_checks")
data class PendingCheckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: String,
    val roomId: String,
    val studentId: String,
    val checkStatusCode: Int,
    val remark: String?,
    val proofLocalPath: String?,
    val createdAt: Long = System.currentTimeMillis()
)