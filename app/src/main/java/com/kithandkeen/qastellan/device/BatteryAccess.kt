package com.kithandkeen.qastellan.device

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Battery optimisation exemption. Many phone makers (Xiaomi, Oppo, Vivo,
 * Huawei, Samsung) stop background apps to save battery, which would stop
 * real-time protection. Exempting Qastellan keeps it running.
 */
object BatteryAccess {
    fun isExempt(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) ?: true

    /** The system dialog that asks to let Qastellan run in the background. */
    @SuppressLint("BatteryLife") // a malware scanner's real-time protection must keep running
    fun requestIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    /** Fallback: the list of battery optimisation settings for all apps. */
    fun settingsIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}
