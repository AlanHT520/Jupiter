package net.alan.jupiter.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.alan.jupiter.data.local.entity.SyncOutboxEntity
import net.alan.jupiter.data.local.entity.outboxSync
import net.alan.jupiter.data.repository.ChinanRepository
import net.alan.jupiter.domain.model.SyncState
import net.alan.jupiter.domain.sync.SyncPolicy
import net.alan.jupiter.domain.sync.TaskDateWindow
import javax.inject.Inject

data class OutboxRow(
    val clientOpId: String,
    val shortOpId: String,
    val taskId: String,
    val roomId: String,
    val taskDate: String?,
    val state: String,
    val retryCount: Int,
    val lastError: String?,
    val createdAt: Long
)

data class SyncUiState(
    val outbox: List<OutboxRow> = emptyList(),
    val pendingCount: Int = 0,
    val failedCount: Int = 0,
    val records: Int = 0,
    val acked: Int = 0,
    val isSyncing: Boolean = false,
    val fromDate: String = "",
    val toDate: String = "",
    val message: String? = null,
    val error: String? = null,
    val lastSyncAt: Long? = null
)

/**
 * A10「缓存与同步」：outbox 列表、立即同步、按 taskDate 时间段补交、清空数据。
 *
 * 补交只挑 QUEUED/FAILED（[SyncState.SUBMITTABLE]），已 ACKED 不重复提交；
 * taskDate 只用于筛选，报文里不改写它。
 */
@HiltViewModel
class SyncViewModel @Inject constructor(
    private val repository: ChinanRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    init {
        observeOutbox()
        viewModelScope.launch { refreshCountsInternal() }
    }

    private fun observeOutbox() {
        viewModelScope.launch {
            repository.observeOutbox().collect { rows ->
                _uiState.update {
                    it.copy(
                        outbox = rows.map { row -> row.toUiRow() },
                        pendingCount = rows.count { row -> row.outboxSync.isPending },
                        failedCount = rows.count { row -> row.outboxSync == SyncState.FAILED }
                    )
                }
            }
        }
    }

    fun updateFromDate(value: String) {
        _uiState.update { it.copy(fromDate = value, error = null) }
    }

    fun updateToDate(value: String) {
        _uiState.update { it.copy(toDate = value, error = null) }
    }

    fun clearRange() {
        _uiState.update { it.copy(fromDate = "", toDate = "", error = null) }
    }

    fun syncNow() {
        val state = _uiState.value
        if (state.isSyncing) return

        val from = state.fromDate.trim()
        val to = state.toDate.trim()
        if (from.isNotEmpty() && !TaskDateWindow.isValidIsoDate(from)) {
            _uiState.update { it.copy(error = "起始日期格式应为 yyyy-MM-dd") }
            return
        }
        if (to.isNotEmpty() && !TaskDateWindow.isValidIsoDate(to)) {
            _uiState.update { it.copy(error = "结束日期格式应为 yyyy-MM-dd") }
            return
        }
        if (from.isNotEmpty() && to.isNotEmpty() && from > to) {
            _uiState.update { it.copy(error = "起始日期不能晚于结束日期") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, error = null, message = null) }
            val report = runCatching {
                repository.syncNow(
                    fromTaskDate = from.takeIf { it.isNotEmpty() },
                    toTaskDate = to.takeIf { it.isNotEmpty() }
                )
            }.getOrNull()
            refreshCountsInternal()
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    lastSyncAt = System.currentTimeMillis(),
                    message = when {
                        report == null -> null
                        report.needsReauth -> "登录已失效，请重新登录后再同步"
                        report.attempted == 0 && report.remaining == 0 -> "队列为空，无需同步"
                        else -> "同步完成：ACK ${report.acked} · 失败 ${report.failed} · 剩余 ${report.remaining}"
                    },
                    error = if (report == null) "同步失败，请稍后重试" else null
                )
            }
        }
    }

    fun clearLocalData() {
        if (_uiState.value.isSyncing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, error = null, message = null) }
            val cleared = runCatching { repository.clearAllLocalData() }.getOrNull()
            refreshCountsInternal()
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    message = cleared?.let { data ->
                        "已清除 记录 ${data.records} · 队列 ${data.outbox} · 快照 ${data.tasks}/${data.students} · 照片 ${data.photos}"
                    },
                    error = if (cleared == null) "清除失败，请稍后重试" else null
                )
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }

    private suspend fun refreshCountsInternal() {
        val counts = runCatching { repository.localDataCounts() }.getOrNull() ?: return
        _uiState.update { it.copy(records = counts.records, acked = counts.acked) }
    }
}

private fun SyncOutboxEntity.toUiRow(): OutboxRow = OutboxRow(
    clientOpId = clientOpId,
    shortOpId = SyncPolicy.shortClientOpId(clientOpId),
    taskId = taskId,
    roomId = roomId,
    taskDate = taskDate,
    state = state,
    retryCount = retryCount,
    lastError = lastError,
    createdAt = createdAt
)
