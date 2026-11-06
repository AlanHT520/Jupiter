package net.alan.jupiter.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.alan.jupiter.data.repository.ChinanRepository
import net.alan.jupiter.domain.summary.SummaryResult
import net.alan.jupiter.domain.summary.SummaryScope
import javax.inject.Inject

data class SummaryUiState(
    val scope: SummaryScope = SummaryScope.ALL,
    val result: SummaryResult? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

/** A09 总汇总：本地聚合（不请求服务端），标注 ● CACHED asOf。 */
@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val repository: ChinanRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SummaryUiState())
    val uiState: StateFlow<SummaryUiState> = _uiState.asStateFlow()

    init {
        load(SummaryScope.ALL)
    }

    fun selectScope(scope: SummaryScope) {
        if (_uiState.value.scope == scope && _uiState.value.result != null) return
        load(scope)
    }

    fun refresh() {
        load(_uiState.value.scope)
    }

    private fun load(scope: SummaryScope) {
        viewModelScope.launch {
            _uiState.update { it.copy(scope = scope, isLoading = it.result == null, error = null) }
            val result = runCatching { repository.localSummary(scope) }.getOrNull()
            _uiState.update {
                it.copy(
                    scope = scope,
                    isLoading = false,
                    result = result,
                    error = if (result == null) "本地汇总失败，请稍后重试" else null
                )
            }
        }
    }
}
