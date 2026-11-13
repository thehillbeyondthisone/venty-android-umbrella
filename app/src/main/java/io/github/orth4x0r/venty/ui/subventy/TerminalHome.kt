package io.github.orth4x0r.venty.ui.subventy

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.orth4x0r.venty.ui.Temperature
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

/** Everything the terminal home draws. Built by the screen from controller state; pure for previews. */
data class HomeState(
    val ctx: TermContext,
    val feed: List<TermLine>,
    val session: String? = null,
    val etaSeconds: Int? = null,
    val lootCount: Int = 0,
)

class HomeActions(
    val onCommand: (String) -> Unit,
    val onTargetDrag: (Int) -> Unit,
    val onTargetDone: () -> Unit,
)

internal fun toneColor(tone: Tone, p: Phosphor): Color = when (tone) {
    Tone.Echo -> Night.Text
    Tone.Out -> p.bright
    Tone.Info -> Color(0xFFB9A8FF)
    Tone.Ok -> Night.Ready
    Tone.Warn -> Night.Heat
    Tone.Err -> Night.Alarm
    Tone.Irc -> Color(0xFF8CC8FF)
    Tone.Dim -> p.dim
}

/** The after-hours home: a big readout to glance at, controls to reach, and a terminal to play in. */
@Composable internal fun TerminalHome(state: HomeState, actions: HomeActions, input: String, onInput: (String) -> Unit,
    modifier: Modifier = Modifier) {
    val ctx = state.ctx
    val p = LocalPhosphor.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val small = fitColumns(with(density) { maxWidth.toPx() }, 36, density.density, density.fontScale)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Readout(ctx, state.etaSeconds, small)
            if (ctx.connected) {
                TargetBar(ctx, actions.onTargetDrag, actions.onTargetDone, small)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TermKey("[ - ]", "Lower target", Modifier.weight(1f), small) { actions.onCommand("-") }
                    TermKey("[ + ]", "Raise target", Modifier.weight(1f), small) { actions.onCommand("+") }
                }
                if (ctx.hasBoost) ModeTabs(ctx, small) { actions.onCommand(it.name.lowercase()) }
                RunButton(ctx, small) { actions.onCommand(if (ctx.heating) "off" else "heat") }
                if (ctx.hasPump) TermKey(if (ctx.pumpOn) "> KILL pump.exe" else "> RUN pump.exe",
                    if (ctx.pumpOn) "Stop pump" else "Start pump", Modifier.fillMaxWidth(), small) { actions.onCommand("pump") }
            } else {
                Term("no carrier. dial a device:", small, p.dim)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TermKey("> connect", "Connect to your device", Modifier.weight(1f), small) { actions.onCommand("connect") }
                    TermKey("> demo", "Try the demo Venty", Modifier.weight(1f), small) { actions.onCommand("demo") }
                }
            }
            StatsLine(state, (small.value * .9f).sp)
            Macros(macroCommands(ctx), small, actions.onCommand)
            Console(state.feed, small, Modifier.fillMaxWidth().height(with(density) { (small.toPx() * 1.05f * 10).toDp() } + 12.dp))
            PromptLine(ctx.handle, input, onInput, small) { actions.onCommand(it) }
        }
    }
}

/** Target in big phosphor digits. The digits never pretend to be a live reading on a Venty. */
@Composable private fun Readout(ctx: TermContext, eta: Int?, small: TextUnit) {
    val p = LocalPhosphor.current
    val reduced = LocalReducedMotion.current
    val unit = Temperature.unit(ctx.fahrenheit)
    val shown = Temperature.toDisplay(ctx.effectiveTenths, ctx.fahrenheit)
    // A short decrypt flourish when the device confirms it has reached the target.
    var scramble by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(ctx.reached && ctx.heating) {
        if (ctx.reached && ctx.heating && !reduced) {
            val digits = shown.toString()
            repeat(10) { i ->
                scramble = digits.mapIndexed { idx, c -> if (idx < i * digits.length / 10) c else "0123456789ABCDEF"[Random.nextInt(16)] }.joinToString("")
                delay(55)
            }
        }
        scramble = null
    }
    val pulse = if (ctx.heating && !ctx.reached && !reduced) rememberInfiniteTransition(label = "warm")
        .animateFloat(.55f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a").value else 1f
    val stateText = when {
        !ctx.connected -> "[ NO CARRIER ]"
        !ctx.heating -> "[ IDLE ]"
        ctx.reached -> "[ ACCESS GRANTED ]"
        else -> "[ WARMING ${spinner()} ]"
    }
    val stateColor = when { !ctx.connected -> p.dim; !ctx.heating -> p.dim; ctx.reached -> Night.Ready; else -> Night.Heat }
    Column(Modifier.fillMaxWidth().border(1.dp, p.dim.copy(alpha = .6f)).background(Color.Black.copy(alpha = .55f))
        .padding(horizontal = 10.dp, vertical = 6.dp).semantics(mergeDescendants = true) {
            contentDescription = if (ctx.connected) "Target $shown degrees $unit. " + when {
                !ctx.heating -> "Heater off."; ctx.reached -> "Target reached."; else -> "Heating." } else "Not connected."
        }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val boosted = ctx.connected && ctx.heating && ctx.mode != TermMode.Normal
            Term("tgt" + (if (boosted) "/${ctx.mode.name.lowercase()}" else ""), small, p.dim, Modifier.weight(1f))
            Term(ctx.deviceName.lowercase().take(16), small, p.dim)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Term(if (ctx.connected) scramble ?: shown.toString() else "---", (small.value * 4.2f).sp,
                (if (ctx.heating && !ctx.reached) Night.Heat else p.bright).copy(alpha = pulse))
            Term("°$unit", (small.value * 1.6f).sp, p.dim, Modifier.padding(bottom = 10.dp, start = 4.dp))
            Spacer(Modifier.weight(1f))
            ctx.liveTenths?.let { live ->
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 8.dp)) {
                    Term("live", small, p.dim)
                    Term("${Temperature.toDisplay(live, ctx.fahrenheit)}°", (small.value * 1.8f).sp, p.bright)
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Term(stateText, small, stateColor, Modifier.weight(1f))
            if (ctx.heating && !ctx.reached && eta != null && eta > 0) Term("eta ~${eta}s", small, p.dim)
        }
    }
}

