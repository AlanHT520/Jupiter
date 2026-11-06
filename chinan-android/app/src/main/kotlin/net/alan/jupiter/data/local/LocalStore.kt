package net.alan.jupiter.data.local

import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import net.alan.jupiter.data.local.dao.CheckRecordDao
import net.alan.jupiter.data.local.dao.StudentSnapshotDao
import net.alan.jupiter.data.local.dao.SummaryDao
import net.alan.jupiter.data.local.dao.SyncOutboxDao
import net.alan.jupiter.data.local.dao.TaskPendingCount
import net.alan.jupiter.data.local.dao.TaskSnapshotDao
import net.alan.jupiter.data.local.entity.CheckRecordLocalEntity
import net.alan.jupiter.data.local.entity.StudentSnapshotEntity
import net.alan.jupiter.data.local.entity.SummarySnapshotEntity
import net.alan.jupiter.data.local.entity.SyncOutboxEntity
import net.alan.jupiter.data.local.entity.TaskSnapshotEntity
import net.alan.jupiter.data.local.entity.recordSync
import net.alan.jupiter.data.remote.dto.StudentCheckItem
import net.alan.jupiter.domain.model.CheckStatus
import net.alan.jupiter.domain.model.RoomSummary
import net.alan.jupiter.domain.model.StudentDetail
import net.alan.jupiter.domain.model.SyncState
import net.alan.jupiter.domain.model.TaskSummary
import net.alan.jupiter.domain.summary.SummaryAggregator
import net.alan.jupiter.domain.summary.SummaryCounts
import net.alan.jupiter.domain.summary.SummaryResult
import net.alan.jupiter.domain.summary.SummaryScope
import net.alan.jupiter.domain.summary.SummarySnapshotPayload
import net.alan.jupiter.domain.sync.SyncPolicy
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room 的唯一入口：所有本地读写都在这里做 owner（userId）分区与事务。
 *
 * 约定：
 * - `ownerId` 默认取当前登录 userId（[TokenManager.getOwnerId]），换账号看不到别人的记录；
 * - 写路径一律走 [ensureOwner]，顺带把迁移遗留的无主记录（ownerId=""）认领到当前账号；
 * - 读路径用 [ownerId]（不加写），Flow 在收集时解析 owner。
 */
