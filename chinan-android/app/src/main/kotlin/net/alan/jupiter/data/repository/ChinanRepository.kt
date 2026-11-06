package net.alan.jupiter.data.repository

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeoutOrNull
import net.alan.jupiter.data.local.LocalStore
import net.alan.jupiter.data.local.TokenManager
import net.alan.jupiter.data.local.entity.CheckRecordLocalEntity
import net.alan.jupiter.data.local.entity.SyncOutboxEntity
import net.alan.jupiter.data.local.entity.recordSync
import net.alan.jupiter.data.remote.dto.StudentCheckItem
import net.alan.jupiter.data.remote.dto.StudentDto
import net.alan.jupiter.data.remote.dto.TaskDto
import net.alan.jupiter.data.remote.dto.TaskOverviewDto
import net.alan.jupiter.data.remote.tcp.AuthExpiredException
import net.alan.jupiter.data.remote.tcp.TcpClient
import net.alan.jupiter.data.sync.NetworkMonitor
import net.alan.jupiter.data.sync.SyncEngine
import net.alan.jupiter.data.sync.SyncScheduler
import net.alan.jupiter.domain.model.CheckStatus
import net.alan.jupiter.domain.model.FloorOverview
import net.alan.jupiter.domain.model.RoomSummary
import net.alan.jupiter.domain.model.StudentDetail
import net.alan.jupiter.domain.model.SyncState
import net.alan.jupiter.domain.model.TaskOverview
import net.alan.jupiter.domain.model.TaskSummary
import net.alan.jupiter.domain.model.User
import net.alan.jupiter.domain.summary.SummaryResult
import net.alan.jupiter.domain.summary.SummaryScope
import net.alan.jupiter.util.Resource
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ChinanRepository"

/**
 * 数据仓库（local-first）：
 *
 * - 下行：TCP 成功 → 一个事务内 upsert 任务/学生快照 → 通知 UI（Flow）；
 * - 上行：先写 CheckRecordLocal(QUEUED) + SyncOutbox，再立即尝试网络；
 *   超时/失败/离线一律不抛给 UI，由 WorkManager 在联网后重试；
 * - 退出登录只清 token 与同步 worker，本地记录/队列保留；
 * - 所有本地读写按 ownerId（当前 userId）分区。
 */
