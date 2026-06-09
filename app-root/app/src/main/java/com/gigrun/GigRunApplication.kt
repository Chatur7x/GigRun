package com.gigrun

import android.app.Application
import com.gigrun.service.MaintenanceAlertWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GigRunApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Schedule daily maintenance check worker
        MaintenanceAlertWorker.schedule(this)
    }
}
