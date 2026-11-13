package io.github.orth4x0r.venty.ui.subventy

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.orth4x0r.venty.VentyApp
import io.github.orth4x0r.venty.ble.*
import io.github.orth4x0r.venty.session.RampPlan
import io.github.orth4x0r.venty.ui.settings.AppPrefs
import io.github.orth4x0r.venty.update.AppUpdater
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable internal fun RampEditor(app: VentyApp, controller: VentyController, enabled: Boolean,
    onClose: () -> Unit, onNotice: (String) -> Unit) {
    val kind by controller.kind.collectAsStateWithLifecycle()
    val saved by app.prefs.ramp.collectAsStateWithLifecycle()
    val profiles by app.prefs.rampProfiles.collectAsStateWithLifecycle()
    val run by controller.ramp.collectAsStateWithLifecycle()
    val initial = saved.fitTo(kind.minTarget / 10, kind.maxTarget / 10)
    var start by rememberSaveable(kind) { mutableStateOf(initial.startC.toString()) }
    var peak by rememberSaveable(kind) { mutableStateOf(initial.peakC.toString()) }
    var step by rememberSaveable(kind) { mutableStateOf(initial.stepC.toString()) }
    var seconds by rememberSaveable(kind) { mutableStateOf((initial.stepEveryMs / 1000).toString()) }
    var hold by rememberSaveable(kind) { mutableStateOf((initial.holdMs / 60000).toString()) }
    val plan = parseRampDraft(start, peak, step, seconds, hold, kind.minTarget / 10..kind.maxTarget / 10)
    UtilityWindow("temperature ramp", onClose) {
        S7Text("${kind.displayName} / ${kind.minTarget / 10}–${kind.maxTarget / 10} °C")
        S7Text("Temperatures here are Celsius. Loading or saving a plan does not start heating.", 11, S7.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            S7Input("start °C", start, { start = it }, Modifier.weight(1f), numeric = true)
            S7Input("finish °C", peak, { peak = it }, Modifier.weight(1f), numeric = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            S7Input("step °C / 1–10", step, { step = it }, Modifier.weight(1f), numeric = true)
            S7Input("interval seconds / 30–1200", seconds, { seconds = it }, Modifier.weight(1f), numeric = true)
        }
        S7Input("hold minutes / 0–15", hold, { hold = it }, numeric = true)
        if (plan == null) S7Text("Enter a valid range, step, interval and hold. Finish must be at least the start temperature.", 11)
        else S7Text("plan length: ${plan.totalMs / 60000}m ${(plan.totalMs / 1000) % 60}s / heater turns off at completion", 11)
        S7Button("save plan", { plan?.let(app.prefs::setRamp); onNotice("Ramp plan saved. Heater unchanged.") }, enabled = plan != null)
        if (run == null) S7Button("start ramp and heater", {
            plan?.let { app.prefs.setRamp(it); controller.startRamp(it); onNotice("Ramp started.") }
        }, enabled = enabled && plan != null)
        else {
            S7Text("running: ${run!!.phase.name.lowercase()} / ${run!!.targetC} °C", 11)
            S7Button("stop ramp", { controller.stopRamp(); onNotice("Ramp stopped. Heater setting is unchanged.") })
            S7Text("Stopping the ramp leaves the heater at its current setting.", 11, S7.Muted)
        }
        S7Text("saved profiles", 11, S7.Link)
        profiles.forEach { profile -> S7Button("load ${profile.name}", {
            val p = profile.plan.fitTo(kind.minTarget / 10, kind.maxTarget / 10)
            start = p.startC.toString(); peak = p.peakC.toString(); step = p.stepC.toString()
            seconds = (p.stepEveryMs / 1000).toString(); hold = (p.holdMs / 60000).toString()
        }) }
    }
}

@Composable internal fun DeviceProperties(app: VentyApp, controller: VentyController, enabled: Boolean,
    onClose: () -> Unit) {
    val kind by controller.kind.collectAsStateWithLifecycle()
    val status by controller.status.collectAsStateWithLifecycle()
    val settings by controller.deviceSettings.collectAsStateWithLifecycle()
    val info by controller.info.collectAsStateWithLifecycle()
    var brightness by remember(settings?.brightness) { mutableFloatStateOf((settings?.brightness ?: 5).toFloat()) }
    UtilityWindow("device properties", onClose) {
        S7Text("${kind.displayName} / firmware ${info.firmware ?: "unknown"}")
        if (!enabled) S7Text("Connect a device to change its properties.", 11, S7.Muted)
        S7Check("display in Fahrenheit", status?.fahrenheit == true, enabled, controller::setFahrenheit)
        if (kind.hasVentyExtras) {
            S7Check("eco charge optimization", status?.ecoChargeOptimization == true, enabled, controller::setEcoCharge)
            S7Check("eco voltage limit", status?.ecoVoltageLimit == true, enabled, controller::setEcoVoltage)
            S7Check("boost visualization", status?.boostVisualization == true, enabled, controller::setBoostVisualization)
            S7Check("device vibration", settings?.vibration == true, enabled && settings != null, controller::setVibration)
            S7Check("disable boost timeout", settings?.boostTimeoutDisabled == true, enabled && settings != null, controller::setBoostTimeoutDisabled)
            S7Text("device brightness: ${brightness.roundToInt()} / 9", 11)
            Slider(brightness, { brightness = it }, valueRange = 1f..9f, steps = 7,
                enabled = enabled && settings != null, onValueChangeFinished = { controller.setBrightness(brightness.roundToInt()) })
            status?.let { s ->
                OffsetEditor("boost offset (${if(s.fahrenheit) "°F" else "°C"})", s.boostOffset, enabled, controller::setBoostOffset)
                OffsetEditor("superboost additional offset", s.superBoostOffset, enabled, controller::setSuperBoostOffset)
            }
        }
        S7Text("Changes are sent through the existing device controller.", 11, S7.Muted)
    }
}

@Composable private fun OffsetEditor(title: String, value: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    S7Text("$title: $value", 11)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        S7Button("−", { onChange(value - 1) }, Modifier.weight(1f), enabled && value > VentyProtocol.MIN_OFFSET)
        S7Button("+", { onChange(value + 1) }, Modifier.weight(1f), enabled && value < VentyProtocol.MAX_OFFSET)
    }
}

