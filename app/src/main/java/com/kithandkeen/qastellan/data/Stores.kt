package com.kithandkeen.qastellan.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** One line in the History screen. */
data class HistoryEvent(
    val kind: Kind,
    val title: String,
    val detail: String,
    val time: Long = System.currentTimeMillis(),
) {
    enum class Kind { SCAN_CLEAN, SCAN_THREATS, THREAT, DEFINITIONS, NEW_APP, QUARANTINE, ACTION }

    val isThreat: Boolean get() = kind == Kind.SCAN_THREATS || kind == Kind.THREAT
}

/** Keeps the last 300 events in a small JSON file on the phone. */
class HistoryStore(private val file: File) {
    private val _events = MutableStateFlow(load())
    val events: StateFlow<List<HistoryEvent>> = _events.asStateFlow()

    @Synchronized
    fun add(event: HistoryEvent) {
        _events.value = (listOf(event) + _events.value).take(MAX)
        save()
    }

    private fun load(): List<HistoryEvent> = runCatching {
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            HistoryEvent(HistoryEvent.Kind.valueOf(o.getString("k")), o.getString("t"), o.getString("d"), o.getLong("at"))
        }
    }.getOrDefault(emptyList())

    private fun save() {
        val arr = JSONArray()
        _events.value.forEach { e -> arr.put(JSONObject().put("k", e.kind.name).put("t", e.title).put("d", e.detail).put("at", e.time)) }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }

    companion object {
        const val MAX = 300
    }
}

/** A file moved out of shared storage so nothing can open it. */
data class QuarantinedItem(
    val id: String,
    val name: String,
    val originalPath: String,
    val detection: String,
    val time: Long,
)

/**
 * File quarantine: the file is moved into Qastellan's private storage, which no
 * other app can read or open. It can be restored to where it was, or deleted.
 */
class QuarantineStore(private val dir: File) {
    private val index = File(dir, "index.json")
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<QuarantinedItem>> = _items.asStateFlow()

    init {
        dir.mkdirs()
    }

    @Synchronized
    fun quarantine(source: File, detection: String): QuarantinedItem? {
        if (!source.isFile) return null
        val id = UUID.randomUUID().toString()
        val target = File(dir, "$id.bin")
        val moved = source.renameTo(target) || runCatching {
            source.copyTo(target, overwrite = true)
            source.delete()
        }.getOrDefault(false)
        if (!moved || source.exists()) {
            target.delete()
            return null
        }
        val item = QuarantinedItem(id, source.name, source.absolutePath, detection, System.currentTimeMillis())
        _items.value = listOf(item) + _items.value
        save()
        return item
    }

    @Synchronized
    fun restore(item: QuarantinedItem): Boolean {
        val stored = File(dir, "${item.id}.bin")
        val original = File(item.originalPath)
        original.parentFile?.mkdirs()
        val ok = !original.exists() && (stored.renameTo(original) || runCatching { stored.copyTo(original); stored.delete() }.getOrDefault(false))
        if (ok) remove(item)
        return ok
    }

    @Synchronized
    fun delete(item: QuarantinedItem) {
        File(dir, "${item.id}.bin").delete()
        remove(item)
    }

    private fun remove(item: QuarantinedItem) {
        _items.value = _items.value.filterNot { it.id == item.id }
        save()
    }

    private fun load(): List<QuarantinedItem> = runCatching {
        val arr = JSONArray(index.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            QuarantinedItem(o.getString("id"), o.getString("name"), o.getString("path"), o.getString("det"), o.getLong("at"))
        }.filter { File(dir, "${it.id}.bin").exists() }
    }.getOrDefault(emptyList())

    private fun save() {
        val arr = JSONArray()
        _items.value.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("path", it.originalPath).put("det", it.detection).put("at", it.time)) }
        index.writeText(arr.toString())
    }
}
