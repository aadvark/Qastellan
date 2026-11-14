package com.kithandkeen.qastellan.device

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** One row of the Security audit screen. */
data class AuditCheck(
    val id: String,
    val label: String,
    val state: State,
    val detail: String,
    val fixLabel: String? = null,
    val fix: Intent? = null,
) {
    enum class State { OK, PROBLEM, UNKNOWN }
}

/** Settings that make malware easier to install or harder to remove. */
class SecurityAudit(private val context: Context) {

    fun run(): List<AuditCheck> = listOf(patch(), usbDebugging(), unknownSources(), screenLock(), root(), bootloader(), encryption())

    private fun patch(): AuditCheck {
        val patch = Build.VERSION.SECURITY_PATCH
        val days = runCatching { ChronoUnit.DAYS.between(LocalDate.parse(patch), LocalDate.now()) }.getOrNull()
        return when {
            days == null -> AuditCheck("patch", "Security update", AuditCheck.State.UNKNOWN, "Patch level unknown")
            days > 90 -> AuditCheck(
                "patch", "Security update", AuditCheck.State.PROBLEM,
                "Patch is ${days / 30} months old ($patch)", "Update", settings("android.settings.SYSTEM_UPDATE_SETTINGS"),
            )
            else -> AuditCheck("patch", "Security update", AuditCheck.State.OK, "Up to date ($patch)")
        }
    }

    private fun usbDebugging(): AuditCheck {
        val on = Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        return if (on) {
            AuditCheck("usb", "USB debugging", AuditCheck.State.PROBLEM, "On: a computer can install apps", "Turn off",
                settings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        } else {
            AuditCheck("usb", "USB debugging", AuditCheck.State.OK, "Off")
        }
    }

    /** Apps the user has allowed to install other apps (browsers, file managers, chat apps). */
    private fun unknownSources(): AuditCheck {
        val allowed = installers()
        return when {
            allowed == null -> AuditCheck("unknown", "Install unknown apps", AuditCheck.State.UNKNOWN, "Could not be checked on this phone")
            allowed.isEmpty() -> AuditCheck("unknown", "Install unknown apps", AuditCheck.State.OK, "Not allowed for any app")
            else -> AuditCheck(
                "unknown", "Install unknown apps", AuditCheck.State.PROBLEM,
                "Allowed for ${allowed.take(2).joinToString()}${if (allowed.size > 2) " and ${allowed.size - 2} more" else ""}",
                "Review", settings(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES),
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun installers(): List<String>? = runCatching {
        val pm = context.packageManager
        val ops = context.getSystemService(AppOpsManager::class.java)
        pm.getInstalledApplications(0)
            .filter { it.packageName != context.packageName }
            .filter { app ->
                val requests = runCatching {
                    pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions
                        ?.contains("android.permission.REQUEST_INSTALL_PACKAGES") == true
                }.getOrDefault(false)
                requests && ops.unsafeCheckOpNoThrowCompat(app.uid, app.packageName) == AppOpsManager.MODE_ALLOWED
            }
            .map { it.loadLabel(pm).toString() }
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun AppOpsManager.unsafeCheckOpNoThrowCompat(uid: Int, pkg: String): Int =
        if (Build.VERSION.SDK_INT >= 29) unsafeCheckOpNoThrow("android:request_install_packages", uid, pkg)
        else checkOpNoThrow("android:request_install_packages", uid, pkg)

    private fun screenLock(): AuditCheck {
        val secure = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
        return if (secure) AuditCheck("lock", "Screen lock", AuditCheck.State.OK, "PIN, pattern or password set")
        else AuditCheck("lock", "Screen lock", AuditCheck.State.PROBLEM, "Anyone can open this phone", "Set up",
            settings(Settings.ACTION_SECURITY_SETTINGS))
    }

    private fun root(): AuditCheck {
        val suFound = SU_PATHS.any { File(it).exists() }
        val testKeys = Build.TAGS?.contains("test-keys") == true
        return if (suFound || testKeys) {
            AuditCheck("root", "Root access", AuditCheck.State.PROBLEM, "Rooted: apps can get full control of the phone")
        } else {
            AuditCheck("root", "Root access", AuditCheck.State.OK, "Not rooted")
        }
    }

    private fun bootloader(): AuditCheck {
        val state = systemProperty("ro.boot.verifiedbootstate")
        val locked = systemProperty("ro.boot.flash.locked")
        return when {
            state == "green" || locked == "1" -> AuditCheck("boot", "Bootloader", AuditCheck.State.OK, "Locked")
            state == "orange" || locked == "0" -> AuditCheck(
                "boot", "Bootloader", AuditCheck.State.PROBLEM, "Unlocked: the system can be replaced without your PIN",
            )
            else -> AuditCheck("boot", "Bootloader", AuditCheck.State.UNKNOWN, "Not reported by this phone")
        }
    }

    private fun encryption(): AuditCheck {
        val status = context.getSystemService(DevicePolicyManager::class.java)?.storageEncryptionStatus
        val on = status == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE || status == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER
        return if (on) AuditCheck("enc", "Storage encryption", AuditCheck.State.OK, "On")
        else AuditCheck("enc", "Storage encryption", AuditCheck.State.PROBLEM, "Off", "Settings", settings(Settings.ACTION_SECURITY_SETTINGS))
    }

    private fun settings(action: String) = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    companion object {
        private val SU_PATHS = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/su", "/su/bin/su",
            "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
        )

        @Suppress("PrivateApi")
        fun systemProperty(name: String): String? = runCatching {
            Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, name) as String
        }.getOrNull()?.takeIf { it.isNotBlank() }

        fun appSettings(packageName: String) =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun uninstall(packageName: String) =
            Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
