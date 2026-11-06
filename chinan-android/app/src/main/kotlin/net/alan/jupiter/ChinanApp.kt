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
        syncScheduler.ensureScheduled()
    }
}