@Singleton
class LocalStore @Inject constructor(
    private val db: AppDatabase,
    private val tokenManager: TokenManager,
    private val taskDao: TaskSnapshotDao,
    private val studentDao: StudentSnapshotDao,
    private val recordDao: CheckRecordDao,
    private val outboxDao: SyncOutboxDao,
    private val summaryDao: SummaryDao
) {

    companion object {
        private const val TAG = "LocalStore"

        /** 未登录 / 迁移遗留记录的分区键。 */
        const val LEGACY_OWNER = ""

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        private val itemsSerializer = ListSerializer(StudentCheckItem.serializer())

        fun encodeItems(items: List<StudentCheckItem>): String =
            json.encodeToString(itemsSerializer, items)

        fun decodeItems(payload: String): List<StudentCheckItem> =
            runCatching { json.decodeFromString(itemsSerializer, payload) }.getOrDefault(emptyList())
    }

    data class EnqueuedBatch(
        val clientOpId: String,
        val recordCount: Int,
        val taskDate: String?
    )

    data class LocalDataCounts(
        val records: Int = 0,
        val acked: Int = 0,
        val outbox: Int = 0,
        val pending: Int = 0
    )

    data class ClearedLocalData(
        val records: Int = 0,
        val outbox: Int = 0,
        val tasks: Int = 0,
        val students: Int = 0,
        val photos: Int = 0
    )

    // ---------------- owner ----------------

    suspend fun ownerId(): String = tokenManager.getOwnerId()

    /** 写路径入口：解析 owner 并认领迁移遗留记录。 */
    suspend fun ensureOwner(): String {
        val owner = ownerId()
        if (owner.isNotBlank()) claimLegacy(owner)
        return owner
    }

    /**
     * 迁移遗留（ownerId="" 的记录/队列）归属到当前账号。
     * 逐条检查是否已有同主键的新记录：有则保留新记录，丢弃旧草稿（避免覆盖已 ACK 数据）。
     */
    private suspend fun claimLegacy(ownerId: String) {
        if (ownerId.isBlank()) return
        db.withTransaction {
            val legacyRecords = recordDao.getLegacy()
            if (legacyRecords.isNotEmpty()) {
                legacyRecords.forEach { row ->
                    val exists = recordDao.getByStudent(ownerId, row.taskId, row.roomId, row.studentId) != null
                    if (!exists) recordDao.upsert(row.copy(ownerId = ownerId))
                }
                recordDao.deleteLegacy()
            }

            val legacyOutbox = outboxDao.getLegacy()
            if (legacyOutbox.isNotEmpty()) {
                legacyOutbox.forEach { row ->
                    val exists = outboxDao.getByClientOpId(ownerId, row.clientOpId) != null
                    if (!exists) outboxDao.insert(row.copy(id = 0, ownerId = ownerId))
                }
                outboxDao.deleteLegacy()
            }
        }
    }

    // ---------------- 任务快照 ----------------

    suspend fun cacheTasks(tasks: List<TaskSummary>) {
        if (tasks.isEmpty()) return
        val owner = ensureOwner()
        val now = System.currentTimeMillis()
        db.withTransaction {
            taskDao.upsertAll(tasks.map { TaskSnapshotEntity.from(owner, it, now) })
        }
    }

    fun observeTasks(): Flow<List<TaskSummary>> = flow {
        emitAll(taskDao.observeByOwner(ownerId()).map { rows -> rows.map { it.toDomain() } })
    }

    suspend fun taskSnapshot(taskId: String): TaskSnapshotEntity? =
        taskDao.getByTaskId(ownerId(), taskId)

    suspend fun taskDate(taskId: String): String? = taskDao.getByTaskId(ownerId(), taskId)?.taskDate

    // ---------------- 学生快照 ----------------

    suspend fun cacheStudents(taskId: String, roomId: String, students: List<StudentDetail>) {
        if (students.isEmpty()) return
        val owner = ensureOwner()
        val now = System.currentTimeMillis()
        db.withTransaction {
            studentDao.upsertAll(students.map { StudentSnapshotEntity.from(owner, taskId, roomId, it, now) })
        }
    }

    suspend fun cachedStudents(taskId: String): List<StudentDetail> =
        studentDao.getByTask(ownerId(), taskId).map { it.toDomain() }

    suspend fun cachedRoomStudents(taskId: String, roomId: String): List<StudentDetail> =
        studentDao.getByRoom(ownerId(), taskId, roomId).map { it.toDomain() }

    /** 离线概览：用已缓存学生 + 本地记录还原房间列表（没有楼层信息时由调用方补）。 */
    suspend fun cachedRooms(taskId: String): List<RoomSummary> {
        val owner = ownerId()
        val students = studentDao.getByTask(owner, taskId)
        if (students.isEmpty()) return emptyList()
        val checkedRoomIds = recordDao.getByTask(owner, taskId)
            .filter { it.recordSync != SyncState.LOCAL_DRAFT }
            .map { it.roomId }
            .toSet()
        return students
            .groupBy { it.roomId }
            .map { (roomId, list) ->
                RoomSummary(
                    roomId = roomId,
                    roomNumber = list.first().roomNumber,
                    studentCount = list.size,
                    checked = roomId in checkedRoomIds
                )
            }
            .sortedBy { it.roomNumber }
    }

    // ---------------- 本地查寝记录 ----------------

    suspend fun roomRecords(taskId: String, roomId: String): List<CheckRecordLocalEntity> =
        recordDao.getByRoom(ownerId(), taskId, roomId)

    fun observeRoomRecords(taskId: String, roomId: String): Flow<List<CheckRecordLocalEntity>> = flow {
        emitAll(recordDao.observeByRoom(ownerId(), taskId, roomId))
    }

    suspend fun taskRecords(taskId: String): List<CheckRecordLocalEntity> =
        recordDao.getByTask(ownerId(), taskId)

    suspend fun roomPendingCount(taskId: String, roomId: String): Int =
        recordDao.countPendingInRoom(ownerId(), taskId, roomId)

    fun observePendingCount(): Flow<Int> = flow {
        emitAll(recordDao.observePendingCount(ownerId()))
    }

    fun observePendingByTask(): Flow<Map<String, Int>> = flow {
        emitAll(recordDao.observePendingByTask(ownerId()).map { rows ->
            rows.associate { row: TaskPendingCount -> row.taskId to row.pending }
        })
    }

    fun observeRecordCount(): Flow<Int> = flow {
        emitAll(recordDao.observeTotalCount(ownerId()))
    }

    fun observeAckedCount(): Flow<Int> = flow {
        emitAll(recordDao.observeAckedCount(ownerId()))
    }

    /**
     * 草稿落盘（LOCAL_DRAFT）。队列中的记录（QUEUED/FAILED/IN_FLIGHT）被编辑时
     * 保持队列状态，并同步刷新 outbox payload，避免上行旧状态。
     */
    suspend fun saveCheckDraft(
        taskId: String,
        roomId: String,
        studentId: String,
        statusCode: Int,
        remark: String?,
        proofPath: String?,
        proofFilePath: String?
    ) {
        val owner = ensureOwner()
        val now = System.currentTimeMillis()
        db.withTransaction {
            val existing = recordDao.getByStudent(owner, taskId, roomId, studentId)
            val keepQueued = existing != null && existing.recordSync in SyncState.PENDING
            val state = if (keepQueued) existing!!.recordSync else SyncState.LOCAL_DRAFT
            recordDao.upsert(
                CheckRecordLocalEntity(
                    ownerId = owner,
                    taskId = taskId,
                    roomId = roomId,
                    studentId = studentId,
                    taskDate = existing?.taskDate ?: taskDao.getByTaskId(owner, taskId)?.taskDate,
                    statusCode = statusCode,
                    remark = remark,
                    proofPath = proofPath,
                    proofFilePath = proofFilePath,
                    syncState = state.name,
                    clientOpId = if (state == SyncState.LOCAL_DRAFT) null else existing?.clientOpId,
                    late = existing?.late ?: false,
                    updatedAt = now,
                    submittedAt = if (state == SyncState.LOCAL_DRAFT) null else existing?.submittedAt,
                    ackedAt = if (state == SyncState.LOCAL_DRAFT) null else existing?.ackedAt,
                    lastError = if (state == SyncState.LOCAL_DRAFT) null else existing?.lastError
                )
            )
            if (state == SyncState.QUEUED || state == SyncState.FAILED) {
                refreshOutboxPayload(owner, taskId, roomId, now)
            }
        }
    }

    private suspend fun refreshOutboxPayload(
        owner: String,
        taskId: String,
        roomId: String,
        now: Long
    ) {
        val row = outboxDao.getSubmittableByRoom(owner, taskId, roomId) ?: return
        val records = recordDao.getByRoom(owner, taskId, roomId)
            .filter { it.recordSync in SyncState.PENDING }
        if (records.isEmpty()) return
        outboxDao.updatePayload(row.clientOpId, encodeItems(records.map { it.toCheckItem() }), now)
    }

    /**
     * 提交入队：一个房间 = 一个批次 = 一个 clientOpId，房间内所有学生记录共用它。
     *
     * 先写 CheckRecordLocal(QUEUED) + SyncOutbox(QUEUED)，随后由调用方立即尝试上行；
     * 失败时保持 QUEUED，不抛给 UI。重试复用同一个 clientOpId。
     */
    suspend fun enqueueSubmit(
        taskId: String,
        roomId: String,
        items: List<StudentCheckItem>,
        proofPaths: Map<String, String?> = emptyMap(),
        singleStudentId: String? = null
    ): EnqueuedBatch {
        require(items.isNotEmpty()) { "提交内容为空" }
        val owner = ensureOwner()
        val now = System.currentTimeMillis()
        val taskDate = taskDao.getByTaskId(owner, taskId)?.taskDate
        val today = LocalDate.now().toString()
        val late = !taskDate.isNullOrBlank() && taskDate < today
        val clientOpId = SyncPolicy.newClientOpId()
        val payload = encodeItems(items)

        db.withTransaction {
            items.forEach { item ->
                val existing = recordDao.getByStudent(owner, taskId, roomId, item.studentId)
                recordDao.upsert(
                    CheckRecordLocalEntity(
                        ownerId = owner,
                        taskId = taskId,
                        roomId = roomId,
                        studentId = item.studentId,
                        taskDate = taskDate ?: existing?.taskDate,
                        statusCode = CheckStatus.fromName(item.checkStatus)?.code
                            ?: existing?.statusCode
                            ?: CheckStatus.PRESENT.code,
                        remark = item.remark,
                        proofPath = proofPaths[item.studentId] ?: existing?.proofPath,
                        proofFilePath = item.proofFile ?: existing?.proofFilePath,
                        syncState = SyncState.QUEUED.name,
                        clientOpId = clientOpId,
                        late = late,
                        updatedAt = now,
                        submittedAt = null,
                        ackedAt = null,
                        lastError = null
                    )
                )
            }
            outboxDao.insert(
                SyncOutboxEntity(
                    clientOpId = clientOpId,
                    ownerId = owner,
                    taskId = taskId,
                    roomId = roomId,
                    studentId = singleStudentId ?: items.singleOrNull()?.studentId,
                    taskDate = taskDate,
                    payload = payload,
                    state = SyncState.QUEUED.name,
                    retryCount = 0,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
        Log.d(TAG, "enqueue SUBMIT_CHECK task=$taskId room=$roomId op=$clientOpId items=${items.size} late=$late")
        return EnqueuedBatch(clientOpId = clientOpId, recordCount = items.size, taskDate = taskDate)
    }

    // ---------------- outbox / 同步状态 ----------------

    suspend fun outboxForSync(includeFailed: Boolean): List<SyncOutboxEntity> {
        val states = if (includeFailed) {
            listOf(SyncState.QUEUED.name, SyncState.FAILED.name)
        } else {
            listOf(SyncState.QUEUED.name)
        }
        return outboxDao.getByStates(ownerId(), states)
    }

    suspend fun outboxRow(ownerId: String, clientOpId: String): SyncOutboxEntity? =
        outboxDao.getByClientOpId(ownerId, clientOpId)

    fun observeOutbox(): Flow<List<SyncOutboxEntity>> = flow {
        emitAll(outboxDao.observeByOwner(ownerId()))
    }

    fun observePendingOutboxCount(): Flow<Int> = flow {
        emitAll(outboxDao.observePendingCount(ownerId()))
    }

    fun observeFailedOutboxCount(): Flow<Int> = flow {
        emitAll(outboxDao.observeFailedCount(ownerId()))
    }

    suspend fun pendingOutboxCount(): Int = outboxDao.countPending(ownerId())

    /** ACK：记录置 ACKED、outbox 删行；此后以下行数据为准。 */
    suspend fun markAcked(ownerId: String, clientOpId: String) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            recordDao.markAcked(ownerId, clientOpId, now)
            outboxDao.deleteByClientOpId(clientOpId)
        }
        Log.d(TAG, "ACK op=$clientOpId")
    }

    /**
     * 失败：retryCount+1；≤3 保持 QUEUED，>3 升 FAILED（记录同步标 FAILED 但保留本地数据）。
     */
    suspend fun markFailure(ownerId: String, clientOpId: String, error: String): SyncState {
        val now = System.currentTimeMillis()
        var next = SyncState.QUEUED
        db.withTransaction {
            val row = outboxDao.getByClientOpId(ownerId, clientOpId) ?: return@withTransaction
            val retryCount = row.retryCount + 1
            next = SyncPolicy.nextStateAfterFailure(retryCount)
            val message = error.take(400)
            outboxDao.recordFailure(clientOpId, next.name, message, now)
            recordDao.updateStateForClientOp(ownerId, clientOpId, next.name, message, now)
        }
        Log.w(TAG, "FAIL op=$clientOpId state=$next error=$error")
        return next
    }

    // ---------------- 总汇总 ----------------

    suspend fun localSummary(scope: SummaryScope): SummaryResult {
        val owner = ownerId()
        val asOf = System.currentTimeMillis()
        val result = SummaryAggregator.aggregate(scope, summaryDao.summaryRows(owner), asOf)
        if (owner.isNotBlank()) {
            summaryDao.upsert(
                SummarySnapshotEntity(
                    ownerId = owner,
                    scope = scope.code,
                    asOf = asOf,
                    countsJson = json.encodeToString(SummaryCounts.serializer(), result.counts),
                    itemsJson = json.encodeToString(
                        SummarySnapshotPayload.serializer(),
                        result.toPayload()
                    ),
                    pendingCount = result.pending,
                    taskCount = result.taskCount,
                    studentCount = result.studentCount
                )
            )
        }
        return result
    }

    suspend fun summarySnapshot(scope: SummaryScope): SummarySnapshotEntity? =
        summaryDao.get(ownerId(), scope.code)

    // ---------------- 数据统计 / 清除 ----------------

    suspend fun localDataCounts(): LocalDataCounts {
        val owner = ownerId()
        return LocalDataCounts(
            records = recordDao.countByOwner(owner),
            acked = recordDao.getByStates(owner, listOf(SyncState.ACKED.name)).size,
            outbox = outboxDao.count(owner),
            pending = outboxDao.countPending(owner)
        )
    }

    /**
     * 设置页「清除本地数据」：记录、队列、快照、汇总、照片文件全部删除。
     * 退出登录**不会**走这里（退出只清 token + 停同步 worker）。
     */
    suspend fun clearAllLocalData(): ClearedLocalData {
        val proofPaths = recordDao.getAllProofPaths()
        var records = 0
        var outbox = 0
        var tasks = 0
        var students = 0
        db.withTransaction {
            records = recordDao.countAll()
            outbox = outboxDao.countAll()
            tasks = taskDao.countAll()
            students = studentDao.countAll()
            recordDao.deleteAll()
            outboxDao.deleteAll()
            taskDao.deleteAll()
            studentDao.deleteAll()
            summaryDao.deleteAll()
        }
        val photos = deletePhotoFiles(proofPaths)
        Log.i(TAG, "cleared local data: records=$records outbox=$outbox tasks=$tasks students=$students photos=$photos")
        return ClearedLocalData(
            records = records,
            outbox = outbox,
            tasks = tasks,
            students = students,
            photos = photos
        )
    }

    private suspend fun deletePhotoFiles(paths: List<String>): Int = withContext(Dispatchers.IO) {
        paths.asSequence()
            .filter { it.isNotBlank() }
            .distinct()
            .count { path -> runCatching { File(path).delete() }.getOrDefault(false) }
    }
}

/** 本地记录 → 上行报文条目（preset 信息不下发时按手动处理）。 */
fun CheckRecordLocalEntity.toCheckItem(): StudentCheckItem = StudentCheckItem(
    studentId = studentId,
    checkStatus = CheckStatus.fromCode(statusCode)?.name ?: CheckStatus.UNKNOWN.name,
    presetStatus = null,
    presetOverridden = null,
    statusSource = "KE",
    remark = remark,
    proofFile = proofFilePath
)
