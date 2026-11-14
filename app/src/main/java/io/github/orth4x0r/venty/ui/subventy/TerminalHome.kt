package io.github.orth4x0r.venty.ui.subventy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.orth4x0r.venty.ui.Temperature
import kotlinx.coroutines.delay

/** Everything the terminal page draws. Built by the screen from controller state; pure for previews. */
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

/**
 * The terminal page, built only from the client's own parts: S7 groups, buttons and the dark input
 * well. Device controls sit on top; the session console and command line sit below.
 */
@Composable internal fun TerminalHome(state: HomeState, actions: HomeActions, input: String, onInput: (String) -> Unit,
    modifier: Modifier = Modifier) {
    val ctx = state.ctx
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        S7Group("remote device") {
            Readout(ctx, state.etaSeconds)
            if (ctx.connected) {
                val lo = ctx.minTenths.toFloat(); val hi = ctx.maxTenths.toFloat()
                val unit = Temperature.unit(ctx.fahrenheit)
                Slider(ctx.targetTenths.toFloat().coerceIn(lo, hi), { actions.onTargetDrag(it.toInt()) }, valueRange = lo..hi,
                    onValueChangeFinished = actions.onTargetDone,
                    modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = "Target temperature"
                        stateDescription = "${Temperature.toDisplay(ctx.targetTenths, ctx.fahrenheit)} degrees $unit"
                    })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    S7Button("−", { actions.onCommand("-") }, Modifier.weight(1f), ctx.targetTenths > ctx.minTenths)
                    S7Button("+", { actions.onCommand("+") }, Modifier.weight(1f), ctx.targetTenths < ctx.maxTenths)
                }
                if (ctx.hasBoost) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(TermMode.Normal to "normal", TermMode.Boost to "boost", TermMode.Super to "superboost").forEach { (mode, label) ->
                        ModeButton(label, ctx.heating && ctx.mode == mode, Modifier.weight(1f)) { actions.onCommand(mode.name.lowercase()) }
                    }
                }
                S7Button(if (ctx.heating) "turn heater off" else "turn heater on",
                    { actions.onCommand(if (ctx.heating) "off" else "heat") }, Modifier.fillMaxWidth())
                if (ctx.hasPump) S7Button(if (ctx.pumpOn) "stop pump" else "start pump", { actions.onCommand("pump") }, Modifier.fillMaxWidth())
            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                S7Button("connect", { actions.onCommand("connect") }, Modifier.weight(1f))
                S7Button("try demo", { actions.onCommand("demo") }, Modifier.weight(1f))
            }
            ctx.battery?.let { Info("battery", "$it%${if (ctx.charging) " / charging" else ""}") }
            Info("session", state.session ?: "—")
            Info("puffs", (ctx.puffs ?: 0).toString())
        }
        S7Group("session console") {
            ConsoleBox(state.feed)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                S7Input(prompt(ctx.handle), input, onInput, Modifier.weight(1f), onSubmit = { if (input.isNotBlank()) actions.onCommand(input) })
                S7Button("send", { if (input.isNotBlank()) actions.onCommand(input) }, enabled = input.isNotBlank())
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                macroCommands(ctx).forEach { c -> S7Button(c, { actions.onCommand(c) }) }
            }
        }
    }
}

/** Target, readiness and (Volcano only) live temperature. Never a simulated reading on a Venty. */
@Composable private fun Readout(ctx: TermContext, eta: Int?) {
    val unit = Temperature.unit(ctx.fahrenheit)
    val shown = Temperature.toDisplay(ctx.effectiveTenths, ctx.fahrenheit)
    val (label, lamp) = when {
        !ctx.connected -> "not connected" to Color(0xFF5D6170)
        !ctx.heating -> "heater off" to Color(0xFF5D6170)
        ctx.reached -> "target reached" to Console.Ok
        else -> "heating${dots()}" to Console.Warn
    }
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
        contentDescription = if (ctx.connected) "${ctx.deviceName}. Target $shown degrees $unit. $label." else "Not connected."
    }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            S7Text("${ctx.deviceName.lowercase()} / ${if (ctx.mode != TermMode.Normal && ctx.heating) "${ctx.mode.name.lowercase()} target" else "target"}", 10, S7.Muted)
            Text(if (ctx.connected) "$shown°$unit" else "—", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).background(lamp).border(1.dp, Color.Black))
                S7Text(label, 12, S7.Text, Modifier.padding(start = 6.dp))
                if (ctx.heating && !ctx.reached && eta != null && eta > 0) S7Text("  est. ${eta}s", 11, S7.Muted)
            }
        }
        ctx.liveTenths?.let { live ->
            Column(horizontalAlignment = Alignment.End) {
                S7Text("live", 10, S7.Muted)
                Text("${Temperature.toDisplay(live, ctx.fahrenheit)}°$unit", color = S7.Link, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable private fun dots(): String {
    if (LocalReducedMotion.current) return "…"
    var n by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(450); n = (n + 1) % 4 } }
    return ".".repeat(n)
}

@Composable private fun ModeButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.heightIn(min = 48.dp).border(1.dp, if (selected) Color.White else Color(0xFF858AAF))
        .background(if (selected) S7.Selection else S7.Panel)
        .semantics { this.selected = selected; role = Role.Tab; contentDescription = "$text mode" }
        .clickable(onClick = onClick).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
        S7Text(text, 12, if (selected) Color.White else S7.Text)
    }
}

@Composable private fun Info(label: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        S7Text(label, 11, S7.Muted, Modifier.weight(1f)); S7Text(value, 12)
    }
}

/** The session log as a client list box: the dark well, newest line at the bottom. */
@Composable private fun ConsoleBox(feed: List<TermLine>) {
    val scroll = rememberScrollState()
    LaunchedEffect(feed.size, feed.lastOrNull()) { scroll.scrollTo(scroll.maxValue) }
    Column(Modifier.fillMaxWidth().height(190.dp).border(1.dp, Console.BoxEdge).background(Console.Box)
        .verticalScroll(scroll).padding(7.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        feed.forEach { S7Text(it.text, 11, toneColor(it.tone)) }
    }
}

/** A drawn moon, used on the screensaver. Lit side on the right while waxing. */
@Composable internal fun MoonGlyph(fraction: Double, modifier: Modifier = Modifier, lit: Color = Color(0xFFD4D5D8),
    dark: Color = Color(0xFF202127)) {
    Canvas(modifier) {
        val r = size.minDimension / 2
        drawCircle(dark, r)
        val waxing = fraction < .5
        drawArc(lit, if (waxing) -90f else 90f, 180f, true, Offset(center.x - r, center.y - r), Size(r * 2, r * 2))
        val k = kotlin.math.cos(2 * Math.PI * fraction).toFloat()
        val w = r * kotlin.math.abs(k)
        drawOval(if (k < 0) lit else dark, Offset(center.x - w, center.y - r), Size(w * 2, r * 2))
    }
}