@Composable internal fun LocateDevice(controller: VentyController, enabled: Boolean, onClose: () -> Unit) {
    var rssi by remember { mutableStateOf<Int?>(null) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(controller, enabled, owner) {
        rssi = null
        if (enabled) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { rssi = controller.readRssi(); delay(2000) }
        }
    }
    UtilityWindow("find my device", onClose) {
        SilverSeven(Modifier.fillMaxWidth().height(105.dp))
        S7Text(if (enabled) "Bluetooth signal: ${rssi?.let { "$it dBm" } ?: "waiting for a reading"}" else "Connect a Venty to locate it.")
        S7Text("Signal strength is approximate. Use the device's vibration/flash to help find it nearby.", 11, S7.Muted)
        S7Button("vibrate / flash device", controller::findMyDevice, enabled = enabled)
    }
}

@Composable internal fun ClientOptions(app: VentyApp, onExport: () -> Unit, onClose: () -> Unit) {
    val prefs by app.prefs.app.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val update by AppUpdater.state.collectAsStateWithLifecycle()
    UtilityWindow("client options", onClose) {
        S7Check("keep screen awake while heating", prefs.keepScreenOn) { app.prefs.setApp(prefs.copy(keepScreenOn = it)) }
        S7Check("device reached notification", prefs.readyNotification) { app.prefs.setApp(prefs.copy(readyNotification = it)) }
        S7Check("check this fork for updates", prefs.checkUpdates) { app.prefs.setApp(prefs.copy(checkUpdates = it)) }
        S7Button("idle disconnect: ${if(prefs.idleDisconnectMinutes == 0) "never" else "${prefs.idleDisconnectMinutes} min"}", {
            val choices = AppPrefs.IDLE_DISCONNECT_STEPS
            app.prefs.setApp(prefs.copy(idleDisconnectMinutes = choices[(choices.indexOf(prefs.idleDisconnectMinutes) + 1) % choices.size]))
        })
        S7Text("Tap idle disconnect to cycle the available intervals.", 11, S7.Muted)
        S7Button("export collection archive…", onExport)
        S7Text("Export includes all device archives, demo entries and notes. Android will ask where to save the JSON file.", 11, S7.Muted)
        S7Button("check for update", { AppUpdater.check(context) }, enabled = update !is AppUpdater.State.Checking)
        S7Text(when (val u = update) {
            is AppUpdater.State.Checking -> "checking this fork…"
            is AppUpdater.State.UpToDate -> "no newer release found."
            is AppUpdater.State.Available -> "an update is available in the release feed."
            is AppUpdater.State.Failed -> u.message
            else -> "Native preview builds are installed manually."
        }, 11, S7.Muted)
        S7Text("version ${AppUpdater.currentVersion(context)} / sound off / no scheduled reminders", 11, S7.Muted)
    }
}
