package io.github.orth4x0r.venty.ui.subventy

import android.animation.ValueAnimator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.orth4x0r.venty.collections.CollectionEntry
import io.github.orth4x0r.venty.session.DrawBall
import io.github.orth4x0r.venty.session.DrawSeat
import io.github.orth4x0r.venty.session.DrawTray
import io.github.orth4x0r.venty.session.InProgressDraw
import java.text.DateFormat
import kotlin.math.sqrt

@Composable
internal fun DrawArchive(entries: List<CollectionEntry>, scope: String, onOpen: (String) -> Unit) {
    val sessions = remember(entries) { entries.filter { it.drawBalls.isNotEmpty() } }
    var page by rememberSaveable(scope) { mutableIntStateOf(0) }
    if (sessions.isEmpty()) S7Text("Finished sessions with recorded draws will appear here.", 12, S7.Muted)
    val last = (sessions.size - 1).coerceAtLeast(0) / 20
    val current = page.coerceAtMost(last)
    sessions.drop(current * 20).take(20).forEach { entry ->
        val prefix = if (entry.device.startsWith("demo:")) "Demo · " else ""
        ArchiveRow(sessionDate(entry), "$prefix${entry.drawBalls.size} completed draws · ${drawSeconds(entry.drawBalls.sumOf { it.durationMs })}") {
            onOpen(entry.id)
        }
    }
    if (last > 0) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        S7Button("Newer", { page = current - 1 }, enabled = current > 0)
        S7Button("Older", { page = current + 1 }, enabled = current < last)
    }
}

@Composable
internal fun SavedDrawSession(entry: CollectionEntry?, onBack: () -> Unit) {
    if (entry == null) {
        Column(Modifier.fillMaxSize().background(S7.Workspace).padding(horizontal = 14.dp, vertical = 5.dp)) {
            SceneHeader("Saved session", onBack, backLabel = "Back to Draw sessions")
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                DeckLabel("This session is unavailable", 14, S7.Muted)
            }
        }
        return
    }
    key(entry.id) {
        SessionDrawViews(entry.drawBalls, onBack = onBack, backLabel = "Back to Draw sessions") {
            val prefix = if (entry.device.startsWith("demo:")) "Demo · " else ""
            Text(
                prefix + sessionDate(entry),
                Modifier.padding(start = 16.dp, top = 8.dp, bottom = 12.dp)
                    .background(Color(0xE6241B14), Deck.Shape)
                    .border(1.dp, Color(0xFF665032), Deck.Shape)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                color = S7.Link, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp,
            )
        }
    }
}

private fun sessionDate(entry: CollectionEntry) =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(entry.startMs)

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
    onBack: () -> Unit,
    status: String?,
    stopHeating: (() -> Unit)?,
    simulate: (() -> Unit)?,
    canSimulate: Boolean,
) {
    if (paused) {
        Column(Modifier.fillMaxSize().background(S7.Workspace).padding(horizontal = 14.dp, vertical = 5.dp)) {
            SceneHeader("Session objects", onBack)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                DeckLabel("Do not track is on", 14, S7.Muted)
            }
            if (stopHeating != null) {
                DeckKey("Turn heater off", stopHeating, Modifier.fillMaxWidth().padding(bottom = 6.dp), enabled, KeyFinish.Rust)
            }
        }
        return
    }
    SessionDrawViews(tray.balls, onBack = onBack, active = tray.active,
        simulate = simulate, canSimulate = canSimulate) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SeatPicker(tray.selected, enabled, onSelect)
                val message = tray.active?.let { "Drawing · ${seatName(it.seat)}" } ?: status
                message?.let { DeckLabel(it, 11, S7.Muted) }
            }
            if (stopHeating != null) {
                DeckKey("Turn heater off", stopHeating,
                    Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 10.dp), enabled, KeyFinish.Rust)
            }
        }
    }
}

@Composable
private fun SeatPicker(seat: DrawSeat?, enabled: Boolean, onSelect: (DrawSeat?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
            DeckLabel("Next draw", 12, if (enabled) S7.Muted else S7.Muted.copy(alpha = .55f))
            Spacer(Modifier.width(7.dp))
            SeatSwatch(seat)
            Spacer(Modifier.width(5.dp))
            DeckLabel("${seatName(seat)}  ▾", 12, if (enabled) S7.Text else S7.Muted.copy(alpha = .55f))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            sculptureSeats.forEach { option ->
                DropdownMenuItem(text = { Text(seatName(option)) }, leadingIcon = { SeatSwatch(option) }, onClick = {
                    onSelect(option)
                    open = false
                })
            }
        }
    }
}

@Composable
private fun SceneHeader(
    title: String,
    onBack: () -> Unit,
    backLabel: String = "Back to Device",
    options: @Composable () -> Unit = {},
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, backLabel, tint = S7.Text)
        }
        DeckLabel(title, 17, S7.Text, Modifier.weight(1f).padding(start = 3.dp))
        options()
    }
}

@Composable
private fun SeatSwatch(seat: DrawSeat?) {
    Box(Modifier.size(8.dp).background(seatColor(seat), CircleShape))
}

private enum class SceneOverlay { Draws, Details }

