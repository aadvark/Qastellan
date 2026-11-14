package com.kithandkeen.qastellan.device

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.view.accessibility.AccessibilityManager
import com.kithandkeen.qastellan.engine.AppFacts
import java.io.File
import java.security.MessageDigest

/** Gathers facts about installed apps from Android's own package records. */
class AppInspector(private val context: Context) {
    private val pm = context.packageManager

    fun installedApps(): List<Pair<AppFacts, File?>> {
        val accessibility = accessibilityPackages()
        val admins = deviceAdminPackages()
        return installedPackages().filter { it.packageName != context.packageName }.map { info ->
            facts(info, accessibility, admins) to info.applicationInfo?.sourceDir?.let(::File)
        }
    }

    fun inspect(packageName: String): Pair<AppFacts, File?>? {
        val info = runCatching { packageInfo(packageName) }.getOrNull() ?: return null
        return facts(info, accessibilityPackages(), deviceAdminPackages()) to info.applicationInfo?.sourceDir?.let(::File)
    }

    private fun facts(info: PackageInfo, accessibility: Set<String>, admins: Set<String>): AppFacts {
        val appInfo = info.applicationInfo
        val isSystem = appInfo != null && (appInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
        return AppFacts(
            packageName = info.packageName,
            label = appInfo?.loadLabel(pm)?.toString() ?: info.packageName,
            installer = installerOf(info.packageName),
            isSystemApp = isSystem,
            requestedPermissions = info.requestedPermissions?.toSet() ?: emptySet(),
            hasAccessibilityService = info.packageName in accessibility,
            isDeviceAdmin = info.packageName in admins,
            hasLauncherIcon = pm.getLaunchIntentForPackage(info.packageName) != null,
            signerSha256 = signerOf(info),
        )
    }

    @Suppress("DEPRECATION")
    private fun flags(): Int {
        var f = PackageManager.GET_PERMISSIONS
        f = f or if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        return f
    }

    @Suppress("DEPRECATION")
    private fun installedPackages(): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= 33) pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags().toLong()))
        else pm.getInstalledPackages(flags())

    @Suppress("DEPRECATION")
    private fun packageInfo(name: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(name, PackageManager.PackageInfoFlags.of(flags().toLong()))
        else pm.getPackageInfo(name, flags())

    @Suppress("DEPRECATION")
    private fun installerOf(name: String): String? = runCatching {
        if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(name).installingPackageName else pm.getInstallerPackageName(name)
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun signerOf(info: PackageInfo): String? {
        val cert = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }?.firstOrNull()
        } else {
            info.signatures?.firstOrNull()
        } ?: return null
        return MessageDigest.getInstance("SHA-256").digest(cert.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun accessibilityPackages(): Set<String> {
        val am = context.getSystemService(AccessibilityManager::class.java) ?: return emptySet()
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .mapNotNull { it.resolveInfo?.serviceInfo?.packageName }.toSet()
    }

    private fun deviceAdminPackages(): Set<String> {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return emptySet()
        return dpm.activeAdmins?.map { it.packageName }?.toSet() ?: emptySet()
    }
}
