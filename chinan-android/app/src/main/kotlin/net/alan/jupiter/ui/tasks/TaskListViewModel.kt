package net.alan.jupiter.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.alan.jupiter.data.repository.ChinanRepository
import net.alan.jupiter.domain.model.TaskSummary
import net.alan.jupiter.util.Resource
import javax.inject.Inject

data class TaskListUiState(
    val tasks: List<TaskSummary> = emptyList(),
    /** taskId → 该任务本地待上行（QUEUED/IN_FLIGHT/FAILED）条数 */
    val pendingByTask: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    /** 网络刷新失败但本地有缓存：仍展示列表，标注 ● CACHED */
    val cacheOnly: Boolean = false,
    val error: String? = null,
    val needsReauth: Boolean = false
) {
    val pendingTotal: Int get() = pendingByTask.values.sum()
}

/**
 * 任务列表：数据来自 Room（离线可查），刷新只是把网络结果 upsert 进本地。
 */
@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val repository: ChinanRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskListUiState())
    val uiState: StateFlow<TaskListUiState> = _uiState.asStateFlow()

    init {
        observeLocal()
        loadTasks()
    }

    private fun observeLocal() {
        viewModelScope.launch {
            repository.observeTasks().collect { tasks ->
                _uiState.update { state ->
                    state.copy(
                        tasks = tasks,
                        cacheOnly = tasks.isNotEmpty() && state.error != null
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.observePendingByTask().collect { pending ->
                _uiState.update { it.copy(pendingByTask = pending) }
            }
        }
    }

    fun loadTasks() {
        val state = _uiState.value
        if (state.isLoading || state.isRefreshing) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = it.tasks.isEmpty(),
                    isRefreshing = it.tasks.isNotEmpty(),
                    error = null
                )
            }
            when (val result = repository.getTasks()) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = null,
                        cacheOnly = false
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = result.message,
                        cacheOnly = it.tasks.isNotEmpty()
                    )
                }
                is Resource.Unauthorized -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, needsReauth = true)
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun clearReauth() {
        _uiState.update { it.copy(needsReauth = false) }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
        }
    }
}
