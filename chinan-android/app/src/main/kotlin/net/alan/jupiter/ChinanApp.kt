package net.alan.jupiter

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import net.alan.jupiter.data.sync.SyncScheduler
import javax.inject.Inject

@HiltAndroidApp
class ChinanApp : Application() {

    @Inject
    lateinit var syncScheduler: SyncScheduler

    override fun onCreate() {
        super.onCreate()
        // 周期同步 + 网络恢复重试：App 启动即注册（KEEP 幂等，重复启动不叠加）
        syncScheduler.ensureScheduled()
    }
}
