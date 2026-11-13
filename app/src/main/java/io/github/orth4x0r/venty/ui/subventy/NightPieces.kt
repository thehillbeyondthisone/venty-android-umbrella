package io.github.orth4x0r.venty.ui.subventy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/* Taskbar, screensaver, ICEBREAKER and loot surfaces. Pure Compose; the screen supplies state. */

@Composable internal fun rememberNow(periodMs: Long = 15_000): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) { while (true) { now = System.currentTimeMillis(); delay(periodMs - now % periodMs) } }
    return now
}

/** Thin to dense; indices [CURL_LEFT]/[CURL_RIGHT] are the curls used mid-plume. */
private const val VAPOR_GLYPHS = " .':()*o8@"
private const val CURL_LEFT = 4
private const val CURL_RIGHT = 5

/** A stable, well-mixed 0..1 value per particle index. */
private fun hash01(i: Int): Float {
    var h = i * -0x61c88647
    h = h xor (h ushr 15); h *= 0x2c1b3c6d; h = h xor (h ushr 12)
    return (h ushr 8) / 16777216f
}

private val hhmm = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

/** A 98-style taskbar: the app's task, the current notice, and a tray with link, battery, moon and clock. */
@Composable internal fun Taskbar(notice: String, link: String, battery: Int?, charging: Boolean, onTask: () -> Unit,
    modifier: Modifier = Modifier) {
    val now = rememberNow()
    val moon = Moon.at(now)
    val time = hhmm.format(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()))
    Row(modifier.fillMaxWidth().background(Night.Panel).border(1.dp, Night.Edge).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.heightIn(min = 40.dp).border(1.dp, Color(0xFF6E73A0)).background(Night.PanelHi)
            .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = "Terminal" }
            .clickable(onClick = onTask).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SilverSeven(Modifier.size(16.dp), watermark = false)
            S7Text("SubVenty", 11, Color.White, Modifier.padding(start = 5.dp))
        }
        Box(Modifier.weight(1f).heightIn(min = 40.dp).border(1.dp, Night.Edge).padding(horizontal = 6.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }, contentAlignment = Alignment.CenterStart) {
            S7Text(notice, 10, Night.Text)
        }
        Row(Modifier.heightIn(min = 40.dp).border(1.dp, Night.Edge).padding(horizontal = 6.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$link. ${battery?.let { "Battery $it percent${if (charging) ", charging" else ""}. " } ?: ""}" +
                    "Moon ${moon.name}. $time"
            }, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(when (link) { "online" -> Night.Ready; "demo" -> Color(0xFFB9A8FF); else -> Night.Muted }))
            battery?.let { S7Text("$it%${if (charging) "+" else ""}", 10, if (it <= 15) Night.Heat else Night.Text) }
            MoonGlyph(moon.fraction, Modifier.size(12.dp))
            S7Text(time, 11, Color.White)
        }
    }
}

/**
 * vapor.scr: a nightstand view. ASCII vapour drifts up past a big clock, with the device state kept
 * readable. With reduced motion the vapour holds still.
 */
@Composable internal fun VaporScreensaver(ctx: TermContext, onWake: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPhosphor.current
    val reduced = LocalReducedMotion.current
    val measurer = rememberTextMeasurer()
    val font = LocalTermFont.current
    val glyphStyle = termStyle(16.sp, p.bright, glow = false).copy(fontFamily = font)
    val glyphs = remember(glyphStyle) { VAPOR_GLYPHS.map { measurer.measure(it.toString(), glyphStyle) } }
    var t by remember { mutableFloatStateOf(0f) }
    if (!reduced) LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) withFrameMillis { t = (it - start) / 1000f }
    }
    val now = rememberNow(1000)
    val zoned = Instant.ofEpochMilli(now).atZone(ctx.zone)
    val unit = io.github.orth4x0r.venty.ui.Temperature.unit(ctx.fahrenheit)
    val stateLine = when {
        !ctx.connected -> "no carrier"
        !ctx.heating -> "idle // ${io.github.orth4x0r.venty.ui.Temperature.toDisplay(ctx.targetTenths, ctx.fahrenheit)}°$unit"
        ctx.reached -> "locked // ${io.github.orth4x0r.venty.ui.Temperature.toDisplay(ctx.effectiveTenths, ctx.fahrenheit)}°$unit"
        else -> "warming // ${io.github.orth4x0r.venty.ui.Temperature.toDisplay(ctx.effectiveTenths, ctx.fahrenheit)}°$unit"
    }
    Box(modifier.fillMaxSize().background(Color.Black)
        .semantics { contentDescription = "Screensaver. ${hhmm.format(zoned)}. $stateLine. Tap to return." ; role = Role.Button }
        .clickable(onClick = onWake)) {
        Canvas(Modifier.fillMaxSize()) {
            // One plume rising from the bottom centre: dense glyphs low down, curls as it sways, thin dots as it fades.
            val count = 280
            for (i in 0 until count) {
                val seed = hash01(i)
                val jitter = hash01(i + 7331)
                val age = (t * .07f + hash01(i + 1913)) % 1f
                val y = size.height * (1.02f - age * .78f)
                val swayPhase = age * 7f + t * .5f + seed * 1.3f
                val spread = size.width * (.018f + age * age * .30f)
                val x = size.width / 2 + sin(swayPhase).toFloat() * size.width * .05f * (.4f + age * 1.6f) + (jitter - .5f) * 2f * spread
                val alpha = (1f - age) * (1f - age) * .75f
                if (alpha < .03f) continue
                val idx = when {
                    age < .12f -> glyphs.size - 1 - (seed * 2).toInt()
                    age < .55f -> if (kotlin.math.cos(swayPhase) > 0) CURL_RIGHT else CURL_LEFT
                    else -> 1 + (seed * 3).toInt()
                }.coerceIn(0, glyphs.size - 1)
                val g = glyphs[idx]
                drawText(g, topLeft = Offset(x - g.size.width / 2f, y), alpha = alpha)
            }
        }
        Column(Modifier.align(Alignment.TopCenter).padding(top = 96.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Term(hhmm.format(zoned), 92.sp, p.bright)
            Term(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH).format(zoned).lowercase(), 22.sp, p.dim)
            Spacer(Modifier.height(10.dp))
            Term(stateLine, 26.sp, when { ctx.reached && ctx.heating -> Night.Ready; ctx.heating -> Night.Heat; else -> p.dim })
            ctx.battery?.let { Term("bat ${Terminal.bar(it, 10)} $it%", 20.sp, if (it <= 15) Night.Heat else p.dim) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoonGlyph(Moon.at(now).fraction, Modifier.size(18.dp))
                Term("  ${Moon.at(now).name}", 18.sp, p.dim)
            }
        }
        Term("vapor.scr // tap to wake", 16.sp, p.dim.copy(alpha = .7f), Modifier.align(Alignment.BottomCenter).padding(18.dp))
    }
}

