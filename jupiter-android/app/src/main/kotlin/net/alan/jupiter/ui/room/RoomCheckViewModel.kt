package net.alan.jupiter.ui.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.alan.jupiter.data.remote.dto.StudentCheckItem
import net.alan.jupiter.data.repository.JupiterRepository
import net.alan.jupiter.domain.model.CheckStatus
import net.alan.jupiter.domain.model.StudentDetail
import net.alan.jupiter.util.Resource
import javax.inject.Inject

data class RoomCheckUiState(
    val roomNumber: String = "",
    val floorNumber: String = "",
    val students: List<StudentWithStatus> = emptyList(),
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val submitSuccess: Boolean = false
)

data class StudentWithStatus(
    val student: StudentDetail,
    val selectedStatus: CheckStatus? = null,
    val remark: String = "",
    val isDirty: Boolean = false,
    val proofLocalPath: String? = null,
    val proofFilePath: String? = null
)

@HiltViewModel
class RoomCheckViewModel @Inject constructor(
    private val repository: JupiterRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoomCheckUiState())
    val uiState: StateFlow<RoomCheckUiState> = _uiState.asStateFlow()

    fun loadRoom(taskId: String, roomId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, submitSuccess = false) }
            when (val result = repository.getRoomStudents(taskId, roomId)) {
                is Resource.Success -> {
                    val pending = repository.getPendingChecks(taskId, roomId)
                    val students = result.data.map { s ->
                        val pendingCheck = pending.find { it.studentId == s.id }
                        StudentWithStatus(
                            student = s,
                            selectedStatus = pendingCheck?.let {
                                CheckStatus.fromCode(it.checkStatusCode)
                            } ?: s.currentStatus ?: CheckStatus.PRESENT,
                            remark = pendingCheck?.remark ?: s.remark ?: "",
                            proofLocalPath = pendingCheck?.proofLocalPath,
                            proofFilePath = s.proofFilePath,
                            isDirty = pendingCheck != null
                        )
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            roomNumber = result.data.firstOrNull()?.roomNumber ?: "",
                            floorNumber = "",
                            students = students
                        )
                    }
                }
                is Resource.Error -> _uiState.update { it.copy(isLoading = false, error = result.message) }
                is Resource.Unauthorized -> _uiState.update { it.copy(isLoading = false, error = result.message) }
                is Resource.Loading -> {}
            }
        }
    }

    fun updateStatus(studentIndex: Int, status: CheckStatus) {
        _uiState.update { state ->
            val updated = state.students.toMutableList()
            updated[studentIndex] = updated[studentIndex].copy(selectedStatus = status, isDirty = true)
            state.copy(students = updated)
        }
    }

    fun updateRemark(studentIndex: Int, remark: String) {
        _uiState.update { state ->
            val updated = state.students.toMutableList()
            updated[studentIndex] = updated[studentIndex].copy(remark = remark, isDirty = true)
            state.copy(students = updated)
        }
    }

    fun updateProofPhoto(studentIndex: Int, localPath: String) {
        _uiState.update { state ->
            val updated = state.students.toMutableList()
            updated[studentIndex] = updated[studentIndex].copy(
                proofLocalPath = localPath,
                proofFilePath = null,
                isDirty = true
            )
            state.copy(students = updated)
        }
    }

    fun clearProofPhoto(studentIndex: Int) {
        _uiState.update { state ->
            val updated = state.students.toMutableList()
            updated[studentIndex] = updated[studentIndex].copy(
                proofLocalPath = null,
                proofFilePath = null,
                isDirty = true
            )
            state.copy(students = updated)
        }
    }

    fun submitChecks(taskId: String, roomId: String) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }

            val proofUploadMap = mutableMapOf<Int, String>()
            for ((index, sws) in state.students.withIndex()) {
                val localPath = sws.proofLocalPath
                if (localPath != null && sws.proofFilePath == null) {
                    try {
                        val (base64, ext) = withContext(Dispatchers.IO) {
                            val bytes = java.io.File(localPath).readBytes()
                            val encoded = android.util.Base64.encodeToString(
                                bytes, android.util.Base64.NO_WRAP
                            )
                            val extension = localPath.substringAfterLast('.', "jpg")
                            encoded to ".$extension"
                        }
                        val result = repository.uploadProof(
                            "${taskId}_${sws.student.id}",
                            base64,
                            ext
                        )
                        if (result is Resource.Success) {
                            proofUploadMap[index] = result.data
                        }
                    } catch (e: Exception) {
                        _uiState.update { it.copy(isSubmitting = false, error = "证明照片上传失败: ${e.message}") }
                        return@launch
                    }
                } else if (sws.proofFilePath != null) {
                    proofUploadMap[index] = sws.proofFilePath
                }
            }

            val items = state.students.mapIndexed { index, sws ->
                val status = sws.selectedStatus ?: CheckStatus.PRESENT
                StudentCheckItem(
                    studentId = sws.student.id,
                    checkStatus = status.name,
                    presetStatus = sws.student.presetStatus?.name,
                    presetOverridden = true,
                    statusSource = "KE",
                    remark = sws.remark.ifBlank { null },
                    proofFile = proofUploadMap[index]
                )
        }

            when (val result = repository.submitCheck(taskId, roomId, items)) {
                is Resource.Success -> {
                    val submittedIds = state.students.map { it.student.id }
                    val pendingChecks = repository.getPendingChecks(taskId, roomId)
                    val toDelete = pendingChecks.filter { it.studentId in submittedIds }.map { it.id }
                    if (toDelete.isNotEmpty()) repository.clearPendingChecks(toDelete)
                    _uiState.update { it.copy(isSubmitting = false, submitSuccess = true) }
                }
                is Resource.Error -> {
                    saveDirtyPendingChecks(state, taskId, roomId)
                    _uiState.update { it.copy(isSubmitting = false, error = result.message) }
                }
                is Resource.Unauthorized -> {
                    saveDirtyPendingChecks(state, taskId, roomId)
                    _uiState.update { it.copy(isSubmitting = false, error = result.message) }
                }
                is Resource.Loading -> {}
            }
        }
    }

    private suspend fun saveDirtyPendingChecks(state: RoomCheckUiState, taskId: String, roomId: String) {
        state.students.filter { it.isDirty }.forEach { s ->
            repository.savePendingCheck(
                taskId, roomId, s.student.id,
                (s.selectedStatus ?: CheckStatus.PRESENT).code,
                s.remark.ifBlank { null },
                s.proofLocalPath
            )
        }
    }
}