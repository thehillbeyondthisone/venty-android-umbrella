package io.github.orth4x0r.venty.ui.subventy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.orth4x0r.venty.VentyApp
import io.github.orth4x0r.venty.ble.*
import io.github.orth4x0r.venty.prefs.BatterySample
import io.github.orth4x0r.venty.prefs.Preset
import io.github.orth4x0r.venty.prefs.PrefsStore
import io.github.orth4x0r.venty.session.DrawTray
import io.github.orth4x0r.venty.session.LiveSession
import io.github.orth4x0r.venty.session.estimateBatteryForecast
import io.github.orth4x0r.venty.ui.Temperature
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
internal fun InstrumentDashboard(
    app: VentyApp,
    controller: VentyController,
    status: VentyStatus,
    kind: DeviceKind,
    enabled: Boolean,
    demo: Boolean,
    draws: DrawTray,
    trackingPaused: Boolean,
    onDraws: () -> Unit,
    notify: (String, (() -> Unit)?) -> Unit,
) {
    val info by controller.info.collectAsStateWithLifecycle()
    val pump by controller.pump.collectAsStateWithLifecycle()
    val presets by app.prefs.presets.collectAsStateWithLifecycle()
    val live by app.sessions.live.collectAsStateWithLifecycle()
    val lifetimePuffs by app.sessions.lifetimePuffs.collectAsStateWithLifecycle()
    val heaterOn = status.heaterMode.isHeating
    val fahrenheit = status.fahrenheit
    val unit = Temperature.unit(fahrenheit)
    val minimum = Temperature.toDisplay(kind.minTarget, fahrenheit)
    val maximum = Temperature.toDisplay(kind.maxTarget, fahrenheit)
    val target = Temperature.toDisplay(status.targetTenths, fahrenheit)
    val effectiveTarget = Temperature.toDisplay(status.effectiveTargetTenths, fahrenheit)
    val current = status.currentTenths?.takeIf { kind.liveTemperature }?.let { Temperature.toDisplay(it, fahrenheit) }
    var draft by remember(kind, fahrenheit, enabled) { mutableStateOf<Int?>(null) }
    val adjustedTarget = draft ?: target
    fun changeTarget(value: Int) {
        controller.setTarget(Temperature.fromDisplay(value.coerceIn(minimum, maximum), fahrenheit))
    }

    Column(Modifier.fillMaxWidth().background(Deck.Graphite).padding(12.dp)) {
        DeckLCD {
            val dragging = draft != null
            DeckLabel(if (dragging) "SET TARGET" else if (current != null) "NOW" else "TARGET", 11)
            LcdNumber(if (dragging) adjustedTarget else current ?: effectiveTarget, unit, Modifier.align(Alignment.CenterHorizontally))
            val indicator = when {
                !enabled -> "RECONNECTING"
                !heaterOn -> "HEATER OFF"
                status.setpointReached -> "READY"
                else -> "HEATING"
            }
            DeckLabel(indicator, 15, modifier = Modifier.align(Alignment.CenterHorizontally))
            if (!dragging && current != null) DeckLabel("Target $effectiveTarget°$unit", modifier = Modifier.align(Alignment.CenterHorizontally))
            else if (!dragging && target != effectiveTarget) DeckLabel("Base $target°$unit", modifier = Modifier.align(Alignment.CenterHorizontally))
            if (enabled && heaterOn && status.setpointReached && status.autoOffSeconds > 0 && live?.puffStartMs == null) {
                DeckLabel("Auto-off ${deckClock(status.autoOffSeconds * 1000L)}", 12,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
    DeckStripe()
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DeckLabel("$minimum°", 11)
            Slider(
                value = adjustedTarget.coerceIn(minimum, maximum).toFloat(),
                onValueChange = { draft = it.roundToInt() },
                onValueChangeFinished = {
                    if (enabled) draft?.let(::changeTarget)
                    draft = null
                },
                enabled = enabled,
                valueRange = minimum.toFloat()..maximum.toFloat(),
                colors = SliderDefaults.colors(thumbColor = Deck.Rust, activeTrackColor = Deck.Teal,
                    inactiveTrackColor = Deck.Border.copy(alpha = .55f)),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics {
                    contentDescription = "Target temperature"
                    stateDescription = "$adjustedTarget°$unit"
                },
            )
            DeckLabel("$maximum°", 11)
        }

        live?.let { SessionReadout(it, kind.hasVentyExtras) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            DeckKey("−", { changeTarget(target - 1) }, Modifier.width(50.dp), enabled && target > minimum,
                description = "Lower target", repeatWhileHeld = true)
            DeckKey("+", { changeTarget(target + 1) }, Modifier.width(50.dp), enabled && target < maximum,
                description = "Raise target", repeatWhileHeld = true)
            DeckKey(if (heaterOn) "Turn heater off" else "Turn heater on", {
                if (heaterOn) {
                    controller.stopRamp()
                    controller.setHeaterMode(HeaterMode.Off)
                } else controller.setHeaterMode(HeaterMode.On)
            }, Modifier.weight(1f), enabled, KeyFinish.Rust)
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            presets.forEachIndexed { index, preset ->
                val temperature = Temperature.toDisplay(preset.tenthsC, fahrenheit)
                DeckKey("${preset.name}\n$temperature°", { controller.setTarget(preset.tenthsC) },
                    Modifier.widthIn(min = 75.dp), enabled, active = status.targetTenths == preset.tenthsC,
                    onLongClick = {
                        app.prefs.removePreset(preset)
                        notify("Removed ${preset.name}", { app.prefs.insertPreset(index, preset) })
                    })
            }
            if (presets.size < PrefsStore.MAX_PRESETS && presets.none { it.tenthsC == status.targetTenths }) {
                DeckKey("+", {
                    val number = generateSequence(1) { it + 1 }.first { n -> presets.none { it.name == "Custom $n" } }
                    app.prefs.addPreset(Preset("Custom $number", status.targetTenths))
                }, Modifier.width(48.dp), enabled, description = "Save current target as preset")
            }
        }

        if (kind.hasBoost) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Normal" to HeaterMode.On, "Boost" to HeaterMode.Boost, "Superboost" to HeaterMode.SuperBoost)
                    .forEach { (label, mode) ->
                        DeckKey(label, {
                            if (status.heaterMode != mode) controller.setHeaterMode(mode)
                        }, Modifier.weight(1f), enabled && heaterOn, active = status.heaterMode == mode)
                    }
            }
        }
        if (kind.hasPump) {
            DeckKey(if (pump) "Stop pump" else "Start pump", { controller.setPump(!pump) },
                Modifier.fillMaxWidth(), enabled, KeyFinish.Teal)
        }

        if (kind.hasVentyExtras) {
            DrawSessionEntry(draws, trackingPaused, onDraws)
        }

        InstrumentRamp(app, controller, kind, fahrenheit, enabled, notify)
        if (kind.hasBattery) InstrumentBattery(app, status, live, demo)
        DeckPanel {
            DeckLabel("Device", 16)
            if (kind.hasBoost) {
                val offsetRange = VentyProtocol.MIN_OFFSET..VentyProtocol.MAX_OFFSET
                DeckStepper("Boost offset", "+${status.boostOffset}°$unit", enabled,
                    status.boostOffset <= offsetRange.first, status.boostOffset >= offsetRange.last) {
                    controller.setBoostOffset(status.boostOffset + it)
                }
                DeckStepper("Superboost offset", "+${status.superBoostOffset}°$unit", enabled,
                    status.superBoostOffset <= offsetRange.first, status.superBoostOffset >= offsetRange.last) {
                    controller.setSuperBoostOffset(status.superBoostOffset + it)
                }
                DeckLabel("Superboost adds its offset on top of Boost.", 11, Deck.Muted)
            }
            if (kind.hasBattery) DeckFact("Lifetime puffs", lifetimePuffs.toString())
            else DeckFact("Target range", "$minimum–$maximum°$unit")
            DeckFact("Heater time", info.heaterMinutes?.let { "${it / 60} h ${it % 60} min" } ?: "—")
            DeckFact("Firmware", info.firmware ?: "—")
        }
    }
}

