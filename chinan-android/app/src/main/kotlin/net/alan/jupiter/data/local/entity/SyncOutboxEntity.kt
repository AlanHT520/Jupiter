package net.alan.jupiter.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import net.alan.jupiter.domain.model.SyncState

/**
 * 上行队列（outbox）：一条记录 = 一个提交批次（task + room）。
 *
 * - `clientOpId` 唯一，是服务端去重的业务幂等键；重试复用同一个值；
 * - `payload` 是 JSON 化的 `List<StudentCheckItem>`，一次房间提交整批上行；
 * - ACK 后整行删除；失败则 retryCount+1，超过 3 次升为 FAILED 并保留。
 */
@Entity(
    tableName = "sync_outbox",
    indices = [
        Index(value = ["clientOpId"], unique = true, name = "index_sync_outbox_op"),
        Index(value = ["ownerId", "state"], name = "index_sync_outbox_owner_state"),
        Index(value = ["ownerId", "taskDate"], name = "index_sync_outbox_owner_date")
    ]
)
data class SyncOutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientOpId: String,
    val ownerId: String,
    val taskId: String,
    val roomId: String,
    /** 单学生提交时记录学生；整房批量提交时为 null（payload 里有完整列表）。 */
    val studentId: String? = null,
    /** 仅用于本地按时段筛选补交，不参与上行报文语义。 */
    val taskDate: String? = null,
    val payload: String,
    val state: String = SyncState.QUEUED.name,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
