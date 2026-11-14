package com.kithandkeen.qastellan.engine

/** What Qastellan knows about an installed app, gathered without leaving the phone. */
data class AppFacts(
    val packageName: String,
    val label: String,
    val installer: String?,
    val isSystemApp: Boolean,
    val requestedPermissions: Set<String>,
    val hasAccessibilityService: Boolean,
    val isDeviceAdmin: Boolean,
    val hasLauncherIcon: Boolean,
    val signerSha256: String?,
)

data class RiskReport(val score: Int, val reasons: List<String>) {
    val level: Level
        get() = when {
            score >= HIGH -> Level.HIGH
            score >= MEDIUM -> Level.MEDIUM
            else -> Level.LOW
        }

    enum class Level { LOW, MEDIUM, HIGH }

    companion object {
        const val MEDIUM = 40
        const val HIGH = 70
    }
}

/**
 * Flags apps that behave like malware even when they are in no signature list.
 * Every rule produces a plain-language reason the app can show to the user.
 */
class RiskScorer(
    /** Known publishers: package name -> SHA-256 of the real signing certificate. */
    private val knownSigners: Map<String, String> = emptyMap(),
) {
    fun score(app: AppFacts): RiskReport {
        if (app.isSystemApp) return RiskReport(0, emptyList())
        val perms = app.requestedPermissions
        var score = 0
        val reasons = mutableListOf<String>()
        fun add(points: Int, reason: String) {
            score += points
            reasons += reason
        }

        val sideloaded = app.installer == null || app.installer !in TRUSTED_INSTALLERS
        if (sideloaded) add(15, "Installed from outside an app store")

        if (app.hasAccessibilityService) add(30, "Can use accessibility to read and control the screen")
        if (app.isDeviceAdmin) add(25, "Has device admin rights, which make it hard to remove")
        if (OVERLAY in perms) add(10, "Can draw over other apps")
        if (BIND_NOTIFICATIONS in perms) add(10, "Can read your notifications")

        val readsSms = perms.any { it in SMS_PERMISSIONS }
        if (readsSms && sideloaded) add(25, "Can read or send text messages")
        if (readsSms && OVERLAY in perms && app.hasAccessibilityService) {
            add(20, "Combines text message access, screen overlays and accessibility, a pattern used by banking trojans")
        }
        if (INSTALL_PACKAGES in perms && sideloaded) add(15, "Can install other apps")
        if (!app.hasLauncherIcon) add(15, "Has no icon in the app drawer, so it is hidden")

        val expectedSigner = knownSigners[app.packageName]
        if (expectedSigner != null && app.signerSha256 != null && !expectedSigner.equals(app.signerSha256, ignoreCase = true)) {
            add(70, "Uses the name of a well-known app but is signed by someone else")
        }
        return RiskReport(score.coerceAtMost(100), reasons)
    }

    companion object {
        val TRUSTED_INSTALLERS = setOf(
            "com.android.vending", // Google Play
            "org.fdroid.fdroid", // F-Droid
            "org.fdroid.basic",
            "com.aurora.store",
            "com.sec.android.app.samsungapps",
            "com.huawei.appmarket",
            "com.amazon.venezia",
        )
        const val OVERLAY = "android.permission.SYSTEM_ALERT_WINDOW"
        const val BIND_NOTIFICATIONS = "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
        const val INSTALL_PACKAGES = "android.permission.REQUEST_INSTALL_PACKAGES"
        val SMS_PERMISSIONS = setOf(
            "android.permission.READ_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.SEND_SMS",
        )
    }
}
