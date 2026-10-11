package net.alan.jupiter.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.alan.jupiter.data.repository.JupiterRepository
import net.alan.jupiter.domain.model.FloorOverview
import net.alan.jupiter.util.Resource
import javax.inject.Inject

data class TaskOverviewUiState(
    val taskNo: String = "",
    val buildingName: String = "",
    val floors: List<FloorOverview> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class TaskOverviewViewModel @Inject constructor(
    private val repository: JupiterRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskOverviewUiState())
    val uiState: StateFlow<TaskOverviewUiState> = _uiState.asStateFlow()

    fun loadOverview(taskId: String, taskNo: String, buildingName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, taskNo = taskNo, buildingName = buildingName) }
            when (val result = repository.getTaskOverview(taskId)) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, floors = result.data.floors)
                }
                is Resource.Error -> _uiState.update { it.copy(isLoading = false, error = result.message) }
                is Resource.Unauthorized -> _uiState.update { it.copy(isLoading = false, error = result.message) }
                is Resource.Loading -> {}
            }
        }
    }
}