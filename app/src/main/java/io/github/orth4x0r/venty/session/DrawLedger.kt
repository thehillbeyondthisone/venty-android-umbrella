package io.github.orth4x0r.venty.session

import org.json.JSONArray
import org.json.JSONObject

/** Seat colors identify people for one session. No names or cross-session identity are inferred. */
enum class DrawSeat { Amber, Teal, Rose, Violet }

data class DrawBall(val startMs: Long, val durationMs: Long, val seat: DrawSeat?)

data class InProgressDraw(val startMs: Long, val seat: DrawSeat?)

data class DrawTray(
    val selected: DrawSeat? = null,
    val active: InProgressDraw? = null,
    val balls: List<DrawBall> = emptyList(),
)

/** Maps detector edges to one stable ball per completed draw. Called under the session lock. */
internal class DrawLedger {
    var tray = DrawTray()
        private set

    val canMerge: Boolean get() = tray.balls.lastOrNull()?.seat == tray.selected

    fun select(seat: DrawSeat?) { tray = tray.copy(selected = seat) }

    fun beginSession() { tray = DrawTray(selected = tray.selected) }

    fun closeSession() { tray = DrawTray(balls = tray.balls) }

    fun clear() { tray = DrawTray() }

    /** Returns the start of a draw that the detector resumed without emitting a new edge. */
    fun record(event: PuffEvent?, puffing: Boolean): InProgressDraw? {
        when (event) {
            is PuffEvent.Started -> tray = tray.copy(active = InProgressDraw(event.atMs, tray.selected))
            is PuffEvent.Ended -> {
                val active = tray.active ?: return null
                val previous = tray.balls.lastOrNull()
                val duration = event.durationMs.coerceAtLeast(0)
                val balls = when {
                    duration == 0L -> tray.balls
                    previous?.startMs == active.startMs -> tray.balls.dropLast(1) + previous.copy(
                        durationMs = maxOf(previous.durationMs, duration),
                    )
                    else -> tray.balls + DrawBall(active.startMs, duration, active.seat)
                }
                tray = tray.copy(active = null, balls = balls)
            }
            null -> if (puffing && tray.active == null) {
                val last = tray.balls.lastOrNull() ?: return null
                val resumed = InProgressDraw(last.startMs, last.seat)
                tray = tray.copy(active = resumed)
                return resumed
            }
        }
        return null
    }
}

internal fun List<DrawBall>.drawBallsJson() = JSONArray().apply {
    this@drawBallsJson.forEach { ball ->
        put(JSONObject().put("startMs", ball.startMs).put("durationMs", ball.durationMs)
            .put("seat", ball.seat?.name ?: JSONObject.NULL))
    }
}

internal fun JSONArray?.readDrawBalls(): List<DrawBall> = buildList {
    val array = this@readDrawBalls ?: return@buildList
    for (index in 0 until array.length()) {
        val json = array.optJSONObject(index) ?: continue
        val start = json.optLong("startMs", -1)
        val duration = json.optLong("durationMs", 0)
        if (start < 0 || duration <= 0) continue
        val seat = DrawSeat.entries.firstOrNull { it.name == json.optString("seat") }
        add(DrawBall(start, duration, seat))
    }
}
