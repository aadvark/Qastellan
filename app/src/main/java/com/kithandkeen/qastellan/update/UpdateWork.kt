package com.kithandkeen.qastellan.update

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kithandkeen.qastellan.BuildConfig
import com.kithandkeen.qastellan.Qastellan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Schedules the update checks: once at every app start, and once a day in the background. */
object UpdateWork {
    private const val STARTUP = "startup-check"
    private const val DAILY = "daily-definitions"
    private const val DOWNLOAD = "definitions-download"
    const val KEY_STARTUP = "startup"

    fun checkOnStartup(context: Context) {
        val request = OneTimeWorkRequestBuilder<UpdateWorker>()
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .setInputData(workDataOf(KEY_STARTUP to true))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(STARTUP, ExistingWorkPolicy.REPLACE, request)
    }

    fun scheduleDaily(context: Context, wifiOnly: Boolean) {
        val network = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints(requiredNetworkType = network, requiresBatteryNotLow = true))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** A new version was found on mobile data with "Wi-Fi only" on: download it once Wi-Fi is back. */
    fun downloadWhenUnmetered(context: Context) {
        val request = OneTimeWorkRequestBuilder<UpdateWorker>()
            .setConstraints(Constraints(requiredNetworkType = NetworkType.UNMETERED))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(DOWNLOAD, ExistingWorkPolicy.KEEP, request)
    }

    fun isUnmetered(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = Qastellan.from(applicationContext)
        val startup = inputData.getBoolean(UpdateWork.KEY_STARTUP, false)

        // 1. Definitions. The check itself is a few hundred bytes, so it runs on any network;
        //    the files download only when allowed.
        val allowDownload = !app.prefs.wifiOnly || UpdateWork.isUnmetered(applicationContext)
        when (val result = app.updater.update(allowDownload)) {
            is DefinitionUpdater.Result.Updated -> app.reloadDefinitions()
            is DefinitionUpdater.Result.Waiting -> UpdateWork.downloadWhenUnmetered(applicationContext)
            else -> Unit
        }
        app.prefs.lastDefinitionsCheck = System.currentTimeMillis()

        // 2. The app itself, on every start.
        if (startup) {
            when (val update = DistributionUpdateSource.check(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME, UrlConnectionHttp)) {
                is AppUpdate.Available -> {
                    app.prefs.appUpdateVersion = update.version
                    app.prefs.appUpdateUrl = update.whereToGet
                }
                AppUpdate.Current -> {
                    app.prefs.appUpdateVersion = null
                    app.prefs.appUpdateUrl = null
                }
                AppUpdate.Unknown -> Unit
            }
        }
        Result.success()
    }
}
