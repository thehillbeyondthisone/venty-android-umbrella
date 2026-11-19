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

enum class Impression { Enjoyed, Neutral }

data class DialInPick(val sessionId: String, val band: Int, val impression: Impression? = null)

data class DialInComparison(val picks: List<DialInPick>, val note: String = "") {
    fun toJson(): JSONObject = JSONObject().put("note", note).put("picks", JSONArray().apply {
        picks.forEach { pick ->
            put(JSONObject().put("sessionId", pick.sessionId).put("bandC", pick.band)
                .put("impression", pick.impression?.name))
        }
    })

    companion object {
        fun fromJson(json: JSONObject): DialInComparison {
            val picks = json.getJSONArray("picks")
            return DialInComparison(List(picks.length()) { index ->
                val pick = picks.getJSONObject(index)
                DialInPick(pick.getString("sessionId"), pick.getInt("bandC"),
                    Impression.entries.firstOrNull { it.name == pick.optString("impression") })
            }, json.optString("note"))
        }
    }
}

class CollectionStore(context: Context) {
    private val prefs = context.getSharedPreferences("subventy_collections", Context.MODE_PRIVATE)
    private val _paused = MutableStateFlow(prefs.getBoolean("paused", false))
    val paused = _paused.asStateFlow()
    private val _entries = MutableStateFlow(load())
    val entries = _entries.asStateFlow()
    private val _comparisons = MutableStateFlow(loadComparisons())
    val comparisons = _comparisons.asStateFlow()
    private val _putAwayWeek = MutableStateFlow(prefs.getString("putAwayWeek", null))
    val putAwayWeek = _putAwayWeek.asStateFlow()

    @Synchronized fun saveComparison(device: String?, comparison: DialInComparison): Boolean {
        val picks = comparison.picks
        if (picks.size !in 2..3 || picks.distinctBy { it.band }.size != picks.size ||
            picks.distinctBy { it.sessionId }.size != picks.size) return false
        val entriesById = _entries.value.associateBy { it.id }
        if (picks.any { pick ->
                val entry = entriesById[pick.sessionId]
                entry == null || pick.band !in entry.bands || (device != null && entry.device != device)
            }) return false
        val scope = device?.let { "device:$it" } ?: "all"
        saveComparisons(_comparisons.value + (scope to comparison.copy(note = comparison.note.trim().take(120))))
        return true
    }

    @Synchronized fun setPutAwayWeek(week: String?) {
        prefs.edit().putString("putAwayWeek", week).apply()
        _putAwayWeek.value = week
    }

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

    @Synchronized fun clear() {
        save(emptyList())
        saveComparisons(emptyMap())
    }

    private fun saveComparisons(comparisons: Map<String, DialInComparison>) {
        val json = JSONObject()
        comparisons.forEach { (scope, comparison) -> json.put(scope, comparison.toJson()) }
        prefs.edit().putString("dialInComparisons", json.toString()).apply()
        _comparisons.value = comparisons
    }

    private fun loadComparisons(): Map<String, DialInComparison> {
        val stored = prefs.getString("dialInComparisons", null) ?: return emptyMap()
        val json = runCatching { JSONObject(stored) }.getOrElse { return emptyMap() }
        return buildMap {
            json.keys().forEach { scope ->
                runCatching { DialInComparison.fromJson(json.getJSONObject(scope)) }.getOrNull()?.let { put(scope, it) }
            }
        }
    }

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
