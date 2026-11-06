package net.alan.jupiter.data.remote.tcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import net.alan.jupiter.data.local.ServerConfigManager
import net.alan.jupiter.data.remote.dto.*
import java.io.IOException

class AuthExpiredException(message: String) : IOException(message)

class TcpClient(
    serverConfigManager: ServerConfigManager
) {
    private val connection: TcpConnection = TcpConnection {
        val host = serverConfigManager.getHost()
        val port = serverConfigManager.getPort().toIntOrNull() ?: 9090
        host to port
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private suspend fun send(type: String, payload: JsonObject? = null): JsonObject {
        var lastException: Exception? = null
        repeat(3) { attempt ->
            try {
                return sendOnConnection(type, payload)
            } catch (e: AuthExpiredException) {
                throw e
            } catch (e: IOException) {
                lastException = e
                if (attempt < 2) {
                    delay(100L * (attempt + 1))
                }
            }
        }
        throw lastException ?: IOException("发送失败")
    }

    private suspend fun sendOnConnection(type: String, payload: JsonObject?): JsonObject =
        connection.request(type, payload) { it }

    fun disconnect() {
        connection.disconnect()
    }

    suspend fun login(username: String, password: String): LoginData {
        val payload = buildJsonObject {
            put("username", username)
            put("password", password)
        }
        val data = send("LOGIN", payload)
        return LoginData(
            token = data.extract("token"),
            userId = data.extract("userId"),
            username = data.extract("username"),
            role = data.extract("role"),
            name = data.extract("name"),
            phone = data.extract("phone"),
            studentNo = data.extract("studentNo"),
            className = data.extractOrNull("className"),
            departmentName = data.extractOrNull("departmentName")
        )
    }

    suspend fun getTasks(token: String, page: Int = 0, size: Int = 50): PageResult<TaskDto> {
        val payload = buildJsonObject {
            put("token", token)
            put("page", page)
            put("size", size)
        }
        val data = send("GET_TASKS", payload)

        val records: List<TaskDto> = if (data.containsKey("records")) {
            json.decodeFromString(data["records"].toString())
        } else emptyList()

        return PageResult(
            records = records,
            total = data["total"]?.toString()?.toLongOrNull() ?: 0,
            page = data["page"]?.toString()?.toIntOrNull() ?: 0,
            size = data["size"]?.toString()?.toIntOrNull() ?: 50,
            totalPages = data["totalPages"]?.toString()?.toIntOrNull() ?: 0
        )
    }

    suspend fun getTaskOverview(token: String, taskId: String): TaskOverviewDto {
        val payload = buildJsonObject {
            put("token", token)
            put("taskId", taskId)
        }
        val data = send("GET_OVERVIEW", payload)

        val task = if (data.containsKey("task")) {
            json.decodeFromString<TaskDto>(data["task"].toString())
        } else TaskDto()

        val floor = if (data.containsKey("floor")) {
            json.decodeFromString<FloorInfo>(data["floor"].toString())
        } else FloorInfo()

        return TaskOverviewDto(task = task, floor = floor)
    }

    suspend fun getRoomStudents(token: String, taskId: String, roomId: String): List<StudentDto> {
        val payload = buildJsonObject {
            put("token", token)
            put("taskId", taskId)
            put("roomId", roomId)
        }
        val data = send("GET_STUDENTS", payload)
        return if (data.containsKey("students")) {
            json.decodeFromString(data["students"].toString())
        } else emptyList()
    }

    /**
     * 提交查寝结果。
     *
     * `clientOpId` 是业务幂等键（一个房间一个批次），服务端按 (clientOpId, studentId) 去重；
     * 它和 TCP 帧里的 requestId（多路复用）是两码事，重试时必须复用同一个值。
     */
    suspend fun submitCheck(
        token: String,
        taskId: String,
        roomId: String,
        students: List<StudentCheckItem>,
        clientOpId: String? = null
    ) {
        val studentsElement = json.encodeToJsonElement(
            ListSerializer(StudentCheckItem.serializer()), students
        )
        val fullPayload = buildJsonObject {
            put("token", token)
            put("taskId", taskId)
            put("roomId", roomId)
            if (!clientOpId.isNullOrBlank()) put("clientOpId", clientOpId)
            put("students", studentsElement)
        }
        send("SUBMIT_CHECK", fullPayload)
    }

    suspend fun uploadProof(
        token: String, checkRecordId: String, base64Image: String, extension: String
    ): String {
        val payload = buildJsonObject {
            put("token", token)
            put("checkRecordId", checkRecordId)
            put("data", base64Image)
            put("extension", extension)
        }
        val result = send("UPLOAD_PROOF", payload)
        return result["proofFilePath"]?.jsonPrimitive?.content
            ?: throw IOException("服务器未返回文件路径")
    }

    suspend fun deleteProof(
        token: String, filePath: String, taskId: String, studentId: String
    ) {
        val payload = buildJsonObject {
            put("token", token)
            put("filePath", filePath)
            put("taskId", taskId)
            put("studentId", studentId)
        }
        send("DELETE_PROOF", payload)
    }

    suspend fun changePassword(token: String, oldPassword: String, newPassword: String) {
        val payload = buildJsonObject {
            put("token", token)
            put("oldPassword", oldPassword)
            put("newPassword", newPassword)
        }
        send("CHANGE_PASSWORD", payload)
    }

    suspend fun getRegistrationStatus(): Boolean = withContext(Dispatchers.IO) {        val data = send("GET_REGISTRATION_STATUS", buildJsonObject {})
        data["enabled"]?.jsonPrimitive?.booleanOrNull ?: false
    }

    suspend fun submitRegistration(
        name: String,
        gender: String,
        phone: String,
        department: String,
        className: String,
        password: String
    ): String = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("name", name)
            put("gender", gender)
            put("phone", phone)
            put("department", department)
            put("className", className)
            put("password", password)
        }
        val data = send("REGISTER", payload)
        data.extract("message")
    }

    companion object {
        private fun JsonObject.extract(key: String): String =
            this[key]?.jsonPrimitive?.content ?: ""

        private fun JsonObject.extractOrNull(key: String): String? =
            this[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
    }
}