package io.github.orth4x0r.venty.ui.subventy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.orth4x0r.venty.VentyApp
import io.github.orth4x0r.venty.ble.HeaterMode
import io.github.orth4x0r.venty.ble.DeviceKind
import io.github.orth4x0r.venty.ble.VentyController
import io.github.orth4x0r.venty.prefs.PrefsStore
import io.github.orth4x0r.venty.session.RampPhase
import io.github.orth4x0r.venty.session.RampPlan
import io.github.orth4x0r.venty.session.RampProfile
import io.github.orth4x0r.venty.ui.Temperature
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

@Composable
internal fun InstrumentRamp(
    app: VentyApp,
    controller: VentyController,
    kind: DeviceKind,
    fahrenheit: Boolean,
    enabled: Boolean,
    notify: (String, (() -> Unit)?) -> Unit,
) {
    val stored by app.prefs.ramp.collectAsStateWithLifecycle()
    val run by controller.ramp.collectAsStateWithLifecycle()
    val profiles by app.prefs.rampProfiles.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var simple by rememberSaveable { mutableStateOf(true) }
    val min = kind.minTarget / 10
    val max = kind.maxTarget / 10
    val plan = stored.fitTo(min, max)
    val active = run

    fun save(plan: RampPlan) = app.prefs.setRamp(plan.fitTo(min, max))
    fun display(celsius: Int) = "${Temperature.toDisplay(celsius * 10, fahrenheit)}°${Temperature.unit(fahrenheit)}"
    fun saveProfile() {
        val next = generateSequence(1) { it + 1 }.first { n -> profiles.none { it.name == "My ramp $n" } }
        val profile = RampProfile("My ramp $next", plan)
        app.prefs.addRampProfile(profile)
        notify("Saved ${profile.name}", null)
    }

    if (!expanded && active == null) {
        DeckKey("Ramp session  ▾", { expanded = true }, Modifier.fillMaxWidth())
        return
    }

    DeckPanel {
        if (active == null) {
            DeckKey("Ramp session  ▴", { expanded = false }, Modifier.fillMaxWidth())
            DeckLCD {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DeckLabel("${display(plan.startC)} → ${display(plan.peakC)}", 15)
                    DeckLabel(deckDuration(plan.totalMs), 15)
                }
                ScheduleTrace(plan, 0, Modifier.fillMaxWidth().height(53.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DeckKey(if (editing) "Done editing" else "Edit ramp", { editing = !editing }, Modifier.weight(1f))
                DeckKey("Start ramp", { controller.startRamp(plan) }, Modifier.weight(1f),
                    enabled = enabled && plan.totalMs > 0, finish = KeyFinish.Rust)
            }
            if (editing) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    profiles.forEachIndexed { index, profile ->
                        DeckKey(profile.name, { save(profile.plan) }, active = plan == profile.plan, onLongClick = {
                            app.prefs.removeRampProfile(profile)
                            notify("Removed ${profile.name}", { app.prefs.insertRampProfile(index, profile) })
                        })
                    }
                    if (profiles.size < PrefsStore.MAX_RAMP_PROFILES && profiles.none { it.plan == plan }) {
                        DeckKey("+ Save", ::saveProfile)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DeckKey("Simple", { simple = true }, Modifier.weight(1f), active = simple)
                    DeckKey("Steps", { simple = false }, Modifier.weight(1f), active = !simple)
                }
                RampFields(plan, simple, min, max, fahrenheit, ::save)
            }
        } else {
            DeckLabel("Ramp session", 16)
            DeckLCD {
                val stage = when (active.phase) {
                    RampPhase.Ramping -> "RAMPING"
                    RampPhase.Holding -> "HOLDING"
                    RampPhase.Done -> "DONE"
                }
                DeckLabel(stage, 12)
                LcdNumber(Temperature.toDisplay(active.targetC * 10, fahrenheit), Temperature.unit(fahrenheit))
                val remaining = deckClock(active.plan.nextChangeInMs(active.elapsedMs))
                if (active.phase == RampPhase.Ramping) {
                    val step = minOf(active.plan.stepC, active.plan.peakC - active.targetC)
                    DeckLabel("Next +${temperatureDelta(step, fahrenheit)}° in $remaining")
                } else if (active.phase == RampPhase.Holding) DeckLabel("Heater off in $remaining")
                ScheduleTrace(active.plan, active.elapsedMs, Modifier.fillMaxWidth().height(66.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DeckKey("Stop ramp", {
                    controller.stopRamp()
                    notify("Ramp stopped. Heater setting kept.", null)
                }, Modifier.weight(1f), finish = KeyFinish.Graphite)
                DeckKey("Turn heater off", {
                    controller.stopRamp()
                    controller.setHeaterMode(HeaterMode.Off)
                }, Modifier.weight(1f), enabled = enabled, finish = KeyFinish.Rust)
            }
        }
    }
}

@Composable
private fun RampFields(plan: RampPlan, simple: Boolean, min: Int, max: Int, fahrenheit: Boolean, save: (RampPlan) -> Unit) {
    fun display(celsius: Int) = "${Temperature.toDisplay(celsius * 10, fahrenheit)}°${Temperature.unit(fahrenheit)}"
    val bowl = ((plan.totalMs + 15_000) / 30_000 * 30_000)
    val plateau = ((plan.holdMs + 15_000) / 30_000 * 30_000)
    fun changeSimple(start: Int = plan.startC, finish: Int = plan.peakC, total: Long = bowl, hold: Long = plateau) {
        val low = start.coerceIn(min, max)
        val high = finish.coerceIn(low, max)
        val length = total.coerceIn(RampPlan.MIN_SESSION_MS, RampPlan.MAX_SESSION_MS)
        save(RampPlan.span(low, high, length, hold.coerceIn(0, minOf(RampPlan.MAX_HOLD_MS, length - 60_000))))
    }

    DeckStepper("Start at", display(plan.startC)) { delta ->
        val newStart = plan.startC + delta
        if (simple) changeSimple(start = newStart, finish = maxOf(newStart, plan.peakC))
        else save(plan.copy(startC = newStart, peakC = maxOf(newStart, plan.peakC)))
    }
    if (simple) {
        DeckStepper("Finish at", display(plan.peakC)) { changeSimple(finish = plan.peakC + it) }
        DeckStepper("Bowl length", deckDuration(bowl)) { changeSimple(total = bowl + it * 30_000L) }
        DeckStepper("Plateau", if (plateau == 0L) "Off" else deckDuration(plateau)) {
            changeSimple(hold = plateau + it * 30_000L)
        }
    } else {
        DeckStepper("Climb", "+${temperatureDelta(plan.stepC, fahrenheit)}°") { save(plan.copy(stepC = plan.stepC + it)) }
        DeckStepper("Every", deckDuration(plan.stepEveryMs)) { save(plan.copy(stepEveryMs = plan.stepEveryMs + it * 30_000L)) }
        DeckStepper("Up to", display(plan.peakC)) { save(plan.copy(peakC = plan.peakC + it)) }
        DeckStepper("Then hold", if (plan.holdMs == 0L) "Off" else deckDuration(plan.holdMs)) {
            save(plan.copy(holdMs = plan.holdMs + it * 60_000L))
        }
    }
}

@Composable
internal fun DeckStepper(
    label: String,
    value: String,
    enabled: Boolean = true,
    minReached: Boolean = false,
    maxReached: Boolean = false,
    adjust: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DeckLabel(label, modifier = Modifier.weight(1f))
        DeckKey("−", { adjust(-1) }, Modifier.width(48.dp), enabled && !minReached, description = "Lower $label", repeatWhileHeld = true)
        DeckLabel(value, modifier = Modifier.widthIn(min = 62.dp))
        DeckKey("+", { adjust(1) }, Modifier.width(48.dp), enabled && !maxReached, description = "Raise $label", repeatWhileHeld = true)
    }
}

@Composable
private fun ScheduleTrace(plan: RampPlan, elapsed: Long, modifier: Modifier) {
    Canvas(modifier.semantics { contentDescription = "Programmed ramp schedule" }) {
        val duration = plan.totalMs.coerceAtLeast(1L)
        val span = (plan.peakC - plan.startC).coerceAtLeast(1)
        fun x(time: Long) = (time.toFloat() / duration).coerceIn(0f, 1f) * size.width
        fun y(target: Int) = size.height * (.83f - (target - plan.startC).toFloat() / span * .64f)
        for (i in 0..5) {
            val yy = size.height * i / 5
            drawLine(Deck.Ink.copy(alpha = .18f), Offset(0f, yy), Offset(size.width, yy), 1.dp.toPx())
        }
        val trace = Path().apply {
            moveTo(0f, y(plan.startC))
            for (i in 1..plan.steps) {
                val t = i * plan.stepEveryMs
                lineTo(x(t), y(plan.targetAt(t - 1)))
                lineTo(x(t), y(plan.targetAt(t)))
            }
            lineTo(size.width, y(plan.peakC))
        }
        drawPath(trace, Deck.Ink, style = Stroke(2.dp.toPx()))
        if (elapsed > 0L) drawCircle(Deck.Rust, 4.dp.toPx(), Offset(x(elapsed), y(plan.targetAt(elapsed))))
    }
}

private fun temperatureDelta(celsius: Int, fahrenheit: Boolean) =
    if (fahrenheit) (celsius * 1.8).roundToInt() else celsius

internal fun deckClock(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

private fun deckDuration(ms: Long): String =
    if (ms % 60_000L == 0L) "${ms / 60_000L} min" else deckClock(ms)
