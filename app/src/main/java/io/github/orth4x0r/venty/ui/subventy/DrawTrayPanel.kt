package io.github.orth4x0r.venty.ui.subventy

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.delay
import java.text.DateFormat
import kotlin.math.sqrt

private const val TRAY_CAPACITY = 18
private val WellDark = Color(0xFF201E1B)

@Composable
internal fun DrawArchive(entries: List<CollectionEntry>, scope: String) {
    val sessions = remember(entries) { entries.filter { it.drawBalls.isNotEmpty() } }
    var selectedId by rememberSaveable(scope) { mutableStateOf<String?>(null) }
    var page by rememberSaveable(scope) { mutableIntStateOf(0) }
    val selected = sessions.firstOrNull { it.id == selectedId }
    fun date(entry: CollectionEntry) = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(entry.startMs)
    if (selected == null) {
        if (sessions.isEmpty()) S7Text("Finished sessions with draw balls will appear here.", 12, S7.Muted)
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
            key(selected.id) { DrawViews(selected.drawBalls) }
        }
    }
}

@Composable
internal fun DrawTrayPanel(
    tray: DrawTray,
    paused: Boolean,
    enabled: Boolean,
    onSelect: (DrawSeat?) -> Unit,
    simulate: (() -> Unit)?,
    canSimulate: Boolean,
) {
    DeckPanel {
        DeckLabel("Draw tray", 16)
        if (paused) {
            DeckLabel("Do not track is on. New draws are paused.", 11, Deck.Muted)
            return@DeckPanel
        }
        val active = tray.active
        DeckLabel(
            if (active != null) "Drawing · ${seatName(active.seat)}" else "Next draw · ${seatName(tray.selected)}",
            12, Deck.Muted,
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            (listOf(null) + DrawSeat.entries).forEach { seat ->
                DeckKey(
                    seatName(seat), { onSelect(seat) }, Modifier.widthIn(min = 58.dp), enabled,
                    active = seat == tray.selected, swatch = seatColor(seat),
                )
            }
        }
        if (simulate != null) {
            DeckKey("Simulate draw", simulate, Modifier.fillMaxWidth(), enabled = canSimulate)
        }
        DrawViews(tray.balls, active = active, animate = true)
    }
}

@Composable
internal fun DrawViews(balls: List<DrawBall>, active: InProgressDraw? = null, animate: Boolean = false) {
    var grouped by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DeckKey("Tray", { grouped = false }, Modifier.weight(1f), active = !grouped)
        DeckKey("By person", { grouped = true }, Modifier.weight(1f), active = grouped)
    }
    val settled = if (active == null || active.startMs != balls.lastOrNull()?.startMs) balls else balls.dropLast(1)
    if (grouped) {
        PersonRows(settled)
    } else {
        BallWell(balls, settled, active, animate)
        val text = when {
            settled.isEmpty() && active == null -> "Each finished draw lands here."
            settled.size > TRAY_CAPACITY -> "Latest $TRAY_CAPACITY of ${settled.size} draws"
            settled.size == 1 -> "1 draw"
            else -> "${settled.size} draws"
        }
        DeckLabel(text, 11, Deck.Muted)
    }
}

@Composable
private fun BallWell(
    balls: List<DrawBall>,
    settled: List<DrawBall>,
    active: InProgressDraw?,
    animate: Boolean,
) {
    val latest = balls.lastOrNull()
    val drop = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(latest) }
    LaunchedEffect(latest, animate) {
        val changed = previous != latest
        previous = latest
        if (animate && changed && latest != null) {
            drop.snapTo(0f)
            drop.animateTo(1f, tween(430, easing = FastOutLinearInEasing))
        } else drop.snapTo(1f)
    }

    var elapsed by remember(active?.startMs) { mutableLongStateOf(0) }
    LaunchedEffect(active) {
        if (active == null) return@LaunchedEffect
        while (true) {
            elapsed = (System.currentTimeMillis() - active.startMs).coerceAtLeast(0)
            delay(40)
        }
    }

    val visible = settled.takeLast(TRAY_CAPACITY)
    Canvas(
        Modifier.fillMaxWidth().height(156.dp).clip(Deck.Shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF323029), WellDark)))
            .border(1.dp, Deck.Border, Deck.Shape)
            .semantics {
                contentDescription = "Draw tray, ${settled.size} completed" +
                    (active?.let { ". Inflating ${seatName(it.seat)} draw" } ?: "")
            },
    ) {
        val pitch = 33.dp.toPx()
        val columns = (size.width / pitch).toInt().coerceAtLeast(6)
        val rows = 3
        val shown = visible.takeLast(columns * rows)
        val span = size.width / columns
        val bottom = size.height - 19.dp.toPx()
        drawLine(Deck.Border.copy(alpha = .75f), Offset(6.dp.toPx(), bottom + 13.dp.toPx()),
            Offset(size.width - 6.dp.toPx(), bottom + 13.dp.toPx()), 1.dp.toPx())
        shown.forEachIndexed { i, ball ->
            val row = i / columns
            val col = if (row % 2 == 0) i % columns else columns - 1 - i % columns
            val target = Offset((col + .5f) * span, bottom - row * pitch)
            val progress = if (ball == latest) drop.value else 1f
            val origin = Offset(size.width / 2, 23.dp.toPx())
            val center = origin + (target - origin) * progress
            ballDot(seatColor(ball.seat), minOf(diameterDp(ball.durationMs).dp.toPx(), span * .9f) / 2, center)
        }
        if (active != null) {
            ballDot(seatColor(active.seat), diameterDp(elapsed).dp.toPx() / 2, Offset(size.width / 2, 23.dp.toPx()))
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
        val count = if (person.size == 1) "1 draw" else "${person.size} draws"
        Column(
            Modifier.fillMaxWidth().background(Deck.Ivory, Deck.Shape).padding(7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Canvas(Modifier.size(14.dp)) { ballDot(seatColor(seat), size.minDimension / 2, center) }
                DeckLabel(seatName(seat), 14)
            }
            DeckLabel("$count · ${drawSeconds(sum)} total · ${drawSeconds(sum / person.size)} avg", 11, Deck.Muted)
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

private fun seatName(seat: DrawSeat?): String = seat?.name ?: "Unassigned"

private fun seatColor(seat: DrawSeat?): Color = when (seat) {
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