/** The body of the ICEBREAKER window. [pulled] is the loot released by the current crack, if any. */
@Composable internal fun IcebreakerBody(game: IcebreakerGame, onGame: (IcebreakerGame) -> Unit, pulled: LootFile?,
    nothingLeft: Boolean, onRead: (LootFile) -> Unit, onNewNode: () -> Unit) {
    val p = LocalPhosphor.current
    BoxWithConstraints(Modifier.fillMaxWidth().background(Color.Black).border(1.dp, p.dim).padding(8.dp)) {
        val density = LocalDensity.current
        val s = fitColumns(with(density) { maxWidth.toPx() }, 30, density.density, density.fontScale)
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Term("ICEBREAKER v1.3", (s.value * 1.3f).sp, p.bright)
            Term("node ${game.node}", s, p.dim)
            val traceColor = when { game.trace >= 80 -> Night.Alarm; game.trace >= 50 -> Night.Heat; else -> p.bright }
            Term("trace ${Terminal.bar(game.trace, 14)} ${game.trace}%", s, traceColor,
                Modifier.semantics { contentDescription = "Trace ${game.trace} percent. ${IcebreakerGame.MAX_ATTEMPTS - game.attempts.size} attempts left." })
            Spacer(Modifier.height(2.dp))
            game.attempts.forEachIndexed { i, a -> AttemptRow(i + 1, a, s) }
            when (game.state) {
                BreakState.Running -> {
                    val slots = (0 until IcebreakerGame.CODE_LENGTH).joinToString(" ") { game.draft.getOrNull(it)?.toString() ?: "_" }
                    Term(String.format(Locale.ROOT, "%02d  %s", game.attempts.size + 1, slots), (s.value * 1.25f).sp, p.bright,
                        Modifier.semantics { contentDescription = "Current guess ${game.draft.ifEmpty { "empty" }}" })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IcebreakerGame.GLYPHS.forEach { g ->
                            TermKey(g.toString(), "Glyph $g", Modifier.weight(1f), (s.value * 1.2f).sp,
                                enabled = game.draft.length < IcebreakerGame.CODE_LENGTH) { onGame(game.type(g)) }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TermKey("del", "Delete last glyph", Modifier.weight(1f), s, enabled = game.draft.isNotEmpty()) { onGame(game.backspace()) }
                        TermKey("probe >", "Submit guess", Modifier.weight(2f), s,
                            enabled = game.draft.length == IcebreakerGame.CODE_LENGTH) { onGame(game.submit()) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pegs(1, 0, 1, Modifier.size(width = 10.dp, height = 10.dp)); Term(" locked  ", s, p.dim)
                        Pegs(0, 1, 1, Modifier.size(width = 10.dp, height = 10.dp)); Term(" loose", s, p.dim)
                    }
                    Term("no clock. take your time.", s, p.dim)
                }
                BreakState.Cracked -> {
                    Term("ACCESS GRANTED", (s.value * 1.6f).sp, Night.Ready)
                    when {
                        pulled != null -> {
                            Term("pulled ${pulled.name} (${pulled.bytes} bytes)", s, p.bright)
                            TermKey("> cat ${pulled.name}", "Read ${pulled.name}", Modifier.fillMaxWidth(), s) { onRead(pulled) }
                        }
                        nothingLeft -> Term("no new files here. the board is quiet.", s, p.dim)
                    }
                    TermKey("> next node", "Crack another node", Modifier.fillMaxWidth(), s, onClick = onNewNode)
                }
                BreakState.Traced -> {
                    Term("TRACE COMPLETE", (s.value * 1.6f).sp, Night.Alarm)
                    Term("carrier lost. the code was ${game.secret.toList().joinToString(" ")}.", s, p.dim)
                    TermKey("> redial", "Try a new node", Modifier.fillMaxWidth(), s, onClick = onNewNode)
                }
            }
        }
    }
}

