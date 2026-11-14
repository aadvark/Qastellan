package com.kithandkeen.qastellan

import android.app.Application
import android.content.Context
import com.kithandkeen.qastellan.data.AppState
import com.kithandkeen.qastellan.device.Notifications
import com.kithandkeen.qastellan.device.ProtectionService
import com.kithandkeen.qastellan.ui.theme.Appearance
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

    /** Shared state behind every screen. */
    val state by lazy { AppState(this) }

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
        if (prefs.autoUpdate) UpdateWork.scheduleDaily(this, prefs.wifiOnly) else UpdateWork.cancelDaily(this)
        if (prefs.onboarded && prefs.realtime) runCatching { ProtectionService.start(this) }
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

    var appearance: Appearance
        get() = runCatching { Appearance.valueOf(sp.getString("appearance", "SYSTEM")!!) }.getOrDefault(Appearance.SYSTEM)
        set(value) = sp.edit().putString("appearance", value.name).apply()

    var realtime: Boolean
        get() = sp.getBoolean("realtime", true)
        set(value) = sp.edit().putBoolean("realtime", value).apply()

    var scanNewApps: Boolean
        get() = sp.getBoolean("scan_new_apps", true)
        set(value) = sp.edit().putBoolean("scan_new_apps", value).apply()

    var autoUpdate: Boolean
        get() = sp.getBoolean("auto_update", true)
        set(value) = sp.edit().putBoolean("auto_update", value).apply()

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(value) = sp.edit().putBoolean("onboarded", value).apply()

    var lastScan: Long
        get() = sp.getLong("last_scan", 0)
        set(value) = sp.edit().putLong("last_scan", value).apply()

    /** Apps and files the user marked as trusted ("pkg:..." / "file:..."). */
    var ignored: Set<String>
        get() = sp.getStringSet("ignored", emptySet())!!.toSet()
        set(value) = sp.edit().putStringSet("ignored", value).apply()
}
