package net.alan.jupiter.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.alan.jupiter.data.repository.ChinanRepository
import javax.inject.Inject

data class SettingsUiState(
    val records: Int = 0,
    val acked: Int = 0,
    val outbox: Int = 0,
    val pending: Int = 0,
    val isClearing: Boolean = false,
    val isChangingPassword: Boolean = false,
    val passwordChanged: Boolean = false,
    val message: String? = null
)

/** 设置页：本地数据统计 + 「清除本地数据」（唯一会删记录的入口）。 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ChinanRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refreshCounts()
    }

    fun refreshCounts() {
        viewModelScope.launch {
            val counts = repository.localDataCounts()
            _uiState.update {
                it.copy(
                    records = counts.records,
                    acked = counts.acked,
                    outbox = counts.outbox,
                    pending = counts.pending
                )
            }
        }
    }

    fun changePassword(oldPassword: String, newPassword: String) {
        if (_uiState.value.isChangingPassword) return
        viewModelScope.launch {
            _uiState.update { it.copy(isChangingPassword = true, message = null) }
            runCatching { repository.changePassword(oldPassword, newPassword) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isChangingPassword = false,
                            passwordChanged = true,
                            message = "密码已修改，请重新登录"
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isChangingPassword = false,
                            message = error.message ?: "修改失败，请检查旧密码"
                        )
                    }
                }
        }
    }

    fun clearLocalData() {
        if (_uiState.value.isClearing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isClearing = true, message = null) }
            val cleared = repository.clearAllLocalData()
            _uiState.update {
                it.copy(
                    records = 0,
                    acked = 0,
                    outbox = 0,
                    pending = 0,
                    isClearing = false,
                    message = "已清除 记录 ${cleared.records} · 队列 ${cleared.outbox} · " +
                        "快照 ${cleared.tasks}/${cleared.students} · 照片 ${cleared.photos}"
                )
            }
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
