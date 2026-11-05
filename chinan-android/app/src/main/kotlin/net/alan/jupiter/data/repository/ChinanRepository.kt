package net.alan.jupiter.data.repository

import android.util.Log
import kotlinx.coroutines.flow.Flow
import net.alan.jupiter.data.local.entity.PendingCheckEntity
import net.alan.jupiter.data.local.PendingCheckDao
import net.alan.jupiter.data.local.TokenManager
import net.alan.jupiter.data.remote.dto.StudentCheckItem
import net.alan.jupiter.data.remote.dto.StudentDto
import net.alan.jupiter.data.remote.dto.TaskDto
import net.alan.jupiter.data.remote.dto.TaskOverviewDto
import net.alan.jupiter.data.remote.tcp.AuthExpiredException
import net.alan.jupiter.data.remote.tcp.TcpClient
import net.alan.jupiter.domain.model.*
import net.alan.jupiter.util.Resource
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ChinanRepository"

@Singleton
class ChinanRepository @Inject constructor(
    private val tcpClient: TcpClient,
    private val tokenManager: TokenManager,
    private val pendingCheckDao: PendingCheckDao
) {
    suspend fun login(username: String, password: String): Resource<User> = try {
        val data = tcpClient.login(username, password)
        tokenManager.saveLoginInfo(data.token, data.username, data.role,
            data.name, data.phone, data.studentNo,
            data.className, data.departmentName)
        Resource.Success(User(data.username, data.role, data.token))
    } catch (e: AuthExpiredException) {
        logout()
        Resource.Unauthorized(e.message ?: "数据变动，需要重新登录")
    } catch (e: Exception) {
        Log.w(TAG, "登录失败", e)
        Resource.Error("连接失败，请检查服务器地址和网络设置")
    }

    suspend fun getTasks(): Resource<List<TaskSummary>> = try {
        val token = tokenManager.getToken().orEmpty()
        val result = tcpClient.getTasks(token)
        Resource.Success(result.records.map { it.toDomain() })
    } catch (e: AuthExpiredException) {
        logout()
        Resource.Unauthorized(e.message ?: "数据变动，需要重新登录")
    } catch (e: Exception) {
        Log.w(TAG, "获取任务列表失败", e)
        Resource.Error("加载失败，请点击刷新重试")
    }

    suspend fun getTaskOverview(taskId: String): Resource<TaskOverview> = try {
        val token = tokenManager.getToken().orEmpty()
        val result = tcpClient.getTaskOverview(token, taskId)
        Resource.Success(result.toDomain())
    } catch (e: AuthExpiredException) {
        logout()
        Resource.Unauthorized(e.message ?: "数据变动，需要重新登录")
    } catch (e: Exception) {
        Log.w(TAG, "获取任务概览失败", e)
        Resource.Error("加载失败，请返回重试")
    }

    suspend fun getRoomStudents(taskId: String, roomId: String): Resource<List<StudentDetail>> = try {
        val token = tokenManager.getToken().orEmpty()
        val result = tcpClient.getRoomStudents(token, taskId, roomId)
        Resource.Success(result.map { it.toDomain() })
    } catch (e: AuthExpiredException) {
        logout()
        Resource.Unauthorized(e.message ?: "数据变动，需要重新登录")
    } catch (e: Exception) {
        Log.w(TAG, "获取学生列表失败", e)
        Resource.Error("加载失败，请返回重试")
    }

    suspend fun submitCheck(
        taskId: String,
        roomId: String,
        items: List<StudentCheckItem>
    ): Resource<Unit> = try {
        val token = tokenManager.getToken().orEmpty()
        tcpClient.submitCheck(token, taskId, roomId, items)
        Resource.Success(Unit)
    } catch (e: AuthExpiredException) {
        logout()
        Resource.Unauthorized(e.message ?: "数据变动，需要重新登录")
    } catch (e: Exception) {
        Log.w(TAG, "提交查寝结果失败", e)
        Resource.Error("提交失败，请稍后重试")
    }

    suspend fun uploadProof(checkRecordId: String, base64Image: String, extension: String): Resource<String> = try {
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

    suspend fun savePendingCheck(
        taskId: String, roomId: String, studentId: String,
        statusCode: Int, remark: String?, proofLocalPath: String?, proofFilePath: String? = null
    ) {
        pendingCheckDao.deleteByTaskRoomAndStudent(taskId, roomId, studentId)
        pendingCheckDao.insert(PendingCheckEntity(
            taskId = taskId, roomId = roomId, studentId = studentId,
            checkStatusCode = statusCode, remark = remark,
            proofLocalPath = proofLocalPath,
            proofFilePath = proofFilePath,
            updatedAt = System.currentTimeMillis()
        ))
    }

    suspend fun getPendingChecks(taskId: String, roomId: String): List<PendingCheckEntity> =
        pendingCheckDao.getByTaskAndRoom(taskId, roomId)

    fun observePendingCount(): Flow<List<PendingCheckEntity>> = pendingCheckDao.observeAll()

    suspend fun getPendingCount(): Int = pendingCheckDao.count()

    suspend fun clearPendingChecks(ids: List<Long>) = pendingCheckDao.deleteByIds(ids)

    suspend fun clearAllPending() = pendingCheckDao.deleteAll()

    suspend fun getToken(): String? = tokenManager.getToken()

    suspend fun logout(): Unit {
        tcpClient.disconnect()
        tokenManager.clear()
    }

    suspend fun getRegistrationStatus(): Resource<Boolean> = try {
        Resource.Success(tcpClient.getRegistrationStatus())
    } catch (e: Exception) {
        Log.w(TAG, "获取注册状态失败", e)
        Resource.Error("获取注册状态失败")
    }

    suspend fun submitRegistration(
        name: String,
        studentNo: String,
        className: String,
        phone: String,
        password: String
    ): Resource<String> = try {
        Resource.Success(tcpClient.submitRegistration(name, studentNo, className, phone, password))
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