package com.kithandkeen.qastellan.engine

import java.io.File

sealed class Finding {
    abstract val title: String

    data class KnownThreat(override val title: String, val detection: String, val path: String?, val packageName: String?) : Finding()
    data class RiskyApp(override val title: String, val packageName: String, val report: RiskReport) : Finding()
}

/** Platform-independent scan logic; the Android layer feeds it apps and files. */
class Scanner(
    private val definitions: () -> Definitions,
    private val riskScorer: RiskScorer = RiskScorer(),
) {
    fun scanFile(file: File): Finding? {
        if (!file.isFile || !file.canRead()) return null
        val hashes = try {
            Hasher.hash(file)
        } catch (e: Exception) {
            return null // file vanished or is unreadable; nothing to report
        }
        val name = definitions().match(hashes) ?: return null
        return Finding.KnownThreat(file.name, name, file.absolutePath, null)
    }

    /** Checks an installed app: its package name, its APK's hashes, then its behaviour. */
    fun scanApp(app: AppFacts, apk: File?): Finding? {
        val defs = definitions()
        defs.matchPackage(app.packageName)?.let {
            return Finding.KnownThreat(app.label, it, apk?.absolutePath, app.packageName)
        }
        if (apk != null && apk.canRead()) {
            val hashes = runCatching { Hasher.hash(apk) }.getOrNull()
            hashes?.let(defs::match)?.let {
                return Finding.KnownThreat(app.label, it, apk.absolutePath, app.packageName)
            }
        }
        val report = riskScorer.score(app)
        return if (report.level == RiskReport.Level.HIGH) Finding.RiskyApp(app.label, app.packageName, report) else null
    }
}
