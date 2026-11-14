package com.kithandkeen.qastellan.device

import android.content.Context
import android.os.Environment
import com.kithandkeen.qastellan.Qastellan
import com.kithandkeen.qastellan.engine.Finding
import java.io.File

/** Runs a full scan: every installed app, then Downloads, then the rest of shared storage. */
class DeviceScanner(private val context: Context) {

    enum class Stage(val label: String) { APPS("Installed apps"), DOWNLOADS("Downloads"), STORAGE("Internal storage") }

    data class Progress(
        val stage: Stage,
        val stageDone: Int,
        val stageTotal: Int,
        val overall: Float,
        val current: String,
        val counts: Map<Stage, Int>,
    )

    data class Summary(val findings: List<Finding>, val apps: Int, val files: Int, val millis: Long, val cancelled: Boolean)

    /** @param keepGoing returns false to cancel. */
    fun scan(includeStorage: Boolean = true, keepGoing: () -> Boolean = { true }, onProgress: (Progress) -> Unit = {}): Summary {
        val started = System.currentTimeMillis()
        val app = Qastellan.from(context)
        val findings = mutableListOf<Finding>()
        val counts = linkedMapOf(Stage.APPS to 0, Stage.DOWNLOADS to 0, Stage.STORAGE to 0)

        val apps = AppInspector(context).installedApps()
        val storageOk = includeStorage && StorageAccess.canReadAllFiles(context)
        val downloads = if (storageOk) downloadFiles() else emptyList()
        val storage = if (storageOk) otherFiles() else emptyList()
        // Weight stages by work so the ring moves evenly.
        val total = (apps.size + downloads.size + storage.size).coerceAtLeast(1).toFloat()
        var done = 0

        fun step(stage: Stage, i: Int, size: Int, name: String) {
            done++
            counts[stage] = i + 1
            onProgress(Progress(stage, i + 1, size, done / total, name, counts.toMap()))
        }

        for ((i, pair) in apps.withIndex()) {
            if (!keepGoing()) return Summary(findings, counts[Stage.APPS]!!, 0, System.currentTimeMillis() - started, true)
            val (facts, apk) = pair
            step(Stage.APPS, i, apps.size, facts.packageName)
            app.scanner.scanApp(facts, apk)?.let(findings::add)
        }
        for ((stage, list) in listOf(Stage.DOWNLOADS to downloads, Stage.STORAGE to storage)) {
            for ((i, file) in list.withIndex()) {
                if (!keepGoing()) {
                    return Summary(findings, apps.size, counts[Stage.DOWNLOADS]!! + counts[Stage.STORAGE]!!, System.currentTimeMillis() - started, true)
                }
                step(stage, i, list.size, file.name)
                app.scanner.scanFile(file)?.let(findings::add)
            }
        }
        return Summary(findings, apps.size, downloads.size + storage.size, System.currentTimeMillis() - started, false)
    }

    private fun downloadFiles(): List<File> =
        walk(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)).toList()

    private fun otherFiles(): List<File> {
        val root = Environment.getExternalStorageDirectory()
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        return walk(root).filter { !it.absolutePath.startsWith(downloads) }.toList()
    }

    private fun walk(dir: File): Sequence<File> {
        val skip = File(Environment.getExternalStorageDirectory(), "Android").absolutePath
        return dir.walkTopDown()
            .onEnter { it.absolutePath != skip && !it.name.startsWith(".") }
            .filter { it.isFile && it.length() in 1..MAX_FILE_BYTES }
    }

    companion object {
        /** Very large files (videos, backups) are skipped; malware is almost always small. */
        const val MAX_FILE_BYTES = 200L * 1024 * 1024
    }
}
