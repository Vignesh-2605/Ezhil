package com.ezhil.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.ezhil.app.data.local.EzhilDatabase
import com.ezhil.app.data.local.seedDemoDataIfNeeded
import com.ezhil.app.sync.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class EzhilApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var db: EzhilDatabase

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Debug builds only. This upserts fixed-id demo rows on every launch,
        // which in a real classroom would keep resurrecting fake students and
        // overwriting a teacher's edits to them.
        if (BuildConfig.DEBUG) {
            appScope.launch { seedDemoDataIfNeeded(db) }
        }

        // Register the periodic offline sync. Nothing called this before, so
        // the whole sync engine — roster pull and data push alike — never ran.
        SyncWorker.schedule(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
