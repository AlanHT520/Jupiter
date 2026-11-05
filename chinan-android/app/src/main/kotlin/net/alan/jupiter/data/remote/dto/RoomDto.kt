package net.alan.jupiter.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class StudentDto(
    val id: String = "",
    val studentNo: String = "",
    val name: String = "",
    val gender: String = "",
    val className: String? = null,
    val roomId: String = "",
    val roomNumber: String = "",
    val buildingId: String = "",
    val buildingName: String = "",
    val bedNumber: String = "",
    val phone: String? = null,
    val isNewStudent: Boolean? = null,
    val longSickLeaveStartDate: String? = null,
    val longSickLeaveEndDate: String? = null,
    val militaryStartDate: String? = null,
    val militaryEndDate: String? = null,
    val internStartDate: String? = null,
    val internEndDate: String? = null,
    val dayStudentStartDate: String? = null,
    val dayStudentEndDate: String? = null,
    val checkStatus: String? = null,
    val statusCode: Int? = null,
    val presetStatus: String? = null,
    val statusSource: String? = null,
    val remark: String? = null,
    val proofFile: String? = null
)