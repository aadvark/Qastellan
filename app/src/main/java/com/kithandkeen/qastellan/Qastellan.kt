package com.kithandkeen.qastellan

import android.app.Application
import android.content.Context
import com.kithandkeen.qastellan.device.Notifications
import com.kithandkeen.qastellan.engine.Definitions
import com.kithandkeen.qastellan.engine.Scanner
import com.kithandkeen.qastellan.update.DefinitionState
import com.kithandkeen.qastellan.update.DefinitionUpdater
import com.kithandkeen.qastellan.update.ManifestVerifier
import com.kithandkeen.qastellan.update.UpdateWork
import java.io.File

class Qastellan : Application() {

    lateinit var prefs: Prefs
        private set

    @Volatile
    var definitions: Definitions = Definitions.EMPTY
        private set

    val scanner by lazy { Scanner({ definitions }) }

    val updater by lazy {
        DefinitionUpdater(
            baseUrl = BuildConfig.DEFINITIONS_URL,
            rootDir = File(filesDir, "definitions").apply { mkdirs() },
            state = prefs,
            verifier = ManifestVerifier(getString(R.string.definitions_public_key)),
        )
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        Notifications.createChannels(this)
        reloadDefinitions()
        // Every start: check the app and the definitions for updates, then keep the daily check scheduled.
        UpdateWork.checkOnStartup(this)
        UpdateWork.scheduleDaily(this, prefs.wifiOnly)
    }

    fun reloadDefinitions() {
        val dir = updater.currentDir()
        definitions = if (dir != null) {
            runCatching { Definitions.load(dir, prefs.currentVersion ?: "unknown") }.getOrDefault(Definitions.EMPTY)
        } else {
            Definitions.EMPTY
        }
    }

    companion object {
        fun from(context: Context): Qastellan = context.applicationContext as Qastellan
    }
}

/** Small settings store; also remembers which definitions are installed. */
class Prefs(context: Context) : DefinitionState {
    private val sp = context.getSharedPreferences("qastellan", Context.MODE_PRIVATE)

    override var currentVersion: String?
        get() = sp.getString("defs_version", null)
        set(value) = sp.edit().putString("defs_version", value).apply()

    override var manifestEtag: String?
        get() = sp.getString("defs_etag", null)
        set(value) = sp.edit().putString("defs_etag", value).apply()

    var wifiOnly: Boolean
        get() = sp.getBoolean("wifi_only", true)
        set(value) = sp.edit().putBoolean("wifi_only", value).apply()

    var lastDefinitionsCheck: Long
        get() = sp.getLong("defs_checked", 0)
        set(value) = sp.edit().putLong("defs_checked", value).apply()

    var appUpdateVersion: String?
        get() = sp.getString("app_update", null)
        set(value) = sp.edit().putString("app_update", value).apply()

    var appUpdateUrl: String?
        get() = sp.getString("app_update_url", null)
        set(value) = sp.edit().putString("app_update_url", value).apply()
}
