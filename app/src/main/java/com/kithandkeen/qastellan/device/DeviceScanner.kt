package com.kithandkeen.qastellan.device

import android.content.Context
import android.os.Environment
import com.kithandkeen.qastellan.Qastellan
import com.kithandkeen.qastellan.engine.Finding
import java.io.File

/** Runs a full scan: every installed app, then the Downloads folder and shared storage. */
class DeviceScanner(private val context: Context) {

    data class Progress(val stage: String, val checked: Int, val current: String)

    fun scan(includeStorage: Boolean = true, onProgress: (Progress) -> Unit = {}): List<Finding> {
        val app = Qastellan.from(context)
        val findings = mutableListOf<Finding>()

        val apps = AppInspector(context).installedApps()
        apps.forEachIndexed { i, (facts, apk) ->
            onProgress(Progress("Installed apps", i + 1, facts.packageName))
            app.scanner.scanApp(facts, apk)?.let(findings::add)
        }

        if (includeStorage && StorageAccess.canReadAllFiles(context)) {
            var checked = 0
            storageFiles().forEach { file ->
                checked++
                onProgress(Progress("Storage", checked, file.name))
                app.scanner.scanFile(file)?.let(findings::add)
            }
        }
        return findings
    }

    /** Downloads first (where most infected files arrive), then the rest of shared storage. */
    private fun storageFiles(): Sequence<File> {
        val root = Environment.getExternalStorageDirectory()
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val skip = setOf(File(root, "Android").absolutePath)
        fun walk(dir: File) = dir.walkTopDown()
            .onEnter { it.absolutePath !in skip && !it.name.startsWith(".") }
            .filter { it.isFile && it.length() in 1..MAX_FILE_BYTES }
        return walk(downloads) + walk(root).filter { !it.absolutePath.startsWith(downloads.absolutePath) }
    }

    companion object {
        /** Very large files (videos, backups) are skipped; malware is almost always small. */
        const val MAX_FILE_BYTES = 200L * 1024 * 1024
    }
}
