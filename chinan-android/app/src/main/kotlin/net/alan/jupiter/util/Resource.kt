package net.alan.jupiter.util

sealed class Resource<out T> {
    data object Loading : Resource<Nothing>()
    data class Success<T>(val data: T) : Resource<T>()
    data class Error(val message: String, val code: Int? = null) : Resource<Nothing>()
    data class Unauthorized(val message: String) : Resource<Nothing>()
}