@Composable private fun AttemptRow(n: Int, a: Attempt, s: TextUnit) {
    val p = LocalPhosphor.current
    Row(Modifier.semantics(mergeDescendants = true) {
        contentDescription = "Attempt $n, ${a.guess.toList().joinToString(" ")}: ${a.probe.exact} locked, ${a.probe.near} loose"
    }, verticalAlignment = Alignment.CenterVertically) {
        Term(String.format(Locale.ROOT, "%02d  %s   ", n, a.guess.toList().joinToString(" ")), (s.value * 1.25f).sp,
            if (a.probe.exact == IcebreakerGame.CODE_LENGTH) Night.Ready else p.bright)
        Pegs(a.probe.exact, a.probe.near, IcebreakerGame.CODE_LENGTH, Modifier.size(width = 56.dp, height = 10.dp))
    }
}

/** Filled squares for locked glyphs, hollow for loose ones, dots for misses. */
@Composable private fun Pegs(exact: Int, near: Int, slots: Int, modifier: Modifier) {
    val p = LocalPhosphor.current
    Canvas(modifier) {
        val cell = size.height
        val gap = cell * .45f
        for (i in 0 until slots) {
            val x = i * (cell + gap)
            val tl = Offset(x, 0f)
            val sz = androidx.compose.ui.geometry.Size(cell, cell)
            when {
                i < exact -> drawRect(p.bright, tl, sz)
                i < exact + near -> drawRect(p.bright, tl, sz, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                else -> drawCircle(p.dim.copy(alpha = .5f), cell * .12f, Offset(x + cell / 2, cell / 2))
            }
        }
    }
}

/** ~/loot as a directory listing, plus the things found at the prompt. */
@Composable internal fun LootListing(found: List<Pair<LootFile, Long>>, secrets: Set<String>, onOpen: (LootFile) -> Unit,
    onIcebreaker: () -> Unit) {
    val p = LocalPhosphor.current
    BoxWithConstraints(Modifier.fillMaxWidth().background(Color.Black).border(1.dp, p.dim).padding(8.dp)) {
        val density = LocalDensity.current
        val s = fitColumns(with(density) { maxWidth.toPx() }, 32, density.density, density.fontScale)
        val date = DateTimeFormatter.ofPattern("MMM dd HH:mm", Locale.ENGLISH)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Term("$ ls -l ~/loot", s, Night.Ready)
            if (found.isEmpty()) {
                Term("total 0", s, p.dim)
                Term("nothing pulled yet. crack a node", s, p.dim)
                Term("with icebreaker to pull a file.", s, p.dim)
            } else {
                Term("total ${found.size}", s, p.dim)
                found.forEach { (file, at) ->
                    Box(Modifier.fillMaxWidth().heightIn(min = 44.dp)
                        .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = "Open ${file.name}" }
                        .clickable { onOpen(file) }, contentAlignment = Alignment.CenterStart) {
                        Term(String.format(Locale.ROOT, "%5d %s %s", file.bytes, date.format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())).lowercase(), file.name), s, p.bright)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            TermKey("> ./icebreaker.exe", "Open ICEBREAKER", Modifier.fillMaxWidth(), s, onClick = onIcebreaker)
            if (secrets.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Term("$ cat ~/.found", s, Night.Ready)
                secrets.sorted().chunked(3).forEach { Term(it.joinToString("  "), s, toneColor(Tone.Irc, p)) }
            }
        }
    }
}

/** A text file in a terminal pane. Scrolls both ways rather than reflowing ASCII art. */
@Composable internal fun LootReader(file: LootFile) {
    val p = LocalPhosphor.current
    BoxWithConstraints(Modifier.fillMaxWidth().background(Color.Black).border(1.dp, p.dim).padding(8.dp)) {
        val density = LocalDensity.current
        val s = fitColumns(with(density) { maxWidth.toPx() }, 32, density.density, density.fontScale)
        Column(Modifier.horizontalScroll(rememberScrollState()).semantics(mergeDescendants = true) { contentDescription = file.text }) {
            Term("$ cat ${file.name}", s, Night.Ready)
            file.text.lines().forEach { Term(it.ifEmpty { " " }, s, p.bright) }
            Term("-- eof --", s, p.dim)
        }
    }
}
