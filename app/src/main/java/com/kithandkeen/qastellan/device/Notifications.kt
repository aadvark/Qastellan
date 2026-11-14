package com.kithandkeen.qastellan.device

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kithandkeen.qastellan.R
import com.kithandkeen.qastellan.engine.Finding
import com.kithandkeen.qastellan.ui.MainActivity

object Notifications {
    const val CHANNEL_THREATS = "threats"
    const val CHANNEL_PROTECTION = "protection"
    const val PROTECTION_ID = 1

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_THREATS, context.getString(R.string.channel_threats), NotificationManager.IMPORTANCE_HIGH),
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PROTECTION, context.getString(R.string.channel_protection), NotificationManager.IMPORTANCE_MIN),
        )
    }

    fun protectionRunning(context: Context) =
        NotificationCompat.Builder(context, CHANNEL_PROTECTION)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(context.getString(R.string.protection_on))
            .setContentIntent(openApp(context, null))
            .setOngoing(true)
            .build()

    fun threat(context: Context, finding: Finding) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) {
            return
        }
        val text = when (finding) {
            is Finding.KnownThreat -> context.getString(R.string.threat_known, finding.detection)
            is Finding.RiskyApp -> finding.report.reasons.firstOrNull() ?: context.getString(R.string.threat_risky)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_THREATS)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(context.getString(R.string.threat_title, finding.title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(context, finding))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(finding.title.hashCode(), notification)
    }

    const val EXTRA_ALERT_PACKAGE = "alert_package"
    const val EXTRA_SHOW_RESULTS = "show_results"

    private fun openApp(context: Context, finding: Finding? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pkg = when (finding) {
            is Finding.KnownThreat -> finding.packageName
            is Finding.RiskyApp -> finding.packageName
            null -> null
        }
        if (pkg != null) intent.putExtra(EXTRA_ALERT_PACKAGE, pkg) else if (finding != null) intent.putExtra(EXTRA_SHOW_RESULTS, true)
        return PendingIntent.getActivity(
            context,
            pkg?.hashCode() ?: finding?.title?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
