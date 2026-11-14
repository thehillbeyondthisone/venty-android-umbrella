package io.github.orth4x0r.venty.ui.subventy

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NightPrefs(
    val handle: String = DEFAULT_HANDLE,
    /** Synthesised handshake on connect. Off by default, like every sound in the app. */
    val modemSound: Boolean = false,
    /** The silver-seven screensaver after a couple of idle minutes on the terminal. */
    val screensaver: Boolean = true,
)

/**
 * Cosmetic, game-only state for the session terminal and ICEBREAKER. Kept apart from sessions and collections:
 * nothing here is derived from, or feeds into, device use.
 */
class NightStore(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("subventy_night", Context.MODE_PRIVATE)

    private val _prefs = MutableStateFlow(NightPrefs(
        handle = sp.getString("handle", null) ?: DEFAULT_HANDLE,
        modemSound = sp.getBoolean("modem", false),
        screensaver = sp.getBoolean("screensaver", true),
    ))
    val prefs: StateFlow<NightPrefs> = _prefs.asStateFlow()

    /** Loot ids with the time they were pulled, oldest first. */
    private val _loot = MutableStateFlow(decodeLoot(sp.getString("loot", "") ?: ""))
    val loot: StateFlow<List<Pair<String, Long>>> = _loot.asStateFlow()

    private val _secrets = MutableStateFlow(sp.getStringSet("secrets", emptySet())!!.toSet())
    val secrets: StateFlow<Set<String>> = _secrets.asStateFlow()

    /** The terminal scrollback. In memory only: it survives rotation and screen changes, not restarts. */
    private val _feed = MutableStateFlow<List<TermLine>>(emptyList())
    val feed: StateFlow<List<TermLine>> = _feed.asStateFlow()
    private var lastSnapshot: FeedSnapshot? = null

    @Synchronized fun appendFeed(lines: List<TermLine>) { _feed.value = Feed.append(_feed.value, lines) }

    @Synchronized fun clearFeed() { _feed.value = emptyList() }

    /** Narrates the change since the last snapshot. */
    @Synchronized fun observe(snapshot: FeedSnapshot) {
        val lines = Feed.diff(lastSnapshot, snapshot)
        lastSnapshot = snapshot
        if (lines.isNotEmpty()) appendFeed(lines)
    }

    @Synchronized fun setPrefs(p: NightPrefs) {
        sp.edit().putString("handle", p.handle)
            .putBoolean("modem", p.modemSound).putBoolean("screensaver", p.screensaver).apply()
        _prefs.value = p
    }

    /** Records a pulled file once; repeated cracks never duplicate it. */
    @Synchronized fun pull(id: String, atMs: Long = System.currentTimeMillis()): Boolean {
        if (_loot.value.any { it.first == id }) return false
        val next = _loot.value + (id to atMs)
        sp.edit().putString("loot", encodeLoot(next)).apply()
        _loot.value = next
        return true
    }

    @Synchronized fun found(secret: String) {
        if (secret in _secrets.value) return
        val next = _secrets.value + secret
        sp.edit().putStringSet("secrets", next).apply()
        _secrets.value = next
    }

    @Synchronized fun clearGame() {
        sp.edit().remove("loot").remove("secrets").apply()
        _loot.value = emptyList(); _secrets.value = emptySet()
    }
}