@Singleton
class ChinanRepository @Inject constructor(
    private val tcpClient: TcpClient,
    private val tokenManager: TokenManager,
    private val localStore: LocalStore,
    private val syncEngine: SyncEngine,
    private val syncScheduler: SyncScheduler,
    private val networkMonitor: NetworkMonitor
) {

    companion object {
        /** 即时提交最长等待时间：超时就直接进队列，不阻塞 UI。 */
        private const val IMMEDIATE_SUBMIT_TIMEOUT_MS = 20_000L
    }

    /**
     * 提交结果。
     * @param queued true = 服务端还没 ACK，记录已进 outbox，等待自动重试
     */
    data class SubmitOutcome(
        val queued: Boolean,
        val clientOpId: String,
        val message: String? = null,
        val needsReauth: Boolean = false
    )

    // ---------------- 登录 / 会话 ----------------

    suspend fun login(username: String, password: String): Resource<User> = try {
        val data = tcpClient.login(username, password)
        tokenManager.saveLoginInfo(
            token = data.token,
            username = data.username,
            role = data.role,
            name = data.name,
            phone = data.phone,
            studentNo = data.studentNo,
            className = data.className,
            departmentName = data.departmentName,
            userId = data.userId
        )
        // 迁移遗留（ownerId 为空）的记录归属到当前账号
        localStore.ensureOwner()
        syncScheduler.ensureScheduled()
        syncScheduler.requestImmediate()
        Resource.Success(User(data.username, data.role, data.token))
    } catch (e: AuthExpiredException) {
        logout()
        Resource.Unauthorized(e.message ?: "数据变动，需要重新登录")
    } catch (e: Exception) {
        Log.w(TAG, "登录失败", e)
        Resource.Error("连接失败，请检查服务器地址和网络设置")
    }

    /**
     * 退出登录：只清 token 并停同步 worker。
     * 本地查寝记录、outbox、照片全部保留（换回同一账号仍可查、可继续补交）。
     */
    suspend fun logout() {
        syncScheduler.cancelAll()
        tcpClient.disconnect()
        tokenManager.clear()
    }

    suspend fun getToken(): String? = tokenManager.getToken()

    /** 修改密码成功后 tokenVersion 失效，清 token 让用户重新登录；本地记录保留。 */
    suspend fun changePassword(oldPassword: String, newPassword: String) {
        val token = tokenManager.getToken().orEmpty()
        tcpClient.changePassword(token, oldPassword, newPassword)
        logout()
    }

    // ---------------- 任务列表 ----------------

    suspend fun getTasks(): Resource<List<TaskSummary>> = try {
        val token = tokenManager.getToken().orEmpty()
        val result = tcpClient.getTasks(token)
        val tasks = result.records.map { it.toDomain() }
        localStore.cacheTasks(tasks)
        Resource.Success(tasks)
    } catch (e: AuthExpiredException) {
        logout()
        Resource.Unauthorized(e.message ?: "数据变动，需要重新登录")
    } catch (e: Exception) {
        Log.w(TAG, "获取任务列表失败", e)
        Resource.Error("加载失败，请点击刷新重试")
    }

    /** 任务列表优先读本地（离线可查），下拉刷新再走网络补齐。 */
    fun observeTasks(): Flow<List<TaskSummary>> = localStore.observeTasks()

    fun observePendingByTask(): Flow<Map<String, Int>> = localStore.observePendingByTask()

    fun observePendingCount(): Flow<Int> = localStore.observePendingCount()

    // ---------------- 任务概览 ----------------

    suspend fun getTaskOverview(taskId: String): Resource<TaskOverview> = try {
        val token = tokenManager.getToken().orEmpty()
        val result = tcpClient.getTaskOverview(token, taskId)
        val overview = result.toDomain()
        // 服务端还没记账的本地已查房间，先按本地状态显示（● QUEUED）
        val localCheckedRoomIds = localStore.taskRecords(taskId)
            .filter { it.recordSync != SyncState.LOCAL_DRAFT }
            .map { it.roomId }
            .toSet()
        val merged = if (localCheckedRoomIds.isEmpty()) {
            overview
        } else {
            overview.copy(
                floors = overview.floors.map { floor ->
                    floor.copy(
                        rooms = floor.rooms.map { room ->
                            if (room.roomId in localCheckedRoomIds) room.copy(checked = true) else room
                        }
                    )
                }
            )
        }
        Resource.Success(merged)
    } catch (e: AuthExpiredException) {
        logout()
        Resource.Unauthorized(e.message ?: "数据变动，需要重新登录")
    } catch (e: Exception) {
        Log.w(TAG, "获取任务概览失败", e)
        Resource.Error("加载失败，请返回重试")
    }

    /** 离线回退：用已缓存学生 + 本地记录还原概览；没有缓存返回 null。 */
    suspend fun cachedOverview(taskId: String): TaskOverview? {
        val snapshot = localStore.taskSnapshot(taskId) ?: return null
        val rooms: List<RoomSummary> = localStore.cachedRooms(taskId)
        if (rooms.isEmpty()) return null
        return TaskOverview(
            taskId = taskId,
            taskNo = snapshot.taskNo,
            buildingName = snapshot.buildingName,
            floors = listOf(
                FloorOverview(
                    floorId = "local",
                    floorNumber = snapshot.floorNumber.ifBlank { "--" },
                    rooms = rooms
                )
            )
        )
    }

    // ---------------- 房间 / 查寝 ----------------

    suspend fun getRoomStudents(taskId: String, roomId: String): Resource<List<StudentDetail>> = try {
        val token = tokenManager.getToken().orEmpty()
        val result = tcpClient.getRoomStudents(token, taskId, roomId)
        val students = result.map { it.toDomain() }
        localStore.cacheStudents(taskId, roomId, students)
        Resource.Success(students)
    } catch (e: AuthExpiredException) {
        logout()
        Resource.Unauthorized(e.message ?: "数据变动，需要重新登录")
    } catch (e: Exception) {
        Log.w(TAG, "获取学生列表失败", e)
        Resource.Error("加载失败，请返回重试")
    }

    suspend fun cachedRoomStudents(taskId: String, roomId: String): List<StudentDetail> =
        localStore.cachedRoomStudents(taskId, roomId)

    suspend fun roomRecords(taskId: String, roomId: String): List<CheckRecordLocalEntity> =
        localStore.roomRecords(taskId, roomId)

    fun observeRoomRecords(taskId: String, roomId: String): Flow<List<CheckRecordLocalEntity>> =
        localStore.observeRoomRecords(taskId, roomId)

    suspend fun roomPendingCount(taskId: String, roomId: String): Int =
        localStore.roomPendingCount(taskId, roomId)

    suspend fun saveCheckDraft(
        taskId: String,
        roomId: String,
        studentId: String,
        statusCode: Int,
        remark: String?,
        proofPath: String?,
        proofFilePath: String?
    ) {
        localStore.saveCheckDraft(
            taskId = taskId,
            roomId = roomId,
            studentId = studentId,
            statusCode = statusCode,
            remark = remark,
            proofPath = proofPath,
            proofFilePath = proofFilePath
        )
    }

    /**
     * 提交查寝（本地优先）：
     * 1. 先写 CheckRecordLocal(QUEUED) + SyncOutbox（一个房间 = 一个 clientOpId）；
     * 2. 在线且已登录 → 立即尝试提交，成功即 ACKED 并删除 outbox 行；
     * 3. 超时/失败/离线 → 保持 QUEUED，交给 WorkManager 自动重试，不抛异常给 UI。
     */
    suspend fun submitCheck(
        taskId: String,
        roomId: String,
        items: List<StudentCheckItem>,
        proofPaths: Map<String, String?> = emptyMap()
    ): SubmitOutcome {
        if (items.isEmpty()) {
            return SubmitOutcome(queued = true, clientOpId = "", message = "没有可提交的内容")
        }
        val batch = localStore.enqueueSubmit(
            taskId = taskId,
            roomId = roomId,
            items = items,
            proofPaths = proofPaths
        )

        if (!networkMonitor.isOnline() || tokenManager.getToken().isNullOrBlank()) {
            syncScheduler.requestNetworkRetry()
            return SubmitOutcome(
                queued = true,
                clientOpId = batch.clientOpId,
                message = "当前离线，已保存在本地，联网后自动同步"
            )
        }

        val ownerId = localStore.ownerId()
        val result = withTimeoutOrNull(IMMEDIATE_SUBMIT_TIMEOUT_MS) {
            syncEngine.submitBatch(ownerId, batch.clientOpId)
        }

        if (result == null) {
            syncScheduler.requestNetworkRetry()
            return SubmitOutcome(
                queued = true,
                clientOpId = batch.clientOpId,
                message = "提交超时，已排队等待自动重试"
            )
        }
        return when {
            result.acked -> SubmitOutcome(queued = false, clientOpId = batch.clientOpId)
            result.needsReauth -> {
                logout()
                SubmitOutcome(
                    queued = true,
                    clientOpId = batch.clientOpId,
                    message = result.message ?: "登录已失效，请重新登录",
                    needsReauth = true
                )
            }
            else -> {
                syncScheduler.requestNetworkRetry()
                SubmitOutcome(
                    queued = true,
                    clientOpId = batch.clientOpId,
                    message = result.message ?: "网络异常，已排队等待自动重试"
                )
            }
        }
    }

    suspend fun uploadProof(
        checkRecordId: String,
        base64Image: String,
        extension: String
    ): Resource<String> = try {
        val token = tokenManager.getToken().orEmpty()
        val path = tcpClient.uploadProof(token, checkRecordId, base64Image, extension)
        Resource.Success(path)
    } catch (e: AuthExpiredException) {
        logout()
        Resource.Unauthorized(e.message ?: "数据变动，需要重新登录")
    } catch (e: Exception) {
        Log.w(TAG, "上传证明照片失败", e)
        Resource.Error("上传证明照片失败，请重试")
    }

    suspend fun deleteProof(filePath: String, taskId: String, studentId: String) {
        try {
            val token = tokenManager.getToken().orEmpty()
            tcpClient.deleteProof(token, filePath, taskId, studentId)
        } catch (e: Exception) {
            Log.w(TAG, "删除远端证明文件失败（静默）: $filePath", e)
        }
    }

    // ---------------- 队列 / 同步 / 汇总 ----------------

    fun observeOutbox(): Flow<List<SyncOutboxEntity>> = localStore.observeOutbox()

    fun observePendingOutboxCount(): Flow<Int> = localStore.observePendingOutboxCount()

    suspend fun localDataCounts(): LocalStore.LocalDataCounts = localStore.localDataCounts()

    /**
     * 手动同步（A10「立即同步」/ 按时间段补交）。
     * @param fromTaskDate 起始 taskDate（ISO，含），null = 不限
     * @param toTaskDate 结束 taskDate（ISO，含），null = 不限
     */
    suspend fun syncNow(
        fromTaskDate: String? = null,
        toTaskDate: String? = null
    ): SyncEngine.SyncReport {
        val report = syncEngine.syncPending(
            fromTaskDate = fromTaskDate,
            toTaskDate = toTaskDate,
            includeFailed = true
        )
        if (report.remaining > 0 && !report.needsReauth) {
            syncScheduler.requestNetworkRetry()
        }
        return report
    }

    /** 设置页「清除本地数据」：记录 + 队列 + 快照 + 照片。 */
    suspend fun clearAllLocalData(): LocalStore.ClearedLocalData = localStore.clearAllLocalData()

    /** 总汇总（A09）：本地聚合，不请求服务端。 */
    suspend fun localSummary(scope: SummaryScope): SummaryResult = localStore.localSummary(scope)

    // ---------------- 注册（原有逻辑） ----------------

    suspend fun getRegistrationStatus(): Resource<Boolean> = try {
        Resource.Success(tcpClient.getRegistrationStatus())
    } catch (e: Exception) {
        Log.w(TAG, "获取注册状态失败", e)
        Resource.Error("获取注册状态失败")
    }

    suspend fun submitRegistration(
        name: String,
        gender: String,
        phone: String,
        department: String,
        className: String,
        password: String
    ): Resource<String> = try {
        Resource.Success(
            tcpClient.submitRegistration(name, gender, phone, department, className, password)
        )
    } catch (e: Exception) {
        Log.w(TAG, "提交注册申请失败", e)
        Resource.Error(e.message ?: "网络错误，请稍后重试")
    }

    private fun TaskDto.toDomain() = TaskSummary(
        id = id, taskNo = taskNo, title = title,
        buildingName = buildingName, floorNumber = floorNumber,
        totalRooms = totalRooms,
        checkedRooms = checkedRooms,
        status = status,
        taskDate = taskDate,
        deadlineTime = deadlineTime
    )

    private fun TaskOverviewDto.toDomain() = TaskOverview(
        taskId = task?.id ?: "",
        taskNo = task?.taskNo ?: "",
        buildingName = task?.buildingName ?: "",
        floors = listOfNotNull(floor?.let { f ->
            FloorOverview(
                floorId = f.floorId,
                floorNumber = f.floorNumber.toString(),
                rooms = f.rooms.map { r ->
                    RoomSummary(
                        roomId = r.roomId, roomNumber = r.roomNumber,
                        studentCount = r.studentCount, checked = r.checked
                    )
                }
            )
        })
    )

    private fun StudentDto.toDomain() = StudentDetail(
        id = id, studentNo = studentNo, name = name,
        gender = gender, roomNumber = roomNumber, bedNumber = bedNumber, className = className,
        presetStatus = CheckStatus.fromName(presetStatus) ?: CheckStatus.fromCode(statusCode),
        currentStatus = CheckStatus.fromCode(statusCode),
        presetStatusFrom = statusSource,
        proofFilePath = proofFile,
        remark = remark
    )
}
