package net.alan.jupiter.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.alan.jupiter.data.local.ServerConfigManager
import net.alan.jupiter.data.repository.JupiterRepository
import net.alan.jupiter.util.Resource
import javax.inject.Inject

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val host: String = "",
    val port: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val loginSuccess: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: JupiterRepository,
    private val serverConfigManager: ServerConfigManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val host = serverConfigManager.getHost()
            val port = serverConfigManager.getPort()
            _uiState.update { it.copy(host = host, port = port) }
        }
    }

    fun updateUsername(value: String) = _uiState.update { it.copy(username = value, error = null) }
    fun updatePassword(value: String) = _uiState.update { it.copy(password = value, error = null) }
    fun updateHost(value: String) = _uiState.update { it.copy(host = value, error = null) }
    fun updatePort(value: String) = _uiState.update { it.copy(port = value, error = null) }

    fun login() {
        val state = _uiState.value
        if (state.host.isBlank()) {
            _uiState.update { it.copy(error = "请输入服务器地址") }
            return
        }
        if (state.username.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(error = "请输入用户名和密码") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            serverConfigManager.saveServerConfig(state.host.trim(), state.port.trim().ifBlank { "9090" })
            when (val result = repository.login(state.username.trim(), state.password)) {
                is Resource.Success -> _uiState.update { it.copy(isLoading = false, loginSuccess = true) }
                is Resource.Error -> _uiState.update { it.copy(isLoading = false, error = result.message) }
                is Resource.Unauthorized -> _uiState.update { it.copy(isLoading = false, error = result.message) }
                is Resource.Loading -> {}
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
        }
    }
}