@Composable private fun spinner(): String {
    if (LocalReducedMotion.current) return "..."
    var i by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(140); i = (i + 1) % 4 } }
    return "|/-\\"[i].toString()
}

/** An ASCII fader: drag or tap anywhere along it. Reports device tenths, snapped to display degrees. */
@Composable private fun TargetBar(ctx: TermContext, onDrag: (Int) -> Unit, onDone: () -> Unit, small: TextUnit) {
    val p = LocalPhosphor.current
    val lo = Temperature.toDisplay(ctx.minTenths, ctx.fahrenheit)
    val hi = Temperature.toDisplay(ctx.maxTenths, ctx.fahrenheit)
    val now = Temperature.toDisplay(ctx.targetTenths, ctx.fahrenheit).coerceIn(lo, hi)
    val unit = Temperature.unit(ctx.fahrenheit)
    var widthPx by remember { mutableFloatStateOf(1f) }
    fun at(x: Float): Int {
        val d = (lo + (x / widthPx).coerceIn(0f, 1f) * (hi - lo)).roundToInt()
        return Temperature.fromDisplay(d, ctx.fahrenheit).coerceIn(ctx.minTenths, ctx.maxTenths)
    }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .semantics {
            contentDescription = "Target temperature"
            stateDescription = "$now degrees $unit"
            progressBarRangeInfo = ProgressBarRangeInfo(now.toFloat(), lo.toFloat()..hi.toFloat(), hi - lo - 1)
            setProgress { v -> onDrag(Temperature.fromDisplay(v.roundToInt(), ctx.fahrenheit)); onDone(); true }
        }
        .pointerInput(ctx.minTenths, ctx.maxTenths, ctx.fahrenheit) {
            widthPx = size.width.toFloat()
            detectTapGestures { onDrag(at(it.x)); onDone() }
        }
        .pointerInput(ctx.minTenths, ctx.maxTenths, ctx.fahrenheit, "drag") {
            widthPx = size.width.toFloat()
            detectHorizontalDragGestures(onDragEnd = onDone, onDragCancel = onDone) { change, _ -> onDrag(at(change.position.x)) }
        }, contentAlignment = Alignment.CenterStart) {
        val cols = ((with(density) { maxWidth.toPx() }) / (small.value * density.density * density.fontScale * TERM_ADVANCE)).toInt().coerceAtLeast(12)
        val inner = cols - 2
        val pos = ((now - lo).toFloat() / (hi - lo).coerceAtLeast(1) * (inner - 1)).roundToInt().coerceIn(0, inner - 1)
        val bar = "[" + "#".repeat(pos) + "|" + "-".repeat(inner - 1 - pos) + "]"
        Column {
            Term(bar, small, if (ctx.heating) Night.Heat else p.bright)
            Row(Modifier.fillMaxWidth()) {
                Term("$lo°", small, p.dim, Modifier.weight(1f)); Term("$hi°", small, p.dim)
            }
        }
    }
}

@Composable private fun ModeTabs(ctx: TermContext, small: TextUnit, onMode: (TermMode) -> Unit) {
    val p = LocalPhosphor.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(TermMode.Normal to "norm", TermMode.Boost to "boost", TermMode.Super to "super").forEach { (mode, label) ->
            val selected = ctx.heating && ctx.mode == mode
            Box(Modifier.weight(1f).heightIn(min = 48.dp).border(1.dp, if (selected) p.bright else p.dim)
                .background(if (selected) p.bright else Color.Transparent)
                .semantics { this.selected = selected; role = Role.Tab; contentDescription = "$label mode" }
                .clickable { onMode(mode) }, contentAlignment = Alignment.Center) {
                Term(label, small, if (selected) Color.Black else p.bright, glow = !selected)
            }
        }
    }
}

