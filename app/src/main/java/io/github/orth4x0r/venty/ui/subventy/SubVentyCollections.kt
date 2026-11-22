package io.github.orth4x0r.venty.ui.subventy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.orth4x0r.venty.collections.CollectionEntry
import io.github.orth4x0r.venty.collections.DialInComparison
import io.github.orth4x0r.venty.collections.DialInPick
import io.github.orth4x0r.venty.collections.Impression
import io.github.orth4x0r.venty.ui.Temperature
import org.json.JSONObject
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val Inset = Color(0xFF080E18)
private val BandInk = listOf(
    Color(0xFF7BA7E0), Color(0xFF9A9CDD), Color(0xFF77B8BB),
    Color(0xFFC094CA), Color(0xFFD9AE8C), Color(0xFF8BA7D6),
)

private fun bandInk(band: Int) = BandInk[(band / 5).mod(BandInk.size)]

private fun bandLabel(band: Int, fahrenheit: Boolean): String {
    if (!fahrenheit) return "$band–${band + 4} °C"
    // Convert the inclusive endpoints of the interval to whole display degrees.
    val first = band * 9 / 5 + 32
    val last = first + 8
    return "$first–$last °F"
}

private fun dateLabel(entry: CollectionEntry): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(entry.startMs)

private fun archiveSummary(entry: CollectionEntry): String =
    listOf(if (entry.device.startsWith("demo:")) "Demo" else "Device", entry.note)
        .filter(String::isNotBlank).joinToString(" / ")

@Composable
internal fun CollectionBrowser(
    entries: List<CollectionEntry>,
    maxC: Int,
    fahrenheit: Boolean,
    scope: String,
    onNote: (CollectionEntry) -> Unit,
) {
    var trail by rememberSaveable(scope) { mutableStateOf(false) }
    var fullMap by rememberSaveable(scope) { mutableStateOf(false) }
    var selectedBand by rememberSaveable(scope) { mutableStateOf<Int?>(null) }
    val byBand = remember(entries) {
        entries.flatMap { entry -> entry.bands.distinct().map { it to entry } }
            .groupBy({ it.first }, { it.second })
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        CollectionTab("Atlas", selected = !trail, Modifier.weight(1f)) { trail = false }
        CollectionTab("Ember Trail", selected = trail, Modifier.weight(1f)) { trail = true }
    }
    if (trail) {
        EmberTrail(entries, scope)
    } else {
        S7Text("Temperature Atlas", 17)
        S7Text("Bands stamped from sessions where the device reported reaching its target.", 11, S7.Muted)
        if (byBand.isEmpty()) S7Text("No reached bands in this archive.", 11, S7.Link)
        val topBand = maxOf(maxC, byBand.keys.maxOrNull() ?: maxC).coerceAtMost(230)
        val first = if (fullMap) 40 else maxOf(40, (byBand.keys.minOrNull() ?: 50) - 10)
        val end = if (fullMap) topBand else minOf(topBand, (byBand.keys.maxOrNull() ?: 40) + 10)
        if (byBand.isNotEmpty() || fullMap) {
            AtlasGrid(first..end, byBand.keys, fahrenheit, onSelect = { selectedBand = it })
        }
        if (first != 40 || end != topBand || fullMap) {
            S7Button(if (fullMap) "Show recorded area" else "Show full map", { fullMap = !fullMap })
        }
    }
    selectedBand?.let { band ->
        val sessions = byBand[band].orEmpty()
        UtilityWindow("atlas / ${bandLabel(band, fahrenheit)}", { selectedBand = null }) {
            S7Text("Sessions in this archive", 12)
            SessionPages(sessions, stateKey = band.toString()) { entry ->
                ArchiveRow(dateLabel(entry), archiveSummary(entry)) {
                    selectedBand = null
                    onNote(entry)
                }
            }
        }
    }
}

@Composable
private fun AtlasGrid(range: IntRange, stamps: Set<Int>, fahrenheit: Boolean, onSelect: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().background(Inset).border(1.dp, S7.Border).padding(5.dp)) {
        val columns = (maxWidth.value / 51).toInt().coerceIn(2, 6)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            (range step 5).chunked(columns).forEach { bands ->
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    bands.forEach { band ->
                        val stamped = band in stamps
                        val shade = if (stamped) bandInk(band) else S7.Muted
                        val mark = if (fahrenheit) Temperature.toDisplay(band * 10, true) else band
                        Box(
                            Modifier.weight(1f).heightIn(min = 48.dp)
                                .background(if (stamped) shade.copy(alpha = .17f) else Inset)
                                .border(1.dp, if (stamped) shade.copy(alpha = .8f) else Color(0xFF293443))
                                .clickable(
                                    enabled = stamped,
                                    role = Role.Button,
                                    onClick = { onSelect(band) },
                                )
                                .semantics { contentDescription = "${bandLabel(band, fahrenheit)}, ${if (stamped) "stamped" else "unrecorded"}" }
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$mark°", color = shade, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                Text(if (stamped) "◆" else "·", color = shade.copy(alpha = .8f), fontSize = 9.sp)
                            }
                        }
                    }
                    repeat(columns - bands.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun CollectionTab(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 48.dp).background(if (selected) S7.Selection else Inset)
            .border(1.dp, if (selected) S7.Link else S7.Border)
            .selectable(selected, role = Role.Tab, onClick = onClick)
            .padding(5.dp),
        contentAlignment = Alignment.Center,
    ) { S7Text(text, 12) }
}

