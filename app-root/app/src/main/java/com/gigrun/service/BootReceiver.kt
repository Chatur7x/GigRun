package com.gigrun.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Reschedules the daily maintenance check after reboot.
 * WorkManager persists periodically, but an explicit kick guarantees the
 * 24 h cadence restarts immediately instead of on next app open.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        try {
            MaintenanceAlertWorker.schedule(context.applicationContext)
            Log.i("BootReceiver", "maintenance check rescheduled after boot")
        } catch (e: Exception) {
            Log.e("BootReceiver", "reschedule failed", e)
        }
    }
}
