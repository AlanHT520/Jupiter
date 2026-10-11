package net.alan.jupiter.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class SubmitRoomRequest(
    val taskId: String,
    val roomId: String,
    val students: List<StudentCheckItem>
)

@Serializable
data class StudentCheckItem(
    val studentId: String,
    val checkStatus: String,
    val presetStatus: String? = null,
    val presetOverridden: Boolean? = null,
    val statusSource: String? = null,
    val remark: String? = null,
    val proofFile: String? = null
)