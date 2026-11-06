package net.alan.jupiter.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException

/** WorkManager 里取 Hilt 依赖的入口（Worker 由默认 WorkerFactory 反射创建）。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncWorkerEntryPoint {
    fun syncEngine(): SyncEngine
}

/**
 * 网络恢复 / 每 15 分钟触发的同步 worker：
 * 读 outbox，按 taskDate 分组、按 clientOpId 复用幂等键上行。
 *
 * 失败不阻塞：retryCount 在 Room 里累加，超过 3 次标 FAILED，等待人工入口。
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "SyncWorker"
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            SyncWorkerEntryPoint::class.java
        )
        return try {
            val report = entryPoint.syncEngine().syncPending()
            Log.d(
                TAG,
                "sync finished attempted=${report.attempted} acked=${report.acked} " +
                    "failed=${report.failed} remaining=${report.remaining} " +
                    "groups=${report.groupCount} offline=${report.offline} " +
                    "reauth=${report.needsReauth}"
            )
            // 离线由 NetworkType.CONNECTED 约束兜底；这里只在真的没网时交给 WorkManager 退避重试
            if (report.offline) Result.retry() else Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "sync failed", e)
            Result.retry()
        }
    }
}
