package com.kithandkeen.qastellan.data

import android.content.Context
import com.kithandkeen.qastellan.Qastellan
import com.kithandkeen.qastellan.device.AppReport
import com.kithandkeen.qastellan.device.AppReports
import com.kithandkeen.qastellan.device.AuditCheck
import com.kithandkeen.qastellan.device.DeviceScanner
import com.kithandkeen.qastellan.device.ProtectionService
import com.kithandkeen.qastellan.device.SecurityAudit
import com.kithandkeen.qastellan.engine.Finding
import com.kithandkeen.qastellan.update.UpdateWork
import com.kithandkeen.qastellan.ui.theme.Appearance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

sealed class ScanState {
    object Idle : ScanState()
    data class Running(val progress: DeviceScanner.Progress?, val singleFile: Boolean = false) : ScanState()
    data class Done(val summary: DeviceScanner.Summary) : ScanState()
}

/** What the user chose to do with a finding. */
enum class Resolution { UNINSTALL, QUARANTINE, DELETE, IGNORE }

/**
 * The app's single source of truth for the screens: scan progress, open threats,
 * settings, history and quarantine. Long work runs on a background scope.
 */
class AppState(private val context: Context) {
    private val app get() = Qastellan.from(context)
    private val prefs get() = app.prefs
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val history = HistoryStore(File(context.filesDir, "history.json"))
    val quarantine = QuarantineStore(File(context.filesDir, "quarantine"))
    val reports by lazy { AppReports(context) { app.definitions.trackerDetector } }

    private val _scan = MutableStateFlow<ScanState>(ScanState.Idle)
    val scan: StateFlow<ScanState> = _scan.asStateFlow()
    private var scanJob: Job? = null

    /** Threats found and not yet dealt with. */
    private val _open = MutableStateFlow<List<Finding>>(emptyList())
    val openThreats: StateFlow<List<Finding>> = _open.asStateFlow()

    private val _appearance = MutableStateFlow(prefs.appearance)
    val appearance: StateFlow<Appearance> = _appearance.asStateFlow()

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _definitions = MutableStateFlow(readDefinitions())
    val definitions: StateFlow<DefinitionsInfo> = _definitions.asStateFlow()

    private val _audit = MutableStateFlow<List<AuditCheck>>(emptyList())
    val audit: StateFlow<List<AuditCheck>> = _audit.asStateFlow()

    private val _apps = MutableStateFlow<List<AppReport>?>(null)
    val apps: StateFlow<List<AppReport>?> = _apps.asStateFlow()

    data class Settings(
        val realtime: Boolean,
        val scanNewApps: Boolean,
        val autoUpdate: Boolean,
        val wifiOnly: Boolean,
        val onboarded: Boolean,
        val lastScan: Long,
        val appUpdateVersion: String?,
        val appUpdateUrl: String?,
    )

    data class DefinitionsInfo(val version: String, val signatures: Int, val lastCheck: Long, val checking: Boolean = false)

    // ------------------------------------------------------------- scanning

    fun startScan() {
        if (scanJob?.isActive == true) return
        _scan.value = ScanState.Running(null)
        scanJob = scope.launch {
            val job = coroutineContext[Job]
            val summary = DeviceScanner(context).scan(keepGoing = { job?.isActive == true }) { p -> _scan.value = ScanState.Running(p) }
            val visible = summary.findings.filterNot { isIgnored(it) }
            val result = summary.copy(findings = visible)
            _open.value = visible
            if (!summary.cancelled) {
                prefs.lastScan = System.currentTimeMillis()
                _settings.value = readSettings()
                history.add(
                    if (visible.isEmpty()) {
                        HistoryEvent(HistoryEvent.Kind.SCAN_CLEAN, "Full scan · no threats", "${summary.apps} apps and ${summary.files} files checked")
                    } else {
                        HistoryEvent(HistoryEvent.Kind.SCAN_THREATS, "Full scan · ${visible.size} ${if (visible.size == 1) "threat" else "threats"}", visible.joinToString { it.title })
                    },
                )
            }
            _scan.value = ScanState.Done(result)
        }
    }

    /** Show the scanning screen as busy while a picked file is copied in. */
    fun markScanStarting() {
        if (scanJob?.isActive != true) _scan.value = ScanState.Running(null, singleFile = true)
    }

    fun cancelScan() {
        scanJob?.cancel()
        _scan.value = ScanState.Idle
    }

    /** Scan a single file the user picked (Home > File). */
    fun scanPickedFile(file: File, displayName: String, sourceUri: String) = scope.launch {
        val finding = app.scanner.scanFile(file)?.let {
            // The scanned file is a temporary copy; remember where the real one is.
            if (it is Finding.KnownThreat) it.copy(title = displayName, path = null, sourceUri = sourceUri) else it
        }
        val summary = DeviceScanner.Summary(listOfNotNull(finding), 0, 1, 0, false, fileName = displayName)
        if (finding != null) {
            _open.update { (it + finding).distinctBy(::keyOf) }
            history.add(HistoryEvent(HistoryEvent.Kind.THREAT, "File check · threat", "$displayName · ${(finding as? Finding.KnownThreat)?.detection ?: ""}"))
        } else {
            history.add(HistoryEvent(HistoryEvent.Kind.SCAN_CLEAN, "File check · clean", displayName))
        }
        file.delete()
        _scan.value = ScanState.Done(summary)
    }

