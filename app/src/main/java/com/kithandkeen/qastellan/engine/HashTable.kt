package com.kithandkeen.qastellan.engine

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * A sorted table of known-bad hash prefixes, in the "QHS1" format written by
 * pipeline/qdefs/formats.py:
 *
 *   magic "QHS1" | count uint32 | count x (prefix uint64, nameId uint32), big-endian, sorted by prefix.
 *
 * The file is memory-mapped, so even a table with millions of entries costs
 * almost no RAM, and each lookup is a binary search (about 22 steps for 4 million).
 */
class HashTable private constructor(private val buffer: ByteBuffer, val size: Int) {

    /** Returns the detection-name index for [digest], or null if it is not a known threat. */
    fun lookup(digest: ByteArray): Int? {
        require(digest.size >= PREFIX_BYTES) { "digest too short" }
        val target = prefixOf(digest)
        var lo = 0
        var hi = size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val value = buffer.getLong(HEADER_BYTES + mid * RECORD_BYTES)
            when {
                value == target -> return buffer.getInt(HEADER_BYTES + mid * RECORD_BYTES + PREFIX_BYTES)
                // Prefixes are compared as unsigned 64-bit numbers, as the pipeline sorts them.
                java.lang.Long.compareUnsigned(value, target) < 0 -> lo = mid + 1
                else -> hi = mid - 1
            }
        }
        return null
    }

    companion object {
        private val MAGIC = byteArrayOf('Q'.code.toByte(), 'H'.code.toByte(), 'S'.code.toByte(), '1'.code.toByte())
        const val HEADER_BYTES = 8
        const val RECORD_BYTES = 12
        const val PREFIX_BYTES = 8

        val EMPTY = HashTable(ByteBuffer.allocate(HEADER_BYTES), 0)

        fun prefixOf(digest: ByteArray): Long =
            ByteBuffer.wrap(digest, 0, PREFIX_BYTES).order(ByteOrder.BIG_ENDIAN).long

        fun open(file: File): HashTable {
            RandomAccessFile(file, "r").use { raf ->
                val map = raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length())
                return fromBuffer(map)
            }
        }

        fun fromBuffer(buffer: ByteBuffer): HashTable {
            val buf = buffer.duplicate().order(ByteOrder.BIG_ENDIAN)
            require(buf.capacity() >= HEADER_BYTES) { "file too small" }
            val magic = ByteArray(4) { buf.get(it) }
            require(magic.contentEquals(MAGIC)) { "not a QHS1 table" }
            val count = buf.getInt(4)
            require(count >= 0 && buf.capacity().toLong() >= HEADER_BYTES + count.toLong() * RECORD_BYTES) {
                "table is truncated"
            }
            return HashTable(buf, count)
        }

        private fun ByteBuffer.get(index: Int, dst: ByteArray) {
            for (i in dst.indices) dst[i] = get(index + i)
        }
    }
}
