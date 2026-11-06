package net.alan.jupiter.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import net.alan.jupiter.domain.model.SyncState

/**
 * 本地查寝记录：提交/查寝的唯一事实来源（local-first）。
 *
 * 写路径：房间页改状态 → LOCAL_DRAFT；点提交 → QUEUED + SyncOutbox；
 * 服务端 ACK → ACKED；重试超限 → FAILED（保留人工入口）。
 *
 * 约束：
 * - 永久保留，退出登录不删，只有设置页「清除本地数据」才删；
 * - 按 `ownerId` 分区（默认为当前登录 userId）；
 * - 下行数据不覆盖 QUEUED / FAILED / IN_FLIGHT，只有 ACKED 之后才以服务端为准。
 */
@Entity(
    tableName = "check_records_local",
    primaryKeys = ["ownerId", "taskId", "roomId", "studentId"],
    indices = [
        Index(value = ["ownerId", "syncState"], name = "index_check_records_owner_state"),
        Index(value = ["ownerId", "clientOpId"], name = "index_check_records_owner_op"),
        Index(value = ["ownerId", "taskDate"], name = "index_check_records_owner_date")
    ]
)
data class CheckRecordLocalEntity(
    val ownerId: String,
    val taskId: String,
    val roomId: String,
    val studentId: String,
    /** 服务端发布/查寝日期，只用于本地按时段筛选，客户端不改写。 */
    val taskDate: String? = null,
    val statusCode: Int,
    val remark: String? = null,
    /** 本地照片路径（清除数据时一并删除）。 */
    val proofPath: String? = null,
    /** 服务端证明文件相对路径（上传成功后才有）。 */
    val proofFilePath: String? = null,
    val syncState: String = SyncState.LOCAL_DRAFT.name,
    /** 本批次幂等键；重试复用同一个值，LOCAL_DRAFT 时为 null。 */
    val clientOpId: String? = null,
    /** 补交标记：taskDate 早于提交当天。 */
    val late: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    /** 服务端 ACK 时间（本地记录，服务端另存 submittedAt）。 */
    val submittedAt: Long? = null,
    val ackedAt: Long? = null,
    val lastError: String? = null
)
