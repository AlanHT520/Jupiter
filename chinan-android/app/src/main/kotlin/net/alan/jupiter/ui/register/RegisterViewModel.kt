package net.alan.jupiter.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.alan.jupiter.data.local.ServerConfigManager
import net.alan.jupiter.data.repository.ChinanRepository
import net.alan.jupiter.util.Resource
import javax.inject.Inject

data class RegisterUiState(
    val name: String = "",
    val studentNo: String = "",
    val className: String = "",
    val phone: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val host: String = "",
    val port: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val repository: ChinanRepository,
    private val serverConfigManager: ServerConfigManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val host = serverConfigManager.getHost()
            val port = serverConfigManager.getPort().ifBlank { "49749" }
            _uiState.update { it.copy(host = host, port = port) }
        }
    }

    fun updateName(value: String) = _uiState.update { it.copy(name = value, error = null) }
    fun updateStudentNo(value: String) = _uiState.update { it.copy(studentNo = value, error = null) }
    fun updateClassName(value: String) = _uiState.update { it.copy(className = value, error = null) }
    fun updatePhone(value: String) = _uiState.update { it.copy(phone = value, error = null) }
    fun updatePassword(value: String) = _uiState.update { it.copy(password = value, error = null) }
    fun updateConfirmPassword(value: String) = _uiState.update { it.copy(confirmPassword = value, error = null) }
    fun updateHost(value: String) = _uiState.update { it.copy(host = value, error = null) }
    fun updatePort(value: String) = _uiState.update { it.copy(port = value, error = null) }

    fun submit() {
        val state = _uiState.value
        if (state.host.isBlank()) {
            _uiState.update { it.copy(error = "请输入服务器地址") }
            return
        }
        if (state.name.isBlank()) {
            _uiState.update { it.copy(error = "请输入姓名") }
            return
        }
        if (state.studentNo.isBlank()) {
            _uiState.update { it.copy(error = "请输入学号") }
            return
        }
        if (state.className.isBlank()) {
            _uiState.update { it.copy(error = "请输入班级") }
            return
        }
        if (state.password.isBlank()) {
            _uiState.update { it.copy(error = "请输入密码") }
            return
        }
        if (state.password != state.confirmPassword) {
            _uiState.update { it.copy(error = "两次输入的密码不一致") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            serverConfigManager.saveServerConfig(state.host.trim(), state.port.trim().ifBlank { "49749" })
            when (val result = repository.submitRegistration(
                name = state.name.trim(),
                studentNo = state.studentNo.trim(),
                className = state.className.trim(),
                phone = state.phone.trim(),
                password = state.password
            )) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, successMessage = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, error = result.message)
                }
                else -> _uiState.update {
                    it.copy(isLoading = false, error = "网络错误，请稍后重试")
                }
            }
        }
    }

    fun clearSuccess() = _uiState.update { it.copy(successMessage = null) }
}