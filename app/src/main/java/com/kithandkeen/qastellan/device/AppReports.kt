package com.kithandkeen.qastellan.device

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.kithandkeen.qastellan.engine.AppFacts
import com.kithandkeen.qastellan.engine.RiskReport
import com.kithandkeen.qastellan.engine.RiskScorer
import com.kithandkeen.qastellan.engine.Tracker
import com.kithandkeen.qastellan.engine.TrackerDetector
import org.json.JSONObject
import java.io.File

/** Everything the Apps and App details screens show about one app. */
data class AppReport(
    val facts: AppFacts,
    val versionName: String,
    val sensitiveAccess: List<String>,
    val newAccess: List<String>,
    val trackers: List<Tracker>,
    val risk: RiskReport,
    val sideloaded: Boolean,
    val installerLabel: String,
)

/** Plain names for the permission groups people care about. */
object SensitivePermissions {
    private val groups = linkedMapOf(
        "Location" to listOf("ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "ACCESS_BACKGROUND_LOCATION"),
        "Camera" to listOf("CAMERA"),
        "Microphone" to listOf("RECORD_AUDIO"),
        "Contacts" to listOf("READ_CONTACTS", "WRITE_CONTACTS", "GET_ACCOUNTS"),
        "Text messages" to listOf("READ_SMS", "RECEIVE_SMS", "SEND_SMS"),
        "Call logs" to listOf("READ_CALL_LOG", "WRITE_CALL_LOG"),
        "Phone" to listOf("READ_PHONE_STATE", "CALL_PHONE", "READ_PHONE_NUMBERS"),
        "Photos and files" to listOf("READ_EXTERNAL_STORAGE", "READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO", "MANAGE_EXTERNAL_STORAGE"),
        "Calendar" to listOf("READ_CALENDAR", "WRITE_CALENDAR"),
        "Body sensors" to listOf("BODY_SENSORS"),
    )

    fun of(granted: Collection<String>): List<String> {
        val short = granted.map { it.substringAfterLast('.') }.toSet()
        return groups.filter { (_, perms) -> perms.any { it in short } }.keys.toList()
    }
}

class AppReports(private val context: Context, private val trackers: () -> TrackerDetector) {
    private val pm = context.packageManager
    private val inspector = AppInspector(context)
    private val scorer = RiskScorer()
    private val snapshotFile = File(context.filesDir, "permission_snapshot.json")

    /** User-installed apps, most concerning first. Tracker detection reads each APK, so call off the main thread. */
    fun all(withTrackers: Boolean = true): List<AppReport> =
        inspector.installedApps()
            .filter { (facts, _) -> !facts.isSystemApp }
            .mapNotNull { (facts, apk) -> runCatching { build(facts, apk, withTrackers) }.getOrNull() }
            .sortedWith(compareByDescending<AppReport> { it.risk.score }.thenByDescending { it.trackers.size }.thenBy { it.facts.label.lowercase() })

    fun one(packageName: String): AppReport? = inspector.inspect(packageName)?.let { (facts, apk) -> build(facts, apk, true) }

    /** Marks an app's current permissions as seen, clearing its "new access" warning. */
    @Synchronized
    fun acknowledge(packageName: String) {
        val snap = snapshot()
        snap.put(packageName, org.json.JSONArray(grantedSensitive(packageName)))
        snapshotFile.writeText(snap.toString())
    }

    private fun build(facts: AppFacts, apk: File?, withTrackers: Boolean): AppReport {
        val access = grantedSensitive(facts.packageName)
        val newAccess = permissionWatch(facts.packageName, access)
        val sideloaded = facts.installer == null || facts.installer !in RiskScorer.TRUSTED_INSTALLERS
        return AppReport(
            facts = facts,
            versionName = versionOf(facts.packageName),
            sensitiveAccess = access,
            newAccess = newAccess,
            trackers = if (withTrackers && apk != null) trackers().detect(apk) else emptyList(),
            risk = scorer.score(facts),
            sideloaded = sideloaded,
            installerLabel = installerLabel(facts.installer),
        )
    }

    /**
     * The first time an app is seen, its access is recorded as the baseline.
     * Later, any sensitive access it gained since then is reported as new.
     */
    @Synchronized
    private fun permissionWatch(pkg: String, now: List<String>): List<String> {
        val snap = snapshot()
        if (!snap.has(pkg)) {
            snap.put(pkg, org.json.JSONArray(now))
            snapshotFile.writeText(snap.toString())
            return emptyList()
        }
        val before = snap.getJSONArray(pkg).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
        return now.filter { it !in before }
    }

    private fun snapshot(): JSONObject = runCatching { JSONObject(snapshotFile.readText()) }.getOrDefault(JSONObject())

    @Suppress("DEPRECATION")
    private fun grantedSensitive(pkg: String): List<String> {
        val info: PackageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
            else pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
        }.getOrNull() ?: return emptyList()
        val perms = info.requestedPermissions ?: return emptyList()
        val flags = info.requestedPermissionsFlags ?: return emptyList()
        val granted = perms.filterIndexed { i, _ -> flags.getOrElse(i) { 0 } and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0 }
        return SensitivePermissions.of(granted)
    }

    @Suppress("DEPRECATION")
    private fun versionOf(pkg: String): String = runCatching { pm.getPackageInfo(pkg, 0).versionName }.getOrNull() ?: ""

    private fun installerLabel(installer: String?): String = when (installer) {
        null -> "Installed from a file or unknown source"
        "com.android.vending" -> "Installed from Google Play"
        "org.fdroid.fdroid", "org.fdroid.basic" -> "Installed from F-Droid"
        else -> {
            val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(installer, 0)).toString() }.getOrDefault(installer)
            if (installer in RiskScorer.TRUSTED_INSTALLERS) "Installed from $label" else "Installed by $label"
        }
    }

    companion object {
        fun isSystem(info: ApplicationInfo) =
            info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }
}
