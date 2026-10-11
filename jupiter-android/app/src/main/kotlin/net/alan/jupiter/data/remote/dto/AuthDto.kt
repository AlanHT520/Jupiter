package net.alan.jupiter.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class LoginResponse(
    val code: Int = 200,
    val message: String = "",
    val data: LoginData? = null
)

@Serializable
data class LoginData(
    val token: String = "",
    val userId: String = "",
    val username: String = "",
    val role: String = "",
    val name: String = "",
    val phone: String = "",
    val studentNo: String = "",
    val className: String? = null,
    val departmentName: String? = null
)

@Serializable
data class ApiResponse<T>(
    val code: Int = 200,
    val message: String = "",
    val data: T? = null
)