@Composable private fun RunButton(ctx: TermContext, small: TextUnit, onClick: () -> Unit) {
    val p = LocalPhosphor.current
    val reduced = LocalReducedMotion.current
    val glow = if (ctx.heating && !reduced) rememberInfiniteTransition(label = "run")
        .animateFloat(.10f, .28f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "g").value else .14f
    val edge = if (ctx.heating) Night.Heat else p.bright
    Column(Modifier.fillMaxWidth().heightIn(min = 64.dp).border(2.dp, edge)
        .background(if (ctx.heating) Night.Heat.copy(alpha = glow) else p.bright.copy(alpha = .06f))
        .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = if (ctx.heating) "Turn heater off" else "Turn heater on" }
        .clickable(onClick = onClick).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Term(if (ctx.heating) "> KILL heat.exe" else "> RUN heat.exe", (small.value * 1.5f).sp, edge)
        Term(if (ctx.heating) "heater on // tap to stop" else "heater off // tap to start", small, p.dim, glow = false)
    }
}

@Composable private fun StatsLine(state: HomeState, small: TextUnit) {
    val ctx = state.ctx
    val p = LocalPhosphor.current
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.SpaceBetween) {
        ctx.battery?.let { b ->
            Term("bat ${Terminal.bar(b, 6)} $b%${if (ctx.charging) "+" else ""}", small, if (b <= 15) Night.Heat else p.bright)
        } ?: Term(if (ctx.hasPump) "pump ${if (ctx.pumpOn) "on" else "off"}" else "bat --", small, p.dim)
        Term(state.session?.let { "ses $it" } ?: "ses --:--", small, p.dim)
        Term("pkt ${ctx.puffs ?: 0}", small, p.dim)
    }
}

@Composable private fun Console(feed: List<TermLine>, small: TextUnit, modifier: Modifier) {
    val p = LocalPhosphor.current
    val scroll = rememberScrollState()
    LaunchedEffect(feed.size, feed.lastOrNull()) { scroll.scrollTo(scroll.maxValue) }
    Column(modifier.border(1.dp, p.dim.copy(alpha = .5f)).background(Color.Black.copy(alpha = .6f))
        .verticalScroll(scroll).padding(6.dp)) {
        if (feed.isEmpty()) Term("_", small, p.dim)
        // Lines wrap like a real terminal at its right margin; ASCII art is kept narrower than the pane.
        feed.forEach { Term(it.text, small, toneColor(it.tone, p), wrap = true) }
    }
}

@Composable private fun PromptLine(handle: String, input: String, onInput: (String) -> Unit, small: TextUnit, onSubmit: (String) -> Unit) {
    val p = LocalPhosphor.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).border(1.dp, p.dim).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Term(prompt(handle) + " ", small, Night.Ready)
        BasicTextField(input, onInput, Modifier.weight(1f).semantics { contentDescription = "Terminal command" },
            textStyle = termStyle(small, p.bright), cursorBrush = SolidColor(p.bright), singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (input.isNotBlank()) onSubmit(input) }),
            decorationBox = { inner -> Box { if (input.isEmpty()) Term("_", small, p.dim, glow = false); inner() } })
    }
}

@Composable private fun Macros(commands: List<String>, small: TextUnit, onCommand: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        commands.forEach { c -> TermKey(c, "Run $c", Modifier, small) { onCommand(c) } }
    }
}

@Composable internal fun TermKey(text: String, description: String, modifier: Modifier, size: TextUnit, enabled: Boolean = true,
    onClick: () -> Unit) {
    val p = LocalPhosphor.current
    Box(modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).border(1.dp, if (enabled) p.dim else p.dim.copy(alpha = .3f))
        .background(p.bright.copy(alpha = .05f))
        .semantics { contentDescription = description; role = Role.Button }
        .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
        Term(text, size, if (enabled) p.bright else p.dim.copy(alpha = .5f))
    }
}

/** A drawn moon (the bundled face has no moon glyphs). Lit side on the right while waxing. */
@Composable internal fun MoonGlyph(fraction: Double, modifier: Modifier = Modifier, lit: Color = Color(0xFFE9E4C8),
    dark: Color = Color(0xFF23263A)) {
    Canvas(modifier.semantics { contentDescription = "Moon ${Moon.at(System.currentTimeMillis()).name}" }) {
        val r = size.minDimension / 2
        drawCircle(dark, r)
        val waxing = fraction < .5
        drawArc(lit, if (waxing) -90f else 90f, 180f, true, Offset(center.x - r, center.y - r), Size(r * 2, r * 2))
        val k = kotlin.math.cos(2 * Math.PI * fraction).toFloat()
        val w = r * kotlin.math.abs(k)
        drawOval(if (k < 0) lit else dark, Offset(center.x - w, center.y - r), Size(w * 2, r * 2))
    }
}
