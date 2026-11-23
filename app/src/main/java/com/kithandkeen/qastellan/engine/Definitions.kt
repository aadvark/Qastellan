package com.kithandkeen.qastellan.engine

import java.io.File

/** Everything the scanner needs from one installed set of definition files. */
class Definitions(
    val version: String,
    private val md5: HashTable,
    private val sha1: HashTable,
    private val sha256: HashTable,
    private val names: List<String>,
    private val packages: Map<String, String>,
    val trackers: List<Tracker> = emptyList(),
) {
    val trackerDetector: TrackerDetector by lazy { TrackerDetector(trackers) }

    /** Detection name for a file's hashes, or null when the file is clean. */
    fun match(hashes: FileHashes): String? {
        // An empty file can't do anything, but some feeds list its hash as malware.
        if (hashes.isEmptyFile) return null
        val id = sha256.lookup(hashes.sha256) ?: sha1.lookup(hashes.sha1) ?: md5.lookup(hashes.md5)
        return id?.let { names.getOrElse(it) { "Unknown threat" } }
    }

    /** Detection name for an app known by its package name (spyware indicators). */
    fun matchPackage(packageName: String): String? = packages[packageName]

    val signatureCount: Int get() = md5.size + sha1.size + sha256.size

    companion object {
        val EMPTY = Definitions("none", HashTable.EMPTY, HashTable.EMPTY, HashTable.EMPTY, emptyList(), emptyMap())

        /** Loads a folder written by DefinitionUpdater (already verified when it was installed). */
        fun load(dir: File, version: String): Definitions {
            fun table(name: String) = File(dir, name).let { if (it.exists()) HashTable.open(it) else HashTable.EMPTY }
            val names = File(dir, "names.txt").takeIf { it.exists() }?.readLines() ?: emptyList()
            val packages = File(dir, "packages.txt").takeIf { it.exists() }?.readLines()
                ?.mapNotNull { line -> line.split('\t').takeIf { it.size == 2 }?.let { it[0] to it[1] } }
                ?.toMap() ?: emptyMap()
            val trackers = File(dir, "trackers.txt").takeIf { it.exists() }?.readLines()?.let(TrackerDetector::parse) ?: emptyList()
            return Definitions(version, table("md5.qhs"), table("sha1.qhs"), table("sha256.qhs"), names, packages, trackers)
        }
    }
}
