package com.kithandkeen.qastellan.device

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.kithandkeen.qastellan.Qastellan
import java.io.File
import java.util.concurrent.Executors

/**
 * Real-time protection: scans each new file in Downloads and each app as soon
 * as it is installed or updated. Runs as a low-priority foreground service so
 * Android keeps it alive.
 */
class ProtectionService : Service() {
    private val worker = Executors.newSingleThreadExecutor()
    private var observer: FileObserver? = null

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pkg = intent.data?.schemeSpecificPart ?: return
            worker.execute { scanPackage(pkg) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, Notifications.PROTECTION_ID, Notifications.protectionRunning(this), type)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED) // system broadcasts still arrive
        watchDownloads()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        observer?.stopWatching()
        unregisterReceiver(packageReceiver)
        worker.shutdown()
        super.onDestroy()
    }

    private fun watchDownloads() {
        if (!StorageAccess.canReadAllFiles(this)) return
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val mask = FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO
        val onEvent: (String?) -> Unit = { name ->
            if (name != null) worker.execute { scanFile(File(dir, name)) }
        }
        observer = if (Build.VERSION.SDK_INT >= 29) {
            object : FileObserver(dir, mask) {
                override fun onEvent(event: Int, path: String?) = onEvent(path)
            }
        } else {
            @Suppress("DEPRECATION")
            object : FileObserver(dir.absolutePath, mask) {
                override fun onEvent(event: Int, path: String?) = onEvent(path)
            }
        }.also { it.startWatching() }
    }

    private fun scanFile(file: File) {
        val app = Qastellan.from(this)
        app.scanner.scanFile(file)?.let {
            app.state.reportRealtime(it)
            Notifications.threat(this, it)
        }
    }

    private fun scanPackage(packageName: String) {
        val app = Qastellan.from(this)
        if (!app.prefs.scanNewApps) return
        val (facts, apk) = AppInspector(this).inspect(packageName) ?: return
        val finding = app.scanner.scanApp(facts, apk)
        if (finding != null) {
            app.state.reportRealtime(finding)
            Notifications.threat(this, finding)
        } else {
            app.state.history.add(
                com.kithandkeen.qastellan.data.HistoryEvent(
                    com.kithandkeen.qastellan.data.HistoryEvent.Kind.NEW_APP, "New app checked", "${facts.label} · safe",
                ),
            )
        }
    }

    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ProtectionService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ProtectionService::class.java))
        }
    }
}

/** Restarts real-time protection after the phone reboots or the app updates. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Qastellan.from(context).prefs
        if ((intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) && prefs.onboarded && prefs.realtime) {
            ProtectionService.start(context)
        }
    }
}
