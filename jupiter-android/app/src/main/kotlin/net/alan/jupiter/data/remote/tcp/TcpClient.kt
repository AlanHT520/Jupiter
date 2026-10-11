package net.alan.jupiter.data.remote.tcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import net.alan.jupiter.data.local.ServerConfigManager
import net.alan.jupiter.data.remote.dto.*
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

class AuthExpiredException(message: String) : IOException(message)

class TcpClient(
    private val serverConfigManager: ServerConfigManager
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private suspend fun send(type: String, payload: JsonObject? = null): JsonObject {
        var lastException: Exception? = null
        repeat(3) { attempt ->
            try {
                return sendOnce(type, payload)
            } catch (e: AuthExpiredException) {
                throw e
            } catch (e: IOException) {
                lastException = e
                if (attempt < 2) {
                    kotlinx.coroutines.delay(100L * (attempt + 1))
                }
            }
        }
        throw lastException ?: IOException("发送失败")
    }

    private suspend fun sendOnce(type: String, payload: JsonObject?): JsonObject =
        withContext(Dispatchers.IO) {
            val host = serverConfigManager.getHost()
            val port = serverConfigManager.getPort().toIntOrNull() ?: 9090
            if (host.isBlank()) throw IOException("未配置服务器地址")

            val socket = Socket()
            try {
                socket.tcpNoDelay = true
                socket.receiveBufferSize = 256 * 1024
                socket.sendBufferSize = 256 * 1024
                socket.connect(InetSocketAddress(host, port), 10000)

                DataOutputStream(BufferedOutputStream(socket.getOutputStream())).use { out ->
                    DataInputStream(BufferedInputStream(socket.getInputStream())).use { input ->
                        val requestId = UUID.randomUUID().toString()
                        TcpProtocol.encode(out, TcpProtocol.Frame(
                            type = type, requestId = requestId, payload = payload
                        ))

                        val resp = TcpProtocol.decode(input)

                        if (resp.code == 401) {
                            throw AuthExpiredException(
                                resp.message.ifBlank { "密码已变更，请重新登录" })
                        }
                        if (resp.code != 200) {
                            throw IOException(
                                resp.message.ifBlank { "服务器错误: ${resp.code}" })
                        }
                        return@withContext resp.payload
                            ?: throw IOException("服务器返回数据为空")
                    }
                }
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }
        }

    fun disconnect() {
    }

    suspend fun login(username: String, password: String): LoginData = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("username", username)
            put("password", password)
        }
        val data = send("LOGIN", payload)
        LoginData(
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

    suspend fun getTasks(token: String, page: Int = 0, size: Int = 50): PageResult<TaskDto> =
        withContext(Dispatchers.IO) {
            val payload = buildJsonObject {
                put("token", token)
                put("page", page)
                put("size", size)
            }
            val data = send("GET_TASKS", payload)

            val records: List<TaskDto> = if (data.containsKey("records")) {
                json.decodeFromString(data["records"].toString())
            } else emptyList()

            PageResult(
                records = records,
                total = data["total"]?.toString()?.toLongOrNull() ?: 0,
                page = data["page"]?.toString()?.toIntOrNull() ?: 0,
                size = data["size"]?.toString()?.toIntOrNull() ?: 50,
                totalPages = data["totalPages"]?.toString()?.toIntOrNull() ?: 0
            )
        }

    suspend fun getTaskOverview(token: String, taskId: String): TaskOverviewDto =
        withContext(Dispatchers.IO) {
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

            TaskOverviewDto(task = task, floor = floor)
        }

    suspend fun getRoomStudents(token: String, taskId: String, roomId: String): List<StudentDto> =
        withContext(Dispatchers.IO) {
            val payload = buildJsonObject {
                put("token", token)
                put("taskId", taskId)
                put("roomId", roomId)
            }
            val data = send("GET_STUDENTS", payload)
            val students: List<StudentDto> = if (data.containsKey("students")) {
                json.decodeFromString(data["students"].toString())
            } else emptyList()
            students
        }

    suspend fun submitCheck(
        token: String, taskId: String, roomId: String, students: List<StudentCheckItem>
    ): Unit = withContext(Dispatchers.IO) {
        val studentsElement = json.encodeToJsonElement(
            ListSerializer(StudentCheckItem.serializer()), students
        )
        val fullPayload = buildJsonObject {
            put("token", token)
            put("taskId", taskId)
            put("roomId", roomId)
            put("students", studentsElement)
        }
        send("SUBMIT_CHECK", fullPayload)
    }

    suspend fun uploadProof(
        token: String, checkRecordId: String, base64Image: String, extension: String
    ): String = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("token", token)
            put("checkRecordId", checkRecordId)
            put("data", base64Image)
            put("extension", extension)
        }
        val result = send("UPLOAD_PROOF", payload)
        result["proofFilePath"]?.jsonPrimitive?.content
            ?: throw IOException("服务器未返回文件路径")
    }

    companion object {
        private fun JsonObject.extract(key: String): String =
            this[key]?.jsonPrimitive?.content ?: ""

        private fun JsonObject.extractOrNull(key: String): String? =
            this[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
    }
}