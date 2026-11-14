package com.kithandkeen.qastellan.engine

import com.kithandkeen.qastellan.data.HistoryEvent
import com.kithandkeen.qastellan.data.HistoryStore
import com.kithandkeen.qastellan.data.QuarantineStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

class TrackersAndStoresTest {

    /** A minimal dex: header with a string_ids table pointing at MUTF-8 strings. */
    private fun dex(strings: List<String>): ByteArray {
        val header = 0x70
        val idsOff = header
        val dataOff = idsOff + strings.size * 4
        val data = ByteArrayOutputStream()
        val offsets = strings.map { s ->
            val off = dataOff + data.size()
            data.write(s.length) // ULEB128, short strings fit in one byte
            data.write(s.toByteArray())
            data.write(0)
            off
        }
        val out = ByteArray(dataOff + data.size())
        "dex\n035\u0000".toByteArray().copyInto(out)
        fun put(off: Int, v: Int) { for (i in 0..3) out[off + i] = (v shr (8 * i)).toByte() }
        put(0x38, strings.size)
        put(0x3C, idsOff)
        offsets.forEachIndexed { i, o -> put(idsOff + i * 4, o) }
        data.toByteArray().copyInto(out, dataOff)
        return out
    }

    @Test
    fun classNamesReadsOnlyTypeDescriptors() {
        val names = TrackerDetector.classNames(dex(listOf("Lcom/google/firebase/analytics/A;", "hello", "LFoo;", "I")).inputStream()).toList()
        assertEquals(listOf("com.google.firebase.analytics.A"), names)
    }

    @Test
    fun matchesByPrefix() {
        val trackers = TrackerDetector.parse(listOf("Firebase Analytics\tAnalytics\tcom.google.firebase.analytics|com.google.android.gms.measurement", "broken line"))
        assertEquals(1, trackers.size)
        val detector = TrackerDetector(trackers)
        val found = LinkedHashSet<Tracker>()
        detector.match("com.google.firebase.analytics.FirebaseAnalytics", found)
        detector.match("com.example.app.Main", found)
        assertEquals(listOf("Firebase Analytics"), found.map { it.name })
    }

    @Test
    fun rejectsNonDex() {
        assertTrue(TrackerDetector.classNames("PK not a dex".toByteArray().inputStream()).toList().isEmpty())
    }

    @Test
    fun historyPersistsAndCaps() {
        val dir = Files.createTempDirectory("hist").toFile()
        val file = File(dir, "history.json")
        val store = HistoryStore(file)
        repeat(HistoryStore.MAX + 5) { store.add(HistoryEvent(HistoryEvent.Kind.SCAN_CLEAN, "Scan $it", "")) }
        val reloaded = HistoryStore(file).events.value
        assertEquals(HistoryStore.MAX, reloaded.size)
        assertEquals("Scan ${HistoryStore.MAX + 4}", reloaded.first().title)
    }

    @Test
    fun quarantineMovesAndRestores() {
        val root = Files.createTempDirectory("q").toFile()
        val bad = File(root, "downloads/bad.apk").apply { parentFile!!.mkdirs(); writeText("payload") }
        val store = QuarantineStore(File(root, "quarantine"))
        val item = assertNotNullAndGet(store.quarantine(bad, "Andr.Test"))
        assertFalse(bad.exists())
        assertEquals(1, QuarantineStore(File(root, "quarantine")).items.value.size)
        assertTrue(store.restore(item))
        assertEquals("payload", bad.readText())
        assertTrue(store.items.value.isEmpty())
    }

    private fun <T> assertNotNullAndGet(v: T?): T { assertNotNull(v); return v!! }
}
