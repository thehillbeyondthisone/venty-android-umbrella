package io.github.orth4x0r.venty.ui.subventy

import android.animation.ValueAnimator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.orth4x0r.venty.collections.CollectionEntry
import io.github.orth4x0r.venty.session.DrawBall
import io.github.orth4x0r.venty.session.DrawSeat
import io.github.orth4x0r.venty.session.DrawTray
import io.github.orth4x0r.venty.session.InProgressDraw
import java.text.DateFormat
import kotlin.math.sqrt

@Composable
internal fun DrawArchive(entries: List<CollectionEntry>, scope: String) {
    val sessions = remember(entries) { entries.filter { it.drawBalls.isNotEmpty() } }
    var selectedId by rememberSaveable(scope) { mutableStateOf<String?>(null) }
    var page by rememberSaveable(scope) { mutableIntStateOf(0) }
    val selected = sessions.firstOrNull { it.id == selectedId }
    fun date(entry: CollectionEntry) = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(entry.startMs)
    if (selected == null) {
        if (sessions.isEmpty()) S7Text("Finished sessions with recorded draws will appear here.", 12, S7.Muted)
        val last = (sessions.size - 1).coerceAtLeast(0) / 20
        val current = page.coerceAtMost(last)
        sessions.drop(current * 20).take(20).forEach { entry ->
            val prefix = if (entry.device.startsWith("demo:")) "Demo · " else ""
            ArchiveRow(date(entry), "$prefix${entry.drawBalls.size} completed draws · ${drawSeconds(entry.drawBalls.sumOf { it.durationMs })}") {
                selectedId = entry.id
            }
        }
        if (last > 0) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            S7Button("Newer", { page = current - 1 }, enabled = current > 0)
            S7Button("Older", { page = current + 1 }, enabled = current < last)
        }
    } else {
        S7Button("‹ Draw sessions", { selectedId = null })
        DeckPanel {
            DeckLabel(date(selected), 15)
            if (selected.device.startsWith("demo:")) DeckLabel("Demo session", 11, Deck.Muted)
            key(selected.id) { SessionDrawViews(selected.drawBalls) }
        }
    }
}

@Composable
internal fun DrawSessionEntry(tray: DrawTray, paused: Boolean, onOpen: () -> Unit) {
    val details = when {
        paused -> "Tracking paused"
        tray.active != null -> "Drawing · ${seatName(tray.active.seat)}"
        else -> "${seatName(tray.selected)} · ${drawCount(tray.balls.size)}"
    }
    DeckKey("Session objects  ·  $details  ›", onOpen, Modifier.fillMaxWidth(), finish = KeyFinish.Graphite)
}

@Composable
internal fun DrawSessionPanel(
    tray: DrawTray,
    paused: Boolean,
    enabled: Boolean,
    onSelect: (DrawSeat?) -> Unit,
    simulate: (() -> Unit)?,
    canSimulate: Boolean,
) {
    DeckPanel {
        DeckLabel("Session objects", 17)
        if (paused) {
            DeckLabel("Do not track is on. New draws are paused.", 11, Deck.Muted)
            return@DeckPanel
        }
        val active = tray.active
        var choosingSeat by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.weight(1f)) {
                DeckKey("Next draw · ${seatName(tray.selected)}  ▾", { choosingSeat = true },
                    Modifier.fillMaxWidth(), enabled, swatch = seatColor(tray.selected))
                DropdownMenu(expanded = choosingSeat, onDismissRequest = { choosingSeat = false }) {
                    (listOf(null) + DrawSeat.entries).forEach { seat ->
                        DropdownMenuItem(text = { Text(seatName(seat)) }, onClick = {
                            onSelect(seat)
                            choosingSeat = false
                        })
                    }
                }
            }
            if (simulate != null) {
                DeckKey("Simulate draw", simulate, Modifier.widthIn(min = 96.dp),
                    enabled = canSimulate, finish = KeyFinish.Graphite)
            }
        }
        if (active != null) DeckLabel("Drawing · ${seatName(active.seat)}", 12, Deck.Muted)
        SessionDrawViews(tray.balls, active = active)
    }
}

@Composable
private fun SessionDrawViews(balls: List<DrawBall>, active: InProgressDraw? = null) {
    var view by rememberSaveable { mutableStateOf(DrawView.Resin) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (candidate in DrawView.entries) {
            DeckKey(candidate.label, { view = candidate }, Modifier.weight(1f),
                active = view == candidate, finish = KeyFinish.Graphite)
        }
    }
    if (view == DrawView.People) {
        PersonRows(balls.filterNot { it.startMs == active?.startMs })
    } else {
        SessionSculpture(view, balls, active)
    }
}

