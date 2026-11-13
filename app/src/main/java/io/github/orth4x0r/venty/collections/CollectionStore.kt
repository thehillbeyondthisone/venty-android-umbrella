package io.github.orth4x0r.venty.collections

import android.content.Context
import io.github.orth4x0r.venty.session.SessionRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Permanent local archive; intentionally independent of the rolling recent-session history. */
data class CollectionEntry(val id: String, val startMs: Long, val endMs: Long, val device: String,
    val bands: List<Int>, val note: String = "")

class CollectionStore(context: Context) {
    private val prefs = context.getSharedPreferences("subventy_collections", Context.MODE_PRIVATE)
    private val _paused = MutableStateFlow(prefs.getBoolean("paused", false))
    val paused = _paused.asStateFlow()
    private val _entries = MutableStateFlow(load())
    val entries = _entries.asStateFlow()

    @Synchronized fun setPaused(paused: Boolean) {
        prefs.edit().putBoolean("paused", paused).apply()
        _paused.value = paused
    }

    @Synchronized fun ingest(history: List<SessionRecord>) {
        if (_paused.value) return
        val merged = mergeArchive(_entries.value, history)
        if (merged != _entries.value) save(merged)
    }

    @Synchronized fun saveNote(id: String, note: String) {
        save(_entries.value.map { if (it.id == id) it.copy(note = note.trim().take(120)) else it })
    }

    @Synchronized fun clear() { save(emptyList()) }

    private fun save(entries: List<CollectionEntry>) {
        val array = JSONArray()
        entries.forEach { e -> array.put(JSONObject().put("id", e.id).put("start", e.startMs)
            .put("end", e.endMs).put("device", e.device).put("bands", JSONArray(e.bands))
            .put("note", e.note)) }
        prefs.edit().putString("entries", array.toString()).apply()
        _entries.value = entries
    }

    private fun load(): List<CollectionEntry> = runCatching {
        val array = JSONArray(prefs.getString("entries", "[]"))
        List(array.length()) { i ->
            val e = array.getJSONObject(i)
            val bands = e.optJSONArray("bands") ?: JSONArray()
            CollectionEntry(e.getString("id"), e.getLong("start"), e.getLong("end"),
                e.optString("device", "legacy"), List(bands.length()) { bands.getInt(it) }, e.optString("note"))
        }
    }.getOrDefault(emptyList())
}

/** Append-only by stable identity; a rolling history snapshot cannot delete older keepsakes. */
internal fun mergeArchive(entries: List<CollectionEntry>, history: List<SessionRecord>): List<CollectionEntry> {
    val existing = entries.associateBy { it.id }.toMutableMap()
    for (session in history) {
        val id = "${session.deviceKey}:${session.startMs}:${session.endMs}"
        if (id !in existing) existing[id] = CollectionEntry(id, session.startMs, session.endMs,
            session.deviceKey, session.reachedBands.distinct().sorted())
    }
    return existing.values.sortedByDescending { it.startMs }
}
