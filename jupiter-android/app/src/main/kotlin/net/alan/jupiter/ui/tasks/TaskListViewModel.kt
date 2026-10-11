package net.alan.jupiter.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.alan.jupiter.data.repository.JupiterRepository
import net.alan.jupiter.domain.model.TaskSummary
import net.alan.jupiter.util.Resource
import javax.inject.Inject

data class TaskListUiState(
    val tasks: List<TaskSummary> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val needsReauth: Boolean = false
)

@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val repository: JupiterRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskListUiState())
    val uiState: StateFlow<TaskListUiState> = _uiState.asStateFlow()

    init { loadTasks() }

    fun loadTasks() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.getTasks()) {
                is Resource.Success -> _uiState.update { it.copy(isLoading = false, tasks = result.data) }
                is Resource.Error -> _uiState.update { it.copy(isLoading = false, error = result.message) }
                is Resource.Unauthorized -> _uiState.update { it.copy(isLoading = false, needsReauth = true) }
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