@Composable
private fun SessionReadout(live: LiveSession, showPuffs: Boolean) {
    var now by remember(live.startMs) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(live.startMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    DeckPanel {
        val elapsed = deckClock(now - live.startMs)
        val puffs = if (!showPuffs) "" else if (live.puffs == 1) "  ·  1 puff" else "  ·  ${live.puffs} puffs"
        val eta = live.etaSeconds?.let { "  ·  ~$it s to temp" }.orEmpty()
        DeckLabel("$elapsed elapsed$puffs$eta", 12)
    }
}

@Composable
private fun InstrumentBattery(app: VentyApp, status: VentyStatus, live: LiveSession?, demo: Boolean) {
    val samples by app.battery.samples.collectAsStateWithLifecycle()
    val history by app.sessions.history.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()
    val hours24 = 86_400_000L
    val day = if (demo) emptyList() else remember(samples) { samples.filter { it.atMs >= now - hours24 } }
    val forecast = if (demo) null else remember(samples, history, status.battery, status.charging, live?.startMs) {
        estimateBatteryForecast(status.battery, status.charging, live?.startMs, history, samples, now)
    }
    DeckPanel {
        DeckFact("Battery", "${status.battery}%${if (status.charging) " · Charging" else ""}")
        if (day.size > 1) {
            DeckLabel("24 h battery history", 11, Deck.Muted)
            BatteryTrace(day, Modifier.fillMaxWidth().height(45.dp))
        }
        val line = when {
            demo -> "Demo battery level"
            status.charging -> null
            forecast?.heatingMinutesLeft != null -> "About ${forecast.heatingMinutesLeft} min of heating left"
            forecast?.sessionsLeft != null -> "About ${forecast.sessionsLeft} sessions left"
            else -> "Learning your drain rate…"
        }
        if (line != null) DeckLabel(line, 11, Deck.Muted)
    }
}

@Composable
private fun BatteryTrace(samples: List<BatterySample>, modifier: Modifier) {
    Canvas(modifier.border(1.dp, Deck.Border).padding(3.dp).semantics { contentDescription = "Battery history" }) {
        val start = samples.first().atMs
        val duration = (samples.last().atMs - start).coerceAtLeast(1).toFloat()
        fun point(sample: BatterySample) = Offset(
            (sample.atMs - start) / duration * size.width,
            (1 - sample.percent.coerceIn(0, 100) / 100f) * size.height,
        )
        samples.zipWithNext().forEach { (a, b) -> drawLine(Deck.Teal, point(a), point(b), 2.dp.toPx()) }
    }
}

@Composable
internal fun DeckFact(name: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DeckLabel(name, 12, Deck.Muted, Modifier.weight(1f))
        DeckLabel(value)
    }
}