@Composable
private fun EmberTrail(entries: List<CollectionEntry>, scope: String) {
    S7Text("Ember Trail", 17)
    if (entries.isEmpty()) {
        S7Text("The trail draws one ember for each recorded session.", 11, S7.Muted)
        return
    }
    // Keep each ember in chronological order within a fixed page.
    val chronological = remember(entries) { entries.asReversed() }
    val pageSize = 32
    val lastPage = (entries.size - 1) / pageSize
    var selectedPage by rememberSaveable(scope) { mutableStateOf<Int?>(null) }
    val page = (selectedPage ?: lastPage).coerceIn(0, lastPage)
    val embers = chronological.drop(page * pageSize).take(pageSize)
    val rows = (embers.size + 7) / 8
    Canvas(
        Modifier.fillMaxWidth().height(maxOf(92, rows * 34 + 28).dp)
            .background(Inset).border(1.dp, S7.Border)
            .semantics { contentDescription = "Ember Trail, page ${page + 1} of ${lastPage + 1}. ${embers.size} recorded sessions." },
    ) {
        val step = size.width / 9f
        val rowHeight = size.height / (rows + 1)
        var previous: Offset? = null
        embers.forEachIndexed { index, entry ->
            val row = index / 8
            val column = if (row % 2 == 0) index % 8 else 7 - index % 8
            val center = Offset(step * (column + 1), rowHeight * (row + 1))
            val ink = entry.bands.firstOrNull()?.let(::bandInk) ?: S7.Caption
            previous?.let { drawLine(Color(0xFF414C6F), it, center, 1.dp.toPx()) }
            drawCircle(ink.copy(alpha = .18f), 7.dp.toPx(), center)
            drawCircle(ink, 3.dp.toPx(), center)
            previous = center
        }
    }
    S7Text("${dateLabel(embers.first())} — ${dateLabel(embers.last())}", 11, S7.Muted)
    if (lastPage > 0) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            S7Button("Older", { selectedPage = page - 1 }, Modifier.weight(1f), enabled = page > 0)
            S7Button("Newer", {
                selectedPage = (page + 1).takeIf { it < lastPage }
            }, Modifier.weight(1f), enabled = page < lastPage)
        }
    }
}

private val ComparisonSaver = Saver<DialInComparison, String>(
    save = { it.toJson().toString() },
    restore = { DialInComparison.fromJson(JSONObject(it)) },
)

