package com.gigrun.core.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object BatteryOptimizationHelper {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun getBatteryOptimizationIntent(context: Context): Intent {
        val manufacturer = Build.MANUFACTURER.lowercase(java.util.Locale.ROOT)
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> {
                // MIUI AutoStart
                Intent().setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                ).let { if (isIntentAvailable(context, it)) it else batteryIntent(context) }
            }
            manufacturer.contains("samsung") -> batteryIntent(context)
            manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") -> {
                Intent().setClassName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                ).let { if (isIntentAvailable(context, it)) it else batteryIntent(context) }
            }
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> {
                Intent().setClassName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                ).let { if (isIntentAvailable(context, it)) it else batteryIntent(context) }
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                Intent().setClassName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"
                ).let { if (isIntentAvailable(context, it)) it else batteryIntent(context) }
            }
            else -> batteryIntent(context)
        }
    }

    // Play-policy-safe fallback: the generic settings screen, not a direct
    // exemption request (direct requests trigger Play review/removal).
    private fun batteryIntent(context: Context): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            `package` = context.packageName
        }

    private fun isIntentAvailable(context: Context, intent: Intent): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.queryIntentActivities(
                    intent,
                    android.content.pm.PackageManager.ResolveInfoFlags.of(0)
                ).isNotEmpty()
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()
            }
        } catch (_: Exception) { false }
    }

    fun getManufacturerSteps(): String {
        val m = Build.MANUFACTURER.lowercase(java.util.Locale.ROOT)
        return when {
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") ->
                "Settings → Apps → Manage apps → GigRun → Autostart → Enable\nSettings → Battery → App battery saver → No restrictions"
            m.contains("samsung") ->
                "Settings → Apps → GigRun → Battery → Unrestricted\nSettings → Battery → Background usage limits → Never sleeping apps → Add GigRun"
            m.contains("oppo") || m.contains("realme") || m.contains("oneplus") ->
                "Settings → Battery → App battery management → GigRun → Allow background activity\nSettings → App management → Autostart → Enable GigRun"
            m.contains("vivo") || m.contains("iqoo") ->
                "Settings → Battery → Background power consumption → GigRun → Allow\nSettings → More settings → Autostart → Enable GigRun"
            m.contains("huawei") || m.contains("honor") ->
                "Settings → Battery → App launch → GigRun → Manage manually → Enable all toggles"
            else ->
                "Settings → Battery → Battery optimization → All apps → GigRun → Don't optimize"
        }
    }
}
