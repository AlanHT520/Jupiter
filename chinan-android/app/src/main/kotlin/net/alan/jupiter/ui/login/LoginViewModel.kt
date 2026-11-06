package net.alan.jupiter.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import net.alan.jupiter.data.local.ServerConfigManager
import net.alan.jupiter.data.repository.ChinanRepository
import net.alan.jupiter.util.Resource
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val host: String = "",
    val port: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val loginSuccess: Boolean = false,
    val registrationEnabled: Boolean = false,
    val isTesting: Boolean = false,
    val testResult: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: ChinanRepository,
    private val serverConfigManager: ServerConfigManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val host = serverConfigManager.getHost()
            val port = serverConfigManager.getPort().ifBlank { ServerConfigManager.DEFAULT_PORT }
            _uiState.update { it.copy(host = host, port = port) }

            if (host.isNotBlank()) {
                when (val r = repository.getRegistrationStatus()) {
                    is Resource.Success -> _uiState.update {
                        it.copy(registrationEnabled = r.data)
                    }
                    else -> _uiState.update {
                        it.copy(registrationEnabled = false)
                    }
                }
            }
        }
    }

    fun updateUsername(value: String) = _uiState.update { it.copy(username = value, error = null) }
    fun updatePassword(value: String) = _uiState.update { it.copy(password = value, error = null) }

    /**
     * 服务器地址归一化：中文输入法会把 `.` 打成 `。`、`,` 打成 `，`，
     * 直接按原样保存会导致连接必然失败，这里统一换成半角。
     */
    fun updateHost(value: String) = _uiState.update {
        it.copy(host = normalizeHost(value), error = null)
    }

    fun updatePort(value: String) = _uiState.update { it.copy(port = value, error = null) }

    private fun normalizeHost(raw: String): String = raw
        .replace('。', '.')
        .replace('，', ',')
        .replace('．', '.')
        .trim()

    fun login() {
        val state = _uiState.value
        if (state.host.isBlank()) {
            _uiState.update { it.copy(error = "请输入服务器地址") }
            return
        }
        if (!state.host.matches(Regex("^[A-Za-z0-9.\\-]+$"))) {
            _uiState.update { it.copy(error = "服务器地址只能包含字母、数字、点和横线") }
            return
        }
        if (state.username.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(error = "请输入用户名和密码") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            serverConfigManager.saveServerConfig(
                state.host.trim(),
                state.port.trim().ifBlank { ServerConfigManager.DEFAULT_PORT }
            )
            when (val result = repository.login(state.username.trim(), state.password)) {
                is Resource.Success -> _uiState.update { it.copy(isLoading = false, loginSuccess = true) }
                is Resource.Error -> _uiState.update { it.copy(isLoading = false, error = result.message) }
                is Resource.Unauthorized -> _uiState.update { it.copy(isLoading = false, error = result.message) }
                is Resource.Loading -> {}
            }
        }
    }

    fun testConnection() {
        val state = _uiState.value
        val host = state.host.trim()
        val port = state.port.trim().ifBlank { ServerConfigManager.DEFAULT_PORT }.toIntOrNull()
        if (host.isBlank() || port == null) {
            _uiState.update { it.copy(testResult = "请先填写服务器地址和正确端口") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, testResult = null, error = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(host, port), 3000)
                    }
                }
            }
            _uiState.update {
                it.copy(
                    isTesting = false,
                    testResult = result.fold(
                        onSuccess = { "TCP $host:$port 可连接" },
                        onFailure = { e ->
                            val reason = when {
                                e is java.net.SocketTimeoutException -> "连接超时"
                                e is java.net.ConnectException -> "连接被拒绝或地址不可达"
                                else -> e.message ?: "连接失败"
                            }
                            "$reason（$host:$port）"
                        }
                    )
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
        }
    }
}