@Composable
private fun SessionDrawViews(
    balls: List<DrawBall>,
    onBack: () -> Unit,
    active: InProgressDraw? = null,
    backLabel: String = "Back to Device",
    simulate: (() -> Unit)? = null,
    canSimulate: Boolean = false,
    footer: @Composable () -> Unit,
) {
    var view by rememberSaveable { mutableStateOf(DrawView.Resin) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var inspectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var browsePage by rememberSaveable { mutableIntStateOf(0) }
    var overlay by rememberSaveable { mutableStateOf<SceneOverlay?>(null) }
    var options by remember { mutableStateOf(false) }
    val lastPage = (balls.size - 1).coerceAtLeast(0) / SCULPTURE_PAGE_SIZE
    val current = page.coerceIn(0, lastPage)
    val visible = drawPage(balls, current)
    val activeHere = active.takeIf { current == 0 }
    val selected = visible.firstOrNull { it.startMs == inspectedId && it.startMs != activeHere?.startMs }
    val stage = remember(view, current) { if (view != DrawView.People) SculptureStage(view) else null }
    val canBrowse = balls.any { it.startMs != active?.startMs }
    LaunchedEffect(lastPage) {
        if (page > lastPage) page = lastPage
    }
    LaunchedEffect(overlay, selected, canBrowse) {
        if ((overlay == SceneOverlay.Details && selected == null) ||
            (overlay == SceneOverlay.Draws && !canBrowse)) overlay = null
    }
    fun browse() {
        browsePage = current
        overlay = SceneOverlay.Draws
    }
    fun inspect(ball: DrawBall) {
        inspectedId = ball.startMs
        overlay = SceneOverlay.Details
    }
    Box(Modifier.fillMaxSize().background(S7.Workspace)) {
        if (stage != null) SculptureBackdrop(view, Modifier.matchParentSize())
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.padding(horizontal = 12.dp)) {
                SceneHeader(view.label, onBack, backLabel) {
                    Box {
                        IconButton(onClick = { options = true }) {
                            Icon(Icons.Default.MoreVert, "Session options", tint = S7.Text)
                        }
                        DropdownMenu(expanded = options, onDismissRequest = { options = false }) {
                            DrawView.entries.filter { it != view }.forEach { mode ->
                                DropdownMenuItem(text = { Text(mode.label) }, onClick = {
                                    view = mode
                                    options = false
                                })
                            }
                            if (stage != null && canBrowse) {
                                DropdownMenuItem(text = { Text("Browse draws") }, onClick = {
                                    options = false
                                    browse()
                                })
                            }
                            if (simulate != null) {
                                DropdownMenuItem(text = { Text("Simulate draw") }, enabled = canSimulate, onClick = {
                                    options = false
                                    simulate()
                                })
                            }
                        }
                    }
                }
            }
            if (stage == null) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PersonRows(balls.filterNot { it.startMs == active?.startMs })
                }
            } else {
                LaunchedEffect(stage, visible, activeHere) { stage.show(visible, activeHere) }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    SculptureCanvas(stage, view, selected?.startMs,
                        onBrowse = if (canBrowse) ::browse else null, onInspect = ::inspect)
                    if (visible.isEmpty() && activeHere == null) {
                        DeckLabel("Finished draws will appear here", 12, S7.Muted)
                    }
                }
                if (current > 0) {
                    TextButton(onClick = { page = 0; inspectedId = null }, modifier = Modifier.padding(start = 10.dp)) {
                        DeckLabel("Back to latest draws", 12, S7.Text)
                    }
                }
            }
            footer()
        }
    }

    when (overlay) {
        SceneOverlay.Draws -> if (stage != null && canBrowse) {
            val browsing = browsePage.coerceIn(0, lastPage)
            UtilityWindow("Recorded draws", { overlay = null }) {
                drawPage(balls, browsing).filterNot { it.startMs == active?.startMs }.forEach { ball ->
                    ArchiveRow(seatName(ball.seat), drawSeconds(ball.durationMs)) {
                        page = browsing
                        inspect(ball)
                    }
                }
                if (lastPage > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        S7Button("Newer", { browsePage = browsing - 1 }, enabled = browsing > 0)
                        S7Button("Older", { browsePage = browsing + 1 }, enabled = browsing < lastPage)
                    }
                }
            }
        }
        SceneOverlay.Details -> if (stage != null && selected != null) {
            UtilityWindow("Draw details", { overlay = null }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    SeatSwatch(selected.seat)
                    DeckLabel("${seatName(selected.seat)} · ${drawSeconds(selected.durationMs)}", 17, S7.Text)
                }
                if (ValueAnimator.areAnimatorsEnabled()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DeckKey("Replay draw", {
                            overlay = null
                            stage.replay(selected)
                        }, Modifier.weight(1f), enabled = stage.replayingId == null, finish = KeyFinish.Teal)
                        if (view == DrawView.Mobile) {
                            DeckKey("Pluck", {
                                overlay = null
                                stage.pluck(selected.startMs)
                            }, Modifier.weight(1f), enabled = stage.replayingId != selected.startMs, finish = KeyFinish.Graphite)
                        }
                    }
                }
            }
        }
        null -> Unit
    }
}

private fun drawPage(balls: List<DrawBall>, page: Int): List<DrawBall> {
    val end = (balls.size - page * SCULPTURE_PAGE_SIZE).coerceAtLeast(0)
    return balls.subList((end - SCULPTURE_PAGE_SIZE).coerceAtLeast(0), end)
}

@Composable
private fun PersonRows(balls: List<DrawBall>) {
    val groups = remember(balls) { balls.groupBy { it.seat } }
    if (balls.isEmpty()) {
        DeckLabel("Finished draws will appear here by color.", 11, S7.Muted)
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