    /** Called by real-time protection when it finds something. */
    fun reportRealtime(finding: Finding) {
        if (isIgnored(finding)) return
        _open.update { (it + finding).distinctBy(::keyOf) }
        history.add(HistoryEvent(HistoryEvent.Kind.THREAT, "Real-time protection · threat", finding.title))
    }

    fun resolve(finding: Finding, how: Resolution): Boolean {
        val ok = when (how) {
            Resolution.UNINSTALL -> true // the system uninstall dialog is shown by the screen
            Resolution.DELETE -> {
                val uri = (finding as? Finding.KnownThreat)?.sourceUri?.let(android.net.Uri::parse)
                val deleted = uri != null && runCatching {
                    android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri)
                }.recoverCatching { context.contentResolver.delete(uri!!, null, null) > 0 }.getOrDefault(false)
                if (deleted) history.add(HistoryEvent(HistoryEvent.Kind.ACTION, "Deleted file", finding.title))
                deleted
            }
            Resolution.IGNORE -> {
                prefs.ignored = prefs.ignored + keyOf(finding)
                history.add(HistoryEvent(HistoryEvent.Kind.ACTION, "Marked as trusted", finding.title))
                true
            }
            Resolution.QUARANTINE -> {
                val path = (finding as? Finding.KnownThreat)?.path
                val detection = (finding as? Finding.KnownThreat)?.detection ?: "Risky file"
                val item = path?.let { quarantine.quarantine(File(it), detection) }
                if (item != null) history.add(HistoryEvent(HistoryEvent.Kind.QUARANTINE, "Moved to quarantine", "${item.name} · $detection"))
                item != null
            }
        }
        if (ok) _open.update { list -> list.filterNot { keyOf(it) == keyOf(finding) } }
        return ok
    }

    /**
     * Drop threats that are no longer on the phone: apps that were uninstalled
     * and files that were deleted or moved (in Qastellan or any other app).
     */
    fun refreshThreats() {
        val pm = context.packageManager
        val gone = _open.value.filter { f ->
            val pkg = packageOf(f)
            val known = f as? Finding.KnownThreat
            when {
                pkg != null -> runCatching { pm.getPackageInfo(pkg, 0) }.isFailure
                known?.path != null && known.sourceUri == null -> !File(known.path).exists()
                else -> false
            }
        }
        if (gone.isEmpty()) return
        _open.update { list -> list.filterNot { f -> gone.any { keyOf(it) == keyOf(f) } } }
        gone.forEach { f ->
            history.add(HistoryEvent(HistoryEvent.Kind.ACTION, if (packageOf(f) != null) "Threat removed: app uninstalled" else "Threat removed: file deleted", f.title))
        }
    }

    fun refreshAfterUninstall() = refreshThreats()

    fun clearScanResult() {
        if (_scan.value is ScanState.Done) _scan.value = ScanState.Idle
    }

    // ------------------------------------------------------------- apps, audit

    fun loadApps(force: Boolean = false) {
        if (_apps.value != null && !force) return
        scope.launch { _apps.value = reports.all() }
    }

    fun runAudit() = scope.launch { _audit.value = SecurityAudit(context).run() }

    // ------------------------------------------------------------- settings

    fun setAppearance(a: Appearance) {
        prefs.appearance = a
        _appearance.value = a
    }

    fun setRealtime(on: Boolean) {
        prefs.realtime = on
        if (on) ProtectionService.start(context) else ProtectionService.stop(context)
        _settings.value = readSettings()
    }

    fun setScanNewApps(on: Boolean) {
        prefs.scanNewApps = on
        _settings.value = readSettings()
    }

    fun setAutoUpdate(on: Boolean) {
        prefs.autoUpdate = on
        if (on) UpdateWork.scheduleDaily(context, prefs.wifiOnly) else UpdateWork.cancelDaily(context)
        _settings.value = readSettings()
    }

    fun setWifiOnly(on: Boolean) {
        prefs.wifiOnly = on
        if (prefs.autoUpdate) UpdateWork.scheduleDaily(context, on)
        _settings.value = readSettings()
    }

    fun finishOnboarding() {
        prefs.onboarded = true
        _settings.value = readSettings()
        if (prefs.realtime) ProtectionService.start(context)
    }

    fun checkForUpdates() {
        _definitions.update { it.copy(checking = true) }
        UpdateWork.checkOnStartup(context)
    }

    /** Called by the update worker when it finishes. */
    fun onUpdateChecked(newDefinitions: String?) {
        if (newDefinitions != null) {
            history.add(HistoryEvent(HistoryEvent.Kind.DEFINITIONS, "Definitions updated", "Version $newDefinitions"))
        }
        _definitions.value = readDefinitions()
        _settings.value = readSettings()
    }

    // ------------------------------------------------------------- helpers

    private fun readSettings() = Settings(
        prefs.realtime, prefs.scanNewApps, prefs.autoUpdate, prefs.wifiOnly, prefs.onboarded, prefs.lastScan,
        prefs.appUpdateVersion, prefs.appUpdateUrl,
    )

    private fun readDefinitions() = DefinitionsInfo(app.definitions.version, app.definitions.signatureCount, prefs.lastDefinitionsCheck)

    private fun isIgnored(f: Finding) = keyOf(f) in prefs.ignored

    companion object {
        fun packageOf(f: Finding): String? = when (f) {
            is Finding.KnownThreat -> f.packageName
            is Finding.RiskyApp -> f.packageName
        }

        fun keyOf(f: Finding): String = packageOf(f)?.let { "pkg:$it" } ?: "file:${(f as Finding.KnownThreat).path}"
    }
}