@Composable
private fun SessionSculpture(
    view: DrawView,
    balls: List<DrawBall>,
    active: InProgressDraw?,
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var inspect by remember { mutableStateOf(false) }
    val lastPage = (balls.size - 1).coerceAtLeast(0) / SCULPTURE_PAGE_SIZE
    val current = page.coerceIn(0, lastPage)
    val end = (balls.size - current * SCULPTURE_PAGE_SIZE).coerceAtLeast(0)
    val start = (end - SCULPTURE_PAGE_SIZE).coerceAtLeast(0)
    val slice = balls.subList(start, end)
    val activeHere = active.takeIf { current == 0 }
    val selectable = slice.filterNot { it.startMs == activeHere?.startMs }
    val selected = selectable.firstOrNull { it.startMs == selectedId } ?: selectable.lastOrNull()
    val stage = remember(view, current) { SculptureStage(view) }
    LaunchedEffect(lastPage) {
        if (page > lastPage) page = lastPage
    }
    LaunchedEffect(stage, slice, activeHere) {
        stage.show(slice, activeHere)
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        SculptureCanvas(stage, view, selected?.startMs) { selectedId = it.startMs }
    }
    if (selected != null) {
        val motion = ValueAnimator.areAnimatorsEnabled()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.weight(1.2f)) {
                DeckKey("${seatName(selected.seat)} · ${drawSeconds(selected.durationMs)}  ▾", { inspect = true },
                    Modifier.fillMaxWidth(), swatch = seatColor(selected.seat))
                DropdownMenu(expanded = inspect, onDismissRequest = { inspect = false }) {
                    selectable.forEachIndexed { index, ball ->
                        DropdownMenuItem(
                            text = { Text("${start + index + 1}. ${seatName(ball.seat)} · ${drawSeconds(ball.durationMs)}") },
                            onClick = {
                                selectedId = ball.startMs
                                inspect = false
                            },
                        )
                    }
                }
            }
            if (view == DrawView.Mobile && motion) {
                DeckKey("Pluck", { stage.pluck(selected.startMs) }, Modifier.weight(.64f),
                    enabled = stage.replayingId != selected.startMs, finish = KeyFinish.Graphite)
            }
            if (motion) {
                DeckKey("Replay draw", { stage.replay(selected) }, Modifier.weight(.95f),
                    enabled = stage.replayingId == null, finish = KeyFinish.Teal)
            }
        }
        if (view == DrawView.Mobile && motion) DeckLabel("Pull a bead sideways and release.", 11, Deck.Muted)
    } else if (activeHere == null) {
        DeckLabel("Finished draws will appear here.", 11, Deck.Muted)
    }
    if (lastPage > 0) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DeckKey("Newer", { page = current - 1; selectedId = null }, enabled = current > 0)
            DeckLabel("${start + 1}–$end of ${balls.size}", 11, Deck.Muted, Modifier.weight(1f))
            DeckKey("Older", { page = current + 1; selectedId = null }, enabled = current < lastPage)
        }
    }
}

@Composable
private fun PersonRows(balls: List<DrawBall>) {
    val groups = remember(balls) { balls.groupBy { it.seat } }
    if (balls.isEmpty()) {
        DeckLabel("Finished draws will appear here by color.", 11, Deck.Muted)
        return
    }
    (DrawSeat.entries + listOf<DrawSeat?>(null)).forEach { seat ->
        val person = groups[seat].orEmpty()
        if (person.isEmpty()) return@forEach
        val sum = person.sumOf { it.durationMs }
        Column(
            Modifier.fillMaxWidth().background(Deck.Ivory, Deck.Shape).padding(7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Canvas(Modifier.size(14.dp)) { ballDot(seatColor(seat), size.minDimension / 2, center) }
                DeckLabel(seatName(seat), 14)
            }
            DeckLabel("${drawCount(person.size)} · ${drawSeconds(sum)} total · ${drawSeconds(sum / person.size)} avg", 11, Deck.Muted)
            LazyRow(
                Modifier.fillMaxWidth().heightIn(min = 34.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                items(person) { ball ->
                    Canvas(
                        Modifier.size(diameterDp(ball.durationMs).dp)
                            .semantics { contentDescription = "${seatName(ball.seat)}, ${drawSeconds(ball.durationMs)} draw" },
                    ) { ballDot(seatColor(ball.seat), size.minDimension / 2, center) }
                }
            }
        }
    }
}

internal fun drawSeconds(ms: Long): String = String.format(java.util.Locale.getDefault(), "%.1f s", ms / 1000.0)

private fun drawCount(count: Int) = if (count == 1) "1 draw" else "$count draws"

internal fun seatName(seat: DrawSeat?): String = seat?.name ?: "Unassigned"

internal fun seatColor(seat: DrawSeat?): Color = when (seat) {
    DrawSeat.Amber -> Color(0xFFF3AB37)
    DrawSeat.Teal -> Color(0xFF43BBA8)
    DrawSeat.Rose -> Color(0xFFE47985)
    DrawSeat.Violet -> Color(0xFFA790DD)
    null -> Color(0xFFBFB3A4)
}

// Area tracks duration. A legible floor and a visual cap keep the tray readable.
private fun diameterDp(ms: Long): Float = (sqrt(ms.coerceAtLeast(0) / 1000f) * 8f).coerceIn(6f, 30f)

private fun DrawScope.ballDot(color: Color, radius: Float, center: Offset) {
    drawCircle(
        Brush.radialGradient(
            listOf(lerpToWhite(color), color, color.copy(alpha = .55f)),
            center = center - Offset(radius * .25f, radius * .3f),
            radius = radius * 1.35f,
        ),
        radius, center,
    )
}

private fun lerpToWhite(color: Color) = androidx.compose.ui.graphics.lerp(color, Color.White, .38f)
