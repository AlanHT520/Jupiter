package net.alan.jupiter.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.alan.jupiter.data.repository.ChinanRepository
import net.alan.jupiter.domain.model.FloorOverview
import net.alan.jupiter.util.Resource
import javax.inject.Inject

data class TaskOverviewUiState(
    val taskNo: String = "",
    val buildingName: String = "",
    val floors: List<FloorOverview> = emptyList(),
    val isLoading: Boolean = false,
    /** 网络失败时用本地快照兜底展示 */
    val cacheOnly: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class TaskOverviewViewModel @Inject constructor(
    private val repository: ChinanRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskOverviewUiState())
    val uiState: StateFlow<TaskOverviewUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun loadOverview(taskId: String, taskNo: String, buildingName: String) {
        // LaunchedEffect 与 ON_RESUME 可能同时触发：先取消上一次，避免两次请求叠在一起互相覆盖状态
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    taskNo = taskNo,
                    buildingName = buildingName
                )
            }
            try {
                when (val result = repository.getTaskOverview(taskId)) {
                    is Resource.Success -> _uiState.update {
                        it.copy(
                            isLoading = false,
                            floors = result.data.floors,
                            cacheOnly = false,
                            error = null
                        )
                    }
                    is Resource.Error -> {
                        val cached = repository.cachedOverview(taskId)
                        if (cached != null) {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    floors = cached.floors,
                                    cacheOnly = true,
                                    error = result.message
                                )
                            }
                        } else {
                            _uiState.update { it.copy(isLoading = false, error = result.message) }
                        }
                    }
                    is Resource.Unauthorized -> _uiState.update {
                        it.copy(isLoading = false, error = result.message)
                    }
                    is Resource.Loading -> {}
                }
            } finally {
                // 只有「仍是最新一次加载」才关掉转圈：被新请求取消时不要覆盖它的 loading 态
                if (isActive) {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }
}