@Composable
internal fun DialInPanel(
    entries: List<CollectionEntry>,
    saved: DialInComparison?,
    scope: String,
    fahrenheit: Boolean,
    onSave: (DialInComparison) -> Boolean,
) {
    val byId = remember(entries) { entries.associateBy { it.id } }
    var draft by rememberSaveable(scope, saved, stateSaver = ComparisonSaver) {
        mutableStateOf(saved ?: DialInComparison(emptyList()))
    }
    var picking by rememberSaveable(scope) { mutableStateOf(false) }
    var bandToPick by rememberSaveable(scope) { mutableStateOf<Int?>(null) }
    val chosenIds = draft.picks.mapTo(mutableSetOf()) { it.sessionId }
    val chosenBands = draft.picks.mapTo(mutableSetOf()) { it.band }
    val choices = remember(entries, draft.picks.map { it.sessionId to it.band }) {
        entries.asSequence().filter { it.id !in chosenIds }
            .flatMap { entry -> entry.bands.distinct().asSequence().filter { it !in chosenBands }.map { it to entry } }
            .groupBy({ it.first }, { it.second }).toSortedMap()
    }

    S7Text("Compare two or three recorded bands. Add an impression or a short note if you like.", 12, S7.Muted)
    draft.picks.forEachIndexed { index, pick ->
        val entry = byId[pick.sessionId]
        S7Group(bandLabel(pick.band, fahrenheit)) {
            S7Text(entry?.let(::dateLabel) ?: "Session unavailable", 12)
            if (entry != null) S7Text(archiveSummary(entry), 11, S7.Muted)
            Impression.entries.forEach { impression ->
                val label = if (impression == Impression.Enjoyed) "🙂  Enjoyed" else "😐  Neutral"
                S7Check(label, pick.impression == impression, enabled = entry != null) { checked ->
                    val newPicks = draft.picks.toMutableList()
                    newPicks[index] = pick.copy(impression = impression.takeIf { checked })
                    draft = draft.copy(picks = newPicks)
                }
            }
            S7Button("Remove from comparison", { draft = draft.copy(picks = draft.picks.filterNot { it == pick }) })
        }
    }
    if (draft.picks.size < 3) {
        if (choices.isEmpty() && draft.picks.isEmpty()) {
            S7Text("There are no archived sessions with reached bands in this view.", 11, S7.Link)
        }
        S7Button("Choose a past session…", { bandToPick = null; picking = true }, enabled = choices.isNotEmpty())
    }
    if (draft.picks.isNotEmpty() || saved != null) {
        S7Input("Comparison note / optional", draft.note, { draft = draft.copy(note = it.take(120)) })
        S7Button(
            if (saved == null) "Save comparison" else "Update saved comparison",
            { onSave(draft) },
            enabled = draft.picks.size >= 2 && draft != saved &&
                draft.picks.all { it.band in byId[it.sessionId]?.bands.orEmpty() },
        )
        if (saved != null) {
            if (draft == saved) S7Text("Comparison saved on this device.", 11, S7.Link)
            else S7Button("Restore saved comparison", { draft = saved })
        }
    }
    if (picking) {
        UtilityWindow("dial-in / choose a session", { picking = false }) {
            val band = bandToPick
            if (band == null) {
                S7Text("Choose a recorded band", 12)
                choices.keys.forEach { candidate ->
                    S7Button(bandLabel(candidate, fahrenheit), { bandToPick = candidate }, Modifier.fillMaxWidth())
                }
            } else {
                S7Text(bandLabel(band, fahrenheit), 14)
                S7Button("Back to bands", { bandToPick = null })
                SessionPages(choices[band].orEmpty(), stateKey = band.toString()) { entry ->
                    ArchiveRow(dateLabel(entry), archiveSummary(entry)) {
                        draft = draft.copy(picks = draft.picks + DialInPick(entry.id, band))
                        picking = false
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionPages(
    sessions: List<CollectionEntry>,
    stateKey: String,
    row: @Composable (CollectionEntry) -> Unit,
) {
    var currentPage by rememberSaveable(stateKey) { mutableIntStateOf(0) }
    val lastPage = ((sessions.size - 1).coerceAtLeast(0)) / 8
    val page = currentPage.coerceIn(0, lastPage)
    sessions.drop(page * 8).take(8).forEach { row(it) }
    if (lastPage > 0) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            S7Button("Newer", { currentPage = page - 1 }, enabled = page > 0)
            S7Button("Older", { currentPage = page + 1 }, enabled = page < lastPage)
        }
    }
}

@Composable
internal fun WeeklyPostcard(
    entries: List<CollectionEntry>,
    fahrenheit: Boolean,
    putAwayWeek: String?,
    onPutAway: (String?) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val weekKey = monday.toString()
    val week = remember(entries, today, zone) {
        entries.filter {
            val day = Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate()
            day in monday..today
        }
    }
    val band = remember(week) {
        week.flatMap { it.bands.distinct() }.groupingBy { it }.eachCount().entries
            .maxWithOrNull(compareBy<Map.Entry<Int, Int>> { it.value }.thenByDescending { it.key })?.key
    }
    if (putAwayWeek == weekKey) {
        S7Text("This week’s postcard is put away.", 12, S7.Muted)
        S7Button("View postcard", { onPutAway(null) })
        return
    }

    val dismiss by rememberUpdatedState(onPutAway)
    val cardColor = band?.let(::bandInk) ?: S7.Caption
    Column(
        Modifier.fillMaxWidth().border(1.dp, cardColor)
            .background(Brush.verticalGradient(listOf(Color(0xFF172842), Inset)))
            .pointerInput(weekKey) {
                var drag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = { if (kotlin.math.abs(drag) >= 64.dp.toPx()) dismiss(weekKey) },
                ) { _, amount -> drag += amount }
            }.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        S7Text("Week of ${monday.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))}", 11, cardColor)
        Canvas(Modifier.fillMaxWidth().height(76.dp).background(Inset)) {
            repeat(16) { index ->
                val width = size.width / 16
                val height = size.height * (.26f + ((index * 7 + 3) % 11) * .056f)
                drawRect(
                    color = cardColor.copy(alpha = .28f + index * .032f),
                    topLeft = Offset(index * width, size.height - height),
                    size = androidx.compose.ui.geometry.Size(width - 1.dp.toPx(), height),
                )
            }
        }
        S7Text("${week.size} recorded ${if (week.size == 1) "session" else "sessions"}", 17)
        S7Text(band?.let { "Most recorded band  ${bandLabel(it, fahrenheit)}" } ?: "No reached band recorded this week.", 11, S7.Muted)
    }
    S7Button("Put postcard away", { onPutAway(weekKey) })
}
