package com.kithandkeen.qastellan.engine

import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

data class Tracker(val name: String, val category: String, val prefixes: List<String>)

/**
 * Finds known tracker libraries (Exodus Privacy's list) inside an app by
 * reading the class names in its dex files. Nothing is run or uploaded.
 */
class TrackerDetector(trackers: List<Tracker>) {
    private val byPrefix = HashMap<String, Tracker>()
    private val lengths: IntArray

    init {
        trackers.forEach { t -> t.prefixes.forEach { byPrefix[it] = t } }
        lengths = byPrefix.keys.map { it.length }.distinct().sorted().toIntArray()
    }

    val isEmpty: Boolean get() = byPrefix.isEmpty()

    fun detect(apk: File): List<Tracker> {
        if (isEmpty || !apk.canRead()) return emptyList()
        val found = LinkedHashSet<Tracker>()
        runCatching {
            ZipFile(apk).use { zip ->
                zip.entries().asSequence()
                    .filter { it.name.startsWith("classes") && it.name.endsWith(".dex") }
                    .forEach { entry -> zip.getInputStream(entry).use { classNames(it).forEach { match(it, found) } } }
            }
        }
        return found.sortedBy { it.name.lowercase() }
    }

    fun match(className: String, into: MutableSet<Tracker>) {
        for (len in lengths) {
            if (len > className.length) break
            byPrefix[className.substring(0, len)]?.let(into::add)
        }
    }

    companion object {
        /** Lines written by the pipeline: name TAB category TAB prefix|prefix */
        fun parse(lines: List<String>): List<Tracker> = lines.mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size != 3) null else Tracker(parts[0], parts[1], parts[2].split('|').filter { it.isNotBlank() })
        }

        /**
         * Class names ("com.example.Foo") from a dex file's string table.
         * Only type descriptors ("Lcom/example/Foo;") are returned.
         */
        fun classNames(input: InputStream): Sequence<String> {
            val dex = input.readBytes()
            if (dex.size < 0x70 || dex[0] != 'd'.code.toByte() || dex[1] != 'e'.code.toByte() || dex[2] != 'x'.code.toByte()) {
                return emptySequence()
            }
            fun u32(off: Int) = (dex[off].toInt() and 0xff) or ((dex[off + 1].toInt() and 0xff) shl 8) or
                ((dex[off + 2].toInt() and 0xff) shl 16) or ((dex[off + 3].toInt() and 0xff) shl 24)
            val count = u32(0x38)
            val idsOff = u32(0x3C)
            if (count <= 0 || idsOff <= 0 || idsOff.toLong() + count * 4L > dex.size) return emptySequence()
            return sequence {
                for (i in 0 until count) {
                    var p = u32(idsOff + i * 4)
                    if (p <= 0 || p >= dex.size) continue
                    // skip the ULEB128 length prefix
                    while (p < dex.size && dex[p].toInt() and 0x80 != 0) p++
                    p++
                    if (p >= dex.size || dex[p] != 'L'.code.toByte()) continue
                    var end = p
                    while (end < dex.size && dex[end] != 0.toByte()) end++
                    if (end - p < 3 || dex[end - 1] != ';'.code.toByte()) continue
                    val name = String(dex, p + 1, end - p - 2, Charsets.UTF_8)
                    if ('/' in name) yield(name.replace('/', '.'))
                }
            }
        }
    }
}
