package net.alan.jupiter.data.sync

import android.util.Log
import kotlinx.coroutines.CancellationException
import net.alan.jupiter.data.local.LocalStore
import net.alan.jupiter.data.local.TokenManager
import net.alan.jupiter.data.local.entity.SyncOutboxEntity
import net.alan.jupiter.data.remote.tcp.AuthExpiredException
import net.alan.jupiter.data.remote.tcp.TcpClient
import net.alan.jupiter.domain.model.SyncState
import net.alan.jupiter.domain.sync.PendingSubmission
import net.alan.jupiter.domain.sync.SubmitGrouper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 上行同步引擎：outbox → SUBMIT_CHECK → ACK / retry。
 *
 * - 每次（重新）发送都复用 outbox 行里的 `clientOpId`，保证服务端按
 *   (clientOpId, studentId) 去重后不会写出重复记录；
 * - 超时/断网/失败不抛给 UI：记录保持 QUEUED，retryCount+1，>3 升 FAILED；
 * - 补交只按 `taskDate` 过滤，报文里不带 taskDate（服务端负责，客户端不改写）。
 */
@Singleton
class SyncEngine @Inject constructor(
    private val tcpClient: TcpClient,
    private val tokenManager: TokenManager,
    private val localStore: LocalStore,
    private val networkMonitor: NetworkMonitor
) {

    companion object {
        private const val TAG = "SyncEngine"
    }

    data class BatchResult(
        val acked: Boolean,
        val needsReauth: Boolean = false,
        val message: String? = null,
        val state: SyncState = SyncState.QUEUED
    )

    data class SyncReport(
        val attempted: Int = 0,
        val acked: Int = 0,
        val failed: Int = 0,
        val remaining: Int = 0,
        val groupCount: Int = 0,
        val offline: Boolean = false,
        val needsReauth: Boolean = false,
        val message: String? = null
    )

    /**
     * 立即提交一个批次（房间一次提交 = 一个 clientOpId）。
     * 失败只改本地状态，不抛异常，UI 直接按“已查 + 待同步”展示。
     */
    suspend fun submitBatch(ownerId: String, clientOpId: String): BatchResult {
        val token = tokenManager.getToken()
        if (token.isNullOrBlank()) {
            return BatchResult(acked = false, needsReauth = true, message = "未登录，无法提交")
        }
        // 行已不存在说明之前已经 ACK（重复调用无需再发）
        val row = localStore.outboxRow(ownerId, clientOpId)
            ?: return BatchResult(acked = true, state = SyncState.ACKED)

        val items = LocalStore.decodeItems(row.payload)
        if (items.isEmpty()) {
            val state = localStore.markFailure(ownerId, clientOpId, "本地队列内容损坏")
            return BatchResult(acked = false, message = "本地队列内容损坏", state = state)
        }

        return try {
            tcpClient.submitCheck(token, row.taskId, row.roomId, items, clientOpId)
            localStore.markAcked(ownerId, clientOpId)
            BatchResult(acked = true, state = SyncState.ACKED)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AuthExpiredException) {
            val message = e.message?.takeIf { it.isNotBlank() } ?: "登录已失效"
            val state = localStore.markFailure(ownerId, clientOpId, message)
            BatchResult(acked = false, needsReauth = true, message = message, state = state)
        } catch (e: Exception) {
            val message = e.message?.takeIf { it.isNotBlank() } ?: "网络异常"
            val state = localStore.markFailure(ownerId, clientOpId, message)
            BatchResult(acked = false, message = message, state = state)
        }
    }

    /**
     * 同步队列（WorkManager 周期/网络恢复/手动「立即同步」共用）。
     *
     * @param fromTaskDate 可选的补交时间段起点（ISO 日期，含）
     * @param toTaskDate 可选的补交时间段终点（ISO 日期，含）
     * @param includeFailed 手动同步时包含 FAILED（人工重试）；自动同步只发 QUEUED
     */
    suspend fun syncPending(
        fromTaskDate: String? = null,
        toTaskDate: String? = null,
        includeFailed: Boolean = false
    ): SyncReport {
        val ownerId = localStore.ensureOwner()
        if (!networkMonitor.isOnline()) {
            return SyncReport(
                remaining = localStore.pendingOutboxCount(),
                offline = true,
                message = "当前离线，稍后自动重试"
            )
        }
        if (tokenManager.getToken().isNullOrBlank()) {
            return SyncReport(
                remaining = localStore.pendingOutboxCount(),
                needsReauth = true,
                message = "未登录，无法同步"
            )
        }

        val rows = localStore.outboxForSync(includeFailed)
        if (rows.isEmpty()) {
            return SyncReport(remaining = localStore.pendingOutboxCount())
        }

        val selected = SubmitGrouper.select(
            submissions = rows.map { it.toPendingSubmission() },
            fromIso = fromTaskDate,
            toIso = toTaskDate
        )
        if (selected.isEmpty()) {
            return SyncReport(remaining = localStore.pendingOutboxCount())
        }

        val groups = SubmitGrouper.groupByTaskDate(selected)
        var attempted = 0
        var acked = 0
        var failed = 0
        var lastError: String? = null
        var needsReauth = false
        var stop = false

        for (group in groups) {
            if (stop) break
            Log.d(TAG, "sync group taskDate=${group.taskDate} ops=${group.submissions.size}")
            for (op in group.submissions) {
                if (stop) break
                attempted++
                val result = submitBatch(ownerId, op.clientOpId)
                if (result.acked) {
                    acked++
                } else {
                    failed++
                    lastError = result.message
                    if (result.needsReauth) {
                        needsReauth = true
                        stop = true
                    }
                }
            }
        }

        return SyncReport(
            attempted = attempted,
            acked = acked,
            failed = failed,
            remaining = localStore.pendingOutboxCount(),
            groupCount = groups.size,
            offline = false,
            needsReauth = needsReauth,
            message = lastError
        )
    }
}

private fun SyncOutboxEntity.toPendingSubmission(): PendingSubmission = PendingSubmission(
    clientOpId = clientOpId,
    taskId = taskId,
    roomId = roomId,
    taskDate = taskDate,
    retryCount = retryCount,
    createdAt = createdAt
)
