package io.github.orth4x0r.venty.ui.subventy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.orth4x0r.venty.ui.Temperature
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.sin

/* ICEBREAKER, loot and the screensaver, drawn with the client's own parts. The screen supplies state. */

/** Original SubSeven menu border ($00C08000 in MainColors.pas, BGR) — used for progress segments. */
private val MenuBorder = Color(0xFF0080C0)

@Composable internal fun rememberNow(periodMs: Long = 15_000): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) { while (true) { now = System.currentTimeMillis(); delay(periodMs - now % periodMs) } }
    return now
}

internal val hhmm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

/** A 98-style segmented progress bar. */
@Composable internal fun SegmentBar(percent: Int, modifier: Modifier = Modifier, color: Color = MenuBorder) {
    Canvas(modifier.height(16.dp).border(1.dp, S7.Border).background(Console.Box).padding(2.dp)) {
        val seg = 8.dp.toPx(); val gap = 2.dp.toPx()
        val filled = size.width * percent.coerceIn(0, 100) / 100f
        var x = 0f
        while (x + seg <= filled + .5f) { drawRect(color, Offset(x, 0f), Size(seg, size.height)); x += seg + gap }
    }
}

/**
 * Screensaver: the silver seven over black, a clock, the device state, and a thin plume of drifting
 * characters. With reduced motion the plume holds still.
 */
@Composable internal fun VaporScreensaver(ctx: TermContext, onWake: () -> Unit, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val measurer = rememberTextMeasurer()
    val glyphs = remember { VAPOR_GLYPHS.map { measurer.measure(it.toString(), TextStyle(color = Color(0xFFA7ADB9), fontSize = 15.sp)) } }
    var t by remember { mutableFloatStateOf(0f) }
    if (!reduced) LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) withFrameMillis { t = (it - start) / 1000f }
    }
    val now = rememberNow(1000)
    val zoned = Instant.ofEpochMilli(now).atZone(ctx.zone)
    val unit = Temperature.unit(ctx.fahrenheit)
    val stateLine = when {
        !ctx.connected -> "not connected"
        !ctx.heating -> "heater off / ${Temperature.toDisplay(ctx.targetTenths, ctx.fahrenheit)}°$unit"
        ctx.reached -> "target reached / ${Temperature.toDisplay(ctx.effectiveTenths, ctx.fahrenheit)}°$unit"
        else -> "heating / ${Temperature.toDisplay(ctx.effectiveTenths, ctx.fahrenheit)}°$unit"
    }
    Box(modifier.fillMaxSize().background(Color.Black)
        .semantics { contentDescription = "Screensaver. ${hhmm.format(zoned)}. $stateLine. Tap to return."; role = Role.Button }
        .clickable(onClick = onWake)) {
        Canvas(Modifier.fillMaxSize()) {
            val count = 240
            for (i in 0 until count) {
                val age = (t * .07f + hash01(i + 1913)) % 1f
                val y = size.height * (1.02f - age * .62f)
                val swayPhase = age * 7f + t * .5f + hash01(i) * 1.3f
                val spread = size.width * (.018f + age * age * .30f)
                val x = size.width / 2 + sin(swayPhase) * size.width * .05f * (.4f + age * 1.6f) + (hash01(i + 7331) - .5f) * 2f * spread
                val alpha = (1f - age) * (1f - age) * .7f
                if (alpha < .03f) continue
                val idx = when {
                    age < .12f -> glyphs.size - 1
                    age < .55f -> if (kotlin.math.cos(swayPhase) > 0) CURL_RIGHT else CURL_LEFT
                    else -> 1 + (hash01(i) * 2).toInt()
                }.coerceIn(0, glyphs.size - 1)
                val g = glyphs[idx]
                drawText(g, topLeft = Offset(x - g.size.width / 2f, y), alpha = alpha)
            }
        }
        Column(Modifier.align(Alignment.TopCenter).padding(top = 72.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SilverSeven(Modifier.size(120.dp), watermark = false)
            Text(hhmm.format(zoned), color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Light)
            S7Text(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH).format(zoned).lowercase(), 14, S7.Muted)
            S7Text(stateLine, 15, when { ctx.reached && ctx.heating -> Console.Ok; ctx.heating -> Console.Warn; else -> S7.Link })
            ctx.battery?.let { S7Text("battery $it%", 12, S7.Muted) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoonGlyph(Moon.at(now).fraction, Modifier.size(14.dp))
                S7Text("  ${Moon.at(now).name} moon", 12, S7.Muted)
            }
        }
        S7Text("SubVenty screensaver / tap to return", 11, S7.Muted, Modifier.align(Alignment.BottomCenter).padding(18.dp))
    }
}

