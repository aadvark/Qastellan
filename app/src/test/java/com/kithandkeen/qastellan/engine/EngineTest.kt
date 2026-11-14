package com.kithandkeen.qastellan.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer

class EngineTest {

    /** The standard, harmless antivirus test string. */
    private val eicar = "X5O!P%@AP[4\\PZX54(P^)7CC)7}\$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!\$H+H*"

    private fun fixtureDir(): File = File(javaClass.classLoader!!.getResource("defs/names.txt")!!.toURI()).parentFile!!

    @Test
    fun `definitions written by the Python pipeline detect the EICAR test file`() {
        val defs = Definitions.load(fixtureDir(), "test")
        val hashes = Hasher.hash(eicar.byteInputStream())
        assertEquals("275a021bbfb6489e54d471899f7db9d1663fc695ec2fe2a2c4538aabf651fd0f", hashes.sha256Hex())
        assertEquals("Qastellan.Test.EICAR", defs.match(hashes))
    }

    @Test
    fun `clean content is not flagged`() {
        val defs = Definitions.load(fixtureDir(), "test")
        assertNull(defs.match(Hasher.hash("hello world".byteInputStream())))
    }

    @Test
    fun `binary search finds every record including unsigned high prefixes`() {
        val prefixes = listOf(0x0000000000000001L, 0x7fffffffffffffffL, -0x7fffffffffffffffL - 1, -1L) // last two are >= 2^63 unsigned
        val table = HashTable.fromBuffer(ByteBuffer.wrap(qhs(prefixes)))
        prefixes.forEachIndexed { i, p ->
            val digest = ByteBuffer.allocate(32).putLong(p).array()
            assertEquals(i, table.lookup(digest))
        }
        assertNull(table.lookup(ByteBuffer.allocate(32).putLong(42L).array()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `truncated table is rejected`() {
        val bytes = qhs(listOf(1L, 2L))
        HashTable.fromBuffer(ByteBuffer.wrap(bytes.copyOf(bytes.size - 4)))
    }

    @Test
    fun `scanner reports a known file and ignores a clean one`() {
        val defs = Definitions.load(fixtureDir(), "test")
        val scanner = Scanner({ defs })
        val dir = createTempDir()
        try {
            val bad = File(dir, "eicar.com").apply { writeText(eicar) }
            val good = File(dir, "notes.txt").apply { writeText("shopping list") }
            val finding = scanner.scanFile(bad)
            assertTrue(finding is Finding.KnownThreat)
            assertEquals("Qastellan.Test.EICAR", (finding as Finding.KnownThreat).detection)
            assertNull(scanner.scanFile(good))
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Sorted unsigned, as the pipeline writes them; name id = position in the given list. */
    private fun qhs(prefixes: List<Long>): ByteArray {
        val sorted = prefixes.withIndex().sortedWith { a, b -> java.lang.Long.compareUnsigned(a.value, b.value) }
        val out = ByteArrayOutputStream()
        DataOutputStream(out).apply {
            writeBytes("QHS1")
            writeInt(sorted.size)
            sorted.forEach { writeLong(it.value); writeInt(it.index) }
        }
        return out.toByteArray()
    }
}
