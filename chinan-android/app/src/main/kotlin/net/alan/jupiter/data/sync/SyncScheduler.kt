package net.alan.jupiter.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 同步调度：
 * - 周期任务（15 分钟，KEEP 幂等注册）兜底重试；
 * - 一次性任务带 `NetworkType.CONNECTED` 约束 → 网络恢复即自动重试；
 * - 「立即同步」用手动触发器（REPLACE）。
 *
 * 退出登录只 `cancelAll()`，不删本地记录与队列（规则 3/4）。
 */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val UNIQUE_PERIODIC = "chinan_sync_periodic"
        const val UNIQUE_NETWORK_RETRY = "chinan_sync_network"
        const val UNIQUE_IMMEDIATE = "chinan_sync_now"
        const val TAG = "chinan-sync"
        private const val PERIOD_MINUTES = 15L
        private const val BACKOFF_SECONDS = 30L
    }

    fun ensureScheduled() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequest.Builder(SyncWorker::class.java, PERIOD_MINUTES, TimeUnit.MINUTES)
                .setConstraints(networkConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()
        )
        requestNetworkRetry()
    }

    /** 网络恢复自动重试（KEEP：不重复堆积）。 */
    fun requestNetworkRetry() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_NETWORK_RETRY,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequest.Builder(SyncWorker::class.java)
                .setConstraints(networkConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()
        )
    }

    /** 手动「立即同步」。 */
    fun requestImmediate() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_IMMEDIATE,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequest.Builder(SyncWorker::class.java)
                .setConstraints(networkConstraints())
                .addTag(TAG)
                .build()
        )
    }

    /** 退出登录：停 worker，保留本地数据。 */
    fun cancelAll() {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(UNIQUE_PERIODIC)
        workManager.cancelUniqueWork(UNIQUE_NETWORK_RETRY)
        workManager.cancelUniqueWork(UNIQUE_IMMEDIATE)
    }

    private fun networkConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}
