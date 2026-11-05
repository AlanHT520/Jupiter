package net.alan.jupiter.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.alan.jupiter.data.local.TokenManager
import javax.inject.Inject

data class ProfileUiState(
    val name: String = "",
    val studentNo: String = "",
    val className: String = "",
    val phone: String = "",
    val departmentName: String = "",
    val username: String = "",
    val role: String = "",
    val isLoading: Boolean = true
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    name = tokenManager.getName() ?: "",
                    studentNo = tokenManager.getStudentNo() ?: "",
                    className = tokenManager.getClassName() ?: "",
                    phone = tokenManager.getPhone() ?: "",
                    departmentName = tokenManager.getDepartmentName() ?: "",
                    username = tokenManager.getUsername() ?: "",
                    role = tokenManager.getRole() ?: "",
                    isLoading = false
                )
            }
        }
    }
}