private const val VAPOR_GLYPHS = " .':()o"
private const val CURL_LEFT = 4
private const val CURL_RIGHT = 5

/** A stable, well-mixed 0..1 value per particle index. */
private fun hash01(i: Int): Float {
    var h = i * -0x61c88647
    h = h xor (h ushr 15); h *= 0x2c1b3c6d; h = h xor (h ushr 12)
    return (h ushr 8) / 16777216f
}

/** The body of the ICEBREAKER utility window. [pulled] is the file released by the current crack, if any. */
@Composable internal fun IcebreakerBody(game: IcebreakerGame, onGame: (IcebreakerGame) -> Unit, pulled: LootFile?,
    nothingLeft: Boolean, onRead: (LootFile) -> Unit, onNewNode: () -> Unit) {
    S7Text("remote node: ${game.node}", 11, S7.Muted)
    S7Group("trace") {
        SegmentBar(game.trace, Modifier.fillMaxWidth().semantics {
            contentDescription = "Trace ${game.trace} percent. ${IcebreakerGame.MAX_ATTEMPTS - game.attempts.size} attempts left."
        }, if (game.trace >= 80) Console.Err else MenuBorder)
        S7Text("${game.attempts.size} / ${IcebreakerGame.MAX_ATTEMPTS} probes", 11, S7.Muted)
    }
    S7Group("access code") {
        game.attempts.forEachIndexed { i, a -> AttemptRow(i + 1, a) }
        when (game.state) {
            BreakState.Running -> {
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Current probe ${game.draft.ifEmpty { "empty" }}" }) {
                    S7Text(String.format(Locale.ROOT, "%02d", game.attempts.size + 1), 11, S7.Muted, Modifier.width(24.dp))
                    repeat(IcebreakerGame.CODE_LENGTH) { GlyphCell(game.draft.getOrNull(it), active = it == game.draft.length) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IcebreakerGame.GLYPHS.forEach { g ->
                        S7Button(g.toString(), { onGame(game.type(g)) }, Modifier.weight(1f), game.draft.length < IcebreakerGame.CODE_LENGTH)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    S7Button("clear", { onGame(game.backspace()) }, Modifier.weight(1f), game.draft.isNotEmpty())
                    S7Button("send probe", { onGame(game.submit()) }, Modifier.weight(2f), game.draft.length == IcebreakerGame.CODE_LENGTH)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pegs(1, 0, 1, Modifier.size(10.dp)); S7Text(" right place   ", 11, S7.Muted)
                    Pegs(0, 1, 1, Modifier.size(10.dp)); S7Text(" wrong place", 11, S7.Muted)
                }
            }
            BreakState.Cracked -> S7Text("access granted", 13, Console.Ok)
            BreakState.Traced -> S7Text("connection traced. the code was ${game.secret.toList().joinToString(" ")}.", 12, Console.Err)
        }
    }
    when (game.state) {
        BreakState.Running -> S7Text("Guess the four-letter code. There is no clock.", 11, S7.Muted)
        BreakState.Cracked -> {
            when {
                pulled != null -> {
                    S7Text("downloaded ${pulled.name} (${pulled.bytes} bytes) to loot", 12)
                    S7Button("open ${pulled.name}", { onRead(pulled) }, Modifier.fillMaxWidth())
                }
                nothingLeft -> S7Text("No new files on this node.", 12, S7.Muted)
            }
            S7Button("next node", onNewNode, Modifier.fillMaxWidth())
        }
        BreakState.Traced -> S7Button("reconnect to a new node", onNewNode, Modifier.fillMaxWidth())
    }
}

@Composable private fun GlyphCell(c: Char?, active: Boolean) {
    Box(Modifier.padding(end = 4.dp).size(30.dp).border(1.dp, if (active) Color.White else Console.BoxEdge).background(Console.Box),
        contentAlignment = Alignment.Center) {
        S7Text(c?.toString() ?: "", 14, Color.White)
    }
}

@Composable private fun AttemptRow(n: Int, a: Attempt) {
    Row(Modifier.semantics(mergeDescendants = true) {
        contentDescription = "Probe $n, ${a.guess.toList().joinToString(" ")}: ${a.probe.exact} right place, ${a.probe.near} wrong place"
    }, verticalAlignment = Alignment.CenterVertically) {
        S7Text(String.format(Locale.ROOT, "%02d", n), 11, S7.Muted, Modifier.width(24.dp))
        a.guess.forEach { GlyphCell(it, active = false) }
        Spacer(Modifier.width(6.dp))
        Pegs(a.probe.exact, a.probe.near, IcebreakerGame.CODE_LENGTH, Modifier.size(width = 56.dp, height = 10.dp))
    }
}

/** Filled squares for right-place glyphs, outlined for wrong-place, dots for misses. */
@Composable private fun Pegs(exact: Int, near: Int, slots: Int, modifier: Modifier) {
    Canvas(modifier) {
        val cell = size.height
        val gap = cell * .45f
        for (i in 0 until slots) {
            val x = i * (cell + gap)
            when {
                i < exact -> drawRect(S7.Link, Offset(x, 0f), Size(cell, cell))
                i < exact + near -> drawRect(S7.Link, Offset(x, 0f), Size(cell, cell), style = Stroke(1.5.dp.toPx()))
                else -> drawCircle(S7.Muted.copy(alpha = .5f), cell * .12f, Offset(x + cell / 2, cell / 2))
            }
        }
    }
}

/** The loot folder, as archive rows, plus the commands found at the prompt. */
@Composable internal fun LootListing(found: List<Pair<LootFile, Long>>, secrets: Set<String>, onOpen: (LootFile) -> Unit,
    onIcebreaker: () -> Unit) {
    val date = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH)
    S7Text("Files downloaded from cracked nodes. Not linked to your sessions.", 11, S7.Muted)
    if (found.isEmpty()) S7Text("Nothing downloaded yet. Crack a node in ICEBREAKER to get a file.", 12, S7.Muted)
    found.forEach { (file, at) ->
        ArchiveRow(file.name, "${file.bytes} bytes / ${date.format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))}") { onOpen(file) }
    }
    S7Button("ICEBREAKER…", onIcebreaker, Modifier.fillMaxWidth())
    if (secrets.isNotEmpty()) S7Group("commands found at the prompt") {
        S7Text(secrets.sorted().joinToString("  "), 12, S7.Link)
    }
}

/** A text file in the dark input well, fixed-width so the ASCII art lines up, scrolling sideways if needed. */
@Composable internal fun LootReader(file: LootFile) {
    Box(Modifier.fillMaxWidth().border(1.dp, Console.BoxEdge).background(Console.Box).padding(9.dp)) {
        Text(file.text, Modifier.horizontalScroll(rememberScrollState()), color = Color.White, fontSize = 12.sp,
            lineHeight = 16.sp, fontFamily = FontFamily.Monospace, softWrap = false)
    }
}
