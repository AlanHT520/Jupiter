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
    val gender: String = "",
    val phone: String = "",
    val department: String = "",
    val className: String = "",
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
            val port = serverConfigManager.getPort().ifBlank { ServerConfigManager.DEFAULT_PORT }
            _uiState.update { it.copy(host = host, port = port) }
        }
    }

    fun updateName(value: String) = _uiState.update { it.copy(name = value, error = null) }
    fun updateGender(value: String) = _uiState.update { it.copy(gender = value, error = null) }
    fun updatePhone(value: String) = _uiState.update { it.copy(phone = value, error = null) }
    fun updateDepartment(value: String) = _uiState.update { it.copy(department = value, error = null) }
    fun updateClassName(value: String) = _uiState.update { it.copy(className = value, error = null) }
    fun updatePassword(value: String) = _uiState.update { it.copy(password = value, error = null) }
    fun updateConfirmPassword(value: String) = _uiState.update { it.copy(confirmPassword = value, error = null) }
    fun updateHost(value: String) = _uiState.update {
        // 中文输入法会把 `.` 打成 `。`，统一归一半角，否则注册必然连不上
        it.copy(
            host = value.replace('。', '.').replace('，', ',').replace('．', '.').trim(),
            error = null
        )
    }
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
        if (state.phone.length != 11) {
            _uiState.update { it.copy(error = "请输入 11 位手机号") }
            return
        }
        if (state.department.isBlank()) {
            _uiState.update { it.copy(error = "请输入部门") }
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
        if (state.password.length < 4) {
            _uiState.update { it.copy(error = "密码至少 4 位") }
            return
        }
        if (state.password != state.confirmPassword) {
            _uiState.update { it.copy(error = "两次输入的密码不一致") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            // 只在地址真正变化时写配置，避免「注册一次把登录页已存的服务器改坏」
            val targetHost = state.host.trim()
            val targetPort = state.port.trim().ifBlank { ServerConfigManager.DEFAULT_PORT }
            if (targetHost != serverConfigManager.getHost() ||
                targetPort != serverConfigManager.getPort()
            ) {
                serverConfigManager.saveServerConfig(targetHost, targetPort)
            }
            when (val result = repository.submitRegistration(
                name = state.name.trim(),
                gender = state.gender.trim(),
                phone = state.phone.trim(),
                department = state.department.trim(),
                className = state.className.trim(),
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
