package net.alan.jupiter.ui.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import net.alan.jupiter.data.remote.dto.StudentCheckItem
import net.alan.jupiter.data.repository.ChinanRepository
import net.alan.jupiter.domain.model.CheckStatus
import net.alan.jupiter.domain.model.StudentDetail
import net.alan.jupiter.util.Resource
import net.alan.jupiter.util.ImageCompressor
import java.io.File
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

sealed interface UploadState {
    data object Idle : UploadState
    data class Uploading(val localPath: String, val job: Job) : UploadState
    data class Uploaded(val localPath: String, val serverPath: String) : UploadState
    data class Failed(val localPath: String, val reason: String) : UploadState
    data object Cancelled : UploadState
}

@HiltViewModel
class RoomCheckViewModel @Inject constructor(
    private val repository: ChinanRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoomCheckUiState())
    val uiState: StateFlow<RoomCheckUiState> = _uiState.asStateFlow()

    private val uploadStates = mutableMapOf<String, UploadState>()
    private val uploadSemaphore = Semaphore(3)
    private val draftJobs = ConcurrentHashMap<Int, Job>()

    private var currentTaskId: String = ""
    private var currentRoomId: String = ""

    fun loadRoom(taskId: String, roomId: String) {
        currentTaskId = taskId
        currentRoomId = roomId
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
                            proofFilePath = pendingCheck?.proofFilePath ?: s.proofFilePath,
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
        persistDraft(studentIndex)
    }

    fun updateRemark(studentIndex: Int, remark: String) {
        _uiState.update { state ->
            val updated = state.students.toMutableList()
            updated[studentIndex] = updated[studentIndex].copy(remark = remark, isDirty = true)
            state.copy(students = updated)
        }
        persistDraft(studentIndex)
    }

    fun updateProofPhoto(studentIndex: Int, newLocalPath: String) {
        val sws = _uiState.value.students.getOrNull(studentIndex) ?: return
        val studentId = sws.student.id

        // 取消旧上传
        (uploadStates[studentId] as? UploadState.Uploading)?.job?.cancel()
        uploadStates[studentId] = UploadState.Idle

        val oldLocal = sws.proofLocalPath
        val oldServer = sws.proofFilePath

        _uiState.update { state ->
            val updated = state.students.toMutableList()
            updated[studentIndex] = updated[studentIndex].copy(
                proofLocalPath = newLocalPath,
                proofFilePath = null,
                isDirty = true
            )
            state.copy(students = updated)
        }
        persistDraft(studentIndex)

        // 删旧本地文件
        if (oldLocal != null) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { File(oldLocal).delete() }
            }
        }
        // 旧的服务端证明等新照片上传成功后再删，避免上传失败导致"证据丢失"
        startUpload(studentIndex, replacedServerPath = oldServer)
    }

    fun clearProofPhoto(studentIndex: Int) {
        val sws = _uiState.value.students.getOrNull(studentIndex) ?: return
        val studentId = sws.student.id

        (uploadStates[studentId] as? UploadState.Uploading)?.job?.cancel()
        uploadStates[studentId] = UploadState.Idle

        val oldLocal = sws.proofLocalPath
        val oldServer = sws.proofFilePath

        _uiState.update { state ->
            val updated = state.students.toMutableList()
            updated[studentIndex] = updated[studentIndex].copy(
                proofLocalPath = null,
                proofFilePath = null,
                isDirty = true
            )
            state.copy(students = updated)
        }
        persistDraft(studentIndex)

        if (oldLocal != null) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { File(oldLocal).delete() }
            }
        }
        if (oldServer != null) {
            viewModelScope.launch {
                runCatching {
                    repository.deleteProof(oldServer, currentTaskId, studentId)
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun startUpload(studentIndex: Int, replacedServerPath: String? = null) {
        val sws = _uiState.value.students.getOrNull(studentIndex) ?: return
        val studentId = sws.student.id
        val localPath = sws.proofLocalPath ?: return

        (uploadStates[studentId] as? UploadState.Uploading)?.job?.cancel()

        val job = viewModelScope.launch {
            val result = runCatching {
                uploadSemaphore.withPermit {
                    val (base64, ext) = withContext(Dispatchers.Default) {
                        // 上传前压缩到最长边 1024px，避免超出服务端帧大小限制
                        val bytes = runCatching {
                            ImageCompressor.compressToJpegBytes(File(localPath))
                        }.getOrElse { File(localPath).readBytes() }
                        android.util.Base64.encodeToString(
                            bytes, android.util.Base64.NO_WRAP
                        ) to ".jpg"
                    }
                    repository.uploadProof("${currentTaskId}_$studentId", base64, ext)
                }
            }

            if (!isActive) return@launch

            // 快照守卫：用户又换了照片就丢弃这次结果
            val currentPath = _uiState.value.students
                .getOrNull(studentIndex)?.proofLocalPath
            if (currentPath != localPath) return@launch

            result.onSuccess { resource ->
                when (resource) {
                    is Resource.Success -> {
                        uploadStates[studentId] =
                            UploadState.Uploaded(localPath, resource.data)
                        _uiState.update { state ->
                            val updated = state.students.toMutableList()
                            if (studentIndex in updated.indices) {
                                updated[studentIndex] = updated[studentIndex].copy(
                                    proofFilePath = resource.data
                                )
                            }
                            state.copy(students = updated)
                        }
                        persistDraft(studentIndex)
                        // 新照片已成功落库，再清理被替换掉的旧服务端文件
                        if (replacedServerPath != null && replacedServerPath != resource.data) {
                            repository.deleteProof(replacedServerPath, currentTaskId, studentId)
                        }
                    }
                    is Resource.Error -> {
                        uploadStates[studentId] =
                            UploadState.Failed(localPath, resource.message)
                    }
                    is Resource.Unauthorized -> {
                        uploadStates[studentId] =
                            UploadState.Failed(localPath, resource.message)
                    }
                    is Resource.Loading -> {}
                }
            }.onFailure { e ->
                if (e is CancellationException) {
                    uploadStates[studentId] = UploadState.Cancelled
                } else {
                    uploadStates[studentId] = UploadState.Failed(
                        localPath, e.message ?: "上传失败"
                    )
                }
            }
        }
        uploadStates[studentId] = UploadState.Uploading(localPath, job)
    }

    /**
     * 带防抖的草稿落盘。单位时间内对同一学生的多次修改只触发一次写入。
     * 防抖窗口 300ms，cancel-before-restart 模式。
     */
    private fun persistDraft(studentIndex: Int) {
        draftJobs[studentIndex]?.cancel()

        val sws = _uiState.value.students.getOrNull(studentIndex) ?: return
        if (currentTaskId.isBlank() || currentRoomId.isBlank()) return

        val taskId = currentTaskId
        val roomId = currentRoomId
        val studentId = sws.student.id
        val statusCode = (sws.selectedStatus ?: CheckStatus.PRESENT).code
        val remark = sws.remark.ifBlank { null }
        val localPath = sws.proofLocalPath
        val serverPath = sws.proofFilePath

        draftJobs[studentIndex] = viewModelScope.launch {
            delay(300L)
            runCatching {
                repository.savePendingCheck(
                    taskId, roomId, studentId,
                    statusCode, remark, localPath, serverPath
                )
            }
        }
    }

    /**
     * 立即落盘所有待处理草稿，在提交查寝前调用，确保所有修改已持久化。
     */
    private suspend fun flushDrafts() {
        draftJobs.values.forEach { it.cancel() }
        draftJobs.clear()
        _uiState.value.students.forEach { sws ->
            if (sws.isDirty) {
                runCatching {
                    repository.savePendingCheck(
                        currentTaskId, currentRoomId, sws.student.id,
                        (sws.selectedStatus ?: CheckStatus.PRESENT).code,
                        sws.remark.ifBlank { null },
                        sws.proofLocalPath,
                        sws.proofFilePath
                    )
                }
            }
        }
    }

    fun submitChecks(taskId: String, roomId: String) {
        if (_uiState.value.isSubmitting) return
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }

            // 提交前落盘所有防抖草稿，防止漏存
            flushDrafts()

            // 等所有进行中的上传，最多 20 秒
            val uploadResults = state.students.mapIndexed { _, sws ->
                async {
                    val st = uploadStates[sws.student.id]
                    if (st is UploadState.Uploading) {
                        withTimeoutOrNull(20_000) { st.job.join() }
                        uploadStates[sws.student.id]
                    } else st
                }
            }.awaitAll()

            val items = state.students.mapIndexed { index, sws ->
                val status = sws.selectedStatus ?: CheckStatus.PRESENT
                val serverPath = when (val st = uploadResults[index]) {
                    is UploadState.Uploaded -> st.serverPath
                    else -> sws.proofFilePath
                }
                StudentCheckItem(
                    studentId = sws.student.id,
                    checkStatus = status.name,
                    presetStatus = sws.student.presetStatus?.name,
                    presetOverridden = true,
                    statusSource = "KE",
                    remark = sws.remark.ifBlank { null },
                    proofFile = serverPath
                )
            }

            when (val result = repository.submitCheck(taskId, roomId, items)) {
                is Resource.Success -> {
                    val submittedIds = state.students.map { it.student.id }
                    val pendingChecks = repository.getPendingChecks(taskId, roomId)
                    val toDelete = pendingChecks
                        .filter { it.studentId in submittedIds }
                        .map { it.id }
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

    private suspend fun saveDirtyPendingChecks(
        state: RoomCheckUiState, taskId: String, roomId: String
    ) {
        state.students.filter { it.isDirty }.forEach { s ->
            repository.savePendingCheck(
                taskId, roomId, s.student.id,
                (s.selectedStatus ?: CheckStatus.PRESENT).code,
                s.remark.ifBlank { null },
                s.proofLocalPath,
                s.proofFilePath
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        uploadStates.values.forEach { (it as? UploadState.Uploading)?.job?.cancel() }
        uploadStates.clear()
    }
}