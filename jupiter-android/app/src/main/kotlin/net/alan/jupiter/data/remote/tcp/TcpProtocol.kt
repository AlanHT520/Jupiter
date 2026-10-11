package net.alan.jupiter.data.remote.tcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

object TcpProtocol {

    private const val MAX_FRAME_SIZE = 10 * 1024 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    data class Frame(
        val type: String = "",
        val requestId: String = "",
        val code: Int = 0,
        val message: String = "",
        val payload: JsonObject? = null
    )

    fun encode(stream: DataOutputStream, frame: Frame) {
        val root = buildJsonObject {
            put("type", frame.type)
            put("requestId", frame.requestId)
            if (frame.payload != null) {
                put("payload", frame.payload)
            }
        }
        val body = root.toString().toByteArray(Charsets.UTF_8)
        val totalLen = 4 + body.size
        stream.writeInt(totalLen)
        stream.write(body)
        stream.flush()
    }

    fun decode(stream: DataInputStream): Frame {
        val length = stream.readInt()
        if (length < 4) {
            throw IOException("Invalid frame length: $length")
        }
        val bodySize = length - 4
        if (bodySize > MAX_FRAME_SIZE) {
            throw IOException("Frame too large: $bodySize bytes, max is $MAX_FRAME_SIZE")
        }
        val body = ByteArray(bodySize)
        stream.readFully(body)
        val text = String(body, Charsets.UTF_8)
        val root = json.parseToJsonElement(text).jsonObject

        return Frame(
            type = root.extractString("type"),
            requestId = root.extractString("requestId"),
            code = root.extractInt("code"),
            message = root.extractString("message"),
            payload = root["payload"]?.jsonObject
        )
    }

    private fun JsonObject.extractString(key: String): String =
        this[key]?.jsonPrimitive?.content ?: ""

    private fun JsonObject.extractInt(key: String): Int =
        this[key]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
}