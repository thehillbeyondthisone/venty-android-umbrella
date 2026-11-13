package io.github.orth4x0r.venty.ui.subventy

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.orth4x0r.venty.VentyApp
import io.github.orth4x0r.venty.ble.*
import io.github.orth4x0r.venty.collections.CollectionEntry
import io.github.orth4x0r.venty.ui.Temperature
import io.github.orth4x0r.venty.ui.dashboard.DashboardScreen
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val Caption = Color(0xFF8080FF)
private val Panel = Color(0xFF004080)
private val Cyan = Color(0xFF0080FF)
private val MenuBorder = Color(0xFF0080C0)
private val Muted = Color(0xFFC2D5EF)
private val pages = listOf("connection", "temperature", "session log", "collections", "field notes", "rituals", "local options")

/** Native Compose client. Every device write goes through the existing controller. */
@Composable
fun SubVentyScreen(controller: VentyController, permissions: Boolean, bluetoothOn: Boolean,
    onGrant: () -> Unit, onEnableBluetooth: () -> Unit) {
    val app = LocalContext.current.applicationContext as VentyApp
    val connection by controller.connection.collectAsStateWithLifecycle()
    val status by controller.status.collectAsStateWithLifecycle()
    val kind by controller.kind.collectAsStateWithLifecycle()
    val info by controller.info.collectAsStateWithLifecycle()
    val pump by controller.pump.collectAsStateWithLifecycle()
    val ramp by controller.ramp.collectAsStateWithLifecycle()
    val entries by app.collections.entries.collectAsStateWithLifecycle()
    val paused by app.collections.paused.collectAsStateWithLifecycle()
    val prefs by app.prefs.app.collectAsStateWithLifecycle()
    val presets by app.prefs.presets.collectAsStateWithLifecycle()
    val live by app.sessions.live.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var archivePage by rememberSaveable { mutableIntStateOf(0) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val connected = connection is ConnectionState.Connected
    val enabled = connected && status != null
    val demo = (connection as? ConnectionState.Connected)?.demo == true
    val name = when (val c = connection) {
        is ConnectionState.Connected -> c.name
        is ConnectionState.Connecting -> c.name
        is ConnectionState.Reconnecting -> c.name
        else -> controller.lastDeviceName ?: kind.displayName
    }
    val key = if (demo) "demo:${kind.name}" else controller.lastDeviceAddress ?: kind.name
    val archive = entries.filter { showAll || it.device == key }
    val lastPage = ((archive.size - 1).coerceAtLeast(0) / 20)
    val safePage = archivePage.coerceIn(0, lastPage)
    val visibleEntries = archive.drop(safePage * 20).take(20)
    val unit = Temperature.unit(status?.fahrenheit == true)
    val view = LocalView.current
    DisposableEffect(status?.heaterMode?.isHeating, prefs.keepScreenOn) {
        view.keepScreenOn = status?.heaterMode?.isHeating == true && prefs.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(controller) { controller.notices.collect { notice = it } }
    BackHandler(enabled = advanced || page != 0) { if (advanced) advanced = false else page = 0 }

    MaterialTheme(colorScheme = darkColorScheme(primary = Caption, onPrimary = Color.Black,
        background = Color.Black, surface = Panel, onSurface = Color.White,
        onBackground = Color.White, secondary = Cyan, outline = Caption)) {
        Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().padding(5.dp)
            .border(1.dp, Color.Gray)) {
            Row(Modifier.fillMaxWidth().background(Caption).heightIn(min = 48.dp).padding(start = 7.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Label("»", 18)
                Text("SubVenty version 2.1", color = Color.White, fontSize = 16.sp,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                FlatButton("?", { about = true }, Modifier.width(48.dp))
            }
            Row(Modifier.fillMaxWidth().background(Panel).padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("7", color = Color.LightGray, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                    Label(name, 12)
                    Label(if (demo) "demo / simulated device" else "Bluetooth LE", 10, Muted)
                }
                FlatButton(if (connected) "disconnect" else "connect", {
                    if (connected) {
                        controller.disconnect()
                    } else {
                        page = 0
                        if (permissions && bluetoothOn) controller.connectToLast()
                    }
                }, Modifier.widthIn(max = 120.dp))
            }
            if (advanced) {
                FlatButton("« return to SubVenty", { advanced = false }, Modifier.fillMaxWidth())
                Box(Modifier.weight(1f)) { DashboardScreen(controller, connection) }
            } else {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    // Large fonts and narrow phones use a top menu, preserving readable content.
                    val stacked = maxWidth < 350.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
                    Column(Modifier.fillMaxSize()) {
                        if (stacked) Row(Modifier.fillMaxWidth().horizontalScrollCompat()) {
                            pages.forEachIndexed { i, title -> MenuButton(title, page == i, { page = i }, Modifier.width(122.dp)) }
                        }
                        Row(Modifier.weight(1f).fillMaxWidth().padding(5.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            if (!stacked) Column(Modifier.width(112.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                pages.forEachIndexed { i, title -> MenuButton(title, page == i, { page = i }, Modifier.fillMaxWidth()) }
                            }
                            Column(Modifier.weight(1f).fillMaxHeight()) {
                                Column(Modifier.weight(1f).fillMaxWidth().background(Panel)
                                    .verticalScroll(rememberScrollState()).padding(9.dp),
                                    verticalArrangement = Arrangement.spacedBy(11.dp)) {
                                    Label("SubVenty version 2.1", 13)
                                    Label("personal thermal control", 11, Muted)
                                    when (page) {
                                        0 -> {
                                            Label(when (val c = connection) {
                                                is ConnectionState.Connected -> if (c.demo) "Demo device connected." else "Your device is connected."
                                                is ConnectionState.Connecting -> "Connecting to ${c.name}…"
                                                is ConnectionState.Reconnecting -> "Reconnecting / attempt ${c.attempt}"
                                                is ConnectionState.Scanning -> if (c.active) "Looking for nearby devices…" else "Choose a device below."
                                                is ConnectionState.Failed -> c.message
                                                else -> "Waiting for your device."
                                            })
                                            when {
                                                !permissions -> FlatButton("Allow Bluetooth access", onGrant)
                                                !bluetoothOn -> FlatButton("Turn on Bluetooth", onEnableBluetooth)
                                                !connected -> {
                                                    FlatButton("Scan for devices", controller::startScan)
                                                    (connection as? ConnectionState.Scanning)?.let { scan ->
                                                        if (scan.active) FlatButton("Stop scanning", controller::stopScan)
                                                        scan.found.forEach { found -> FlatButton("${found.name} / ${found.rssi} dBm", { controller.connect(found) }) }
                                                    }
                                                }
                                            }
                                            if (!connected && connection !is ConnectionState.Connecting && connection !is ConnectionState.Reconnecting) {
                                                FlatButton("Try demo Venty", controller::startDemo)
                                                FlatButton("Try demo Volcano", controller::startVolcanoDemo)
                                            }
                                            InfoLine("device", kind.displayName)
                                            if (kind.hasBattery) InfoLine("battery", if (enabled) "${status!!.battery}%${if(status!!.charging) " / charging" else ""}" else "—")
                                            InfoLine("firmware", info.firmware ?: "—")
                                            InfoLine("heater", heaterLabel(status, connected))
                                            if (kind.liveTemperature) InfoLine("live", status?.currentTenths?.let { "${Temperature.toDisplay(it, status?.fahrenheit == true)}°$unit" } ?: "—")
                                            else Label("Venty reports target and readiness, not live temperature.", 11, Muted)
                                            HeaterControl(status, enabled, controller)
                                        }
                                        1 -> {
                                            Label("target controls")
                                            val current = status?.targetTenths ?: 1850
                                            InfoLine("base target", "${Temperature.toDisplay(current, status?.fahrenheit == true)}°$unit")
                                            InfoLine("effective target", status?.let { "${Temperature.toDisplay(it.effectiveTargetTenths, it.fahrenheit)}°$unit" } ?: "—")
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                FlatButton("−", { controller.setTarget(Temperature.fromDisplay(Temperature.toDisplay(current, status?.fahrenheit == true) - 1, status?.fahrenheit == true)) }, Modifier.weight(1f), enabled && current > kind.minTarget)
                                                FlatButton("+", { controller.setTarget(Temperature.fromDisplay(Temperature.toDisplay(current, status?.fahrenheit == true) + 1, status?.fahrenheit == true)) }, Modifier.weight(1f), enabled && current < kind.maxTarget)
                                            }
                                            Slider(value = current.toFloat().coerceIn(kind.minTarget.toFloat(), kind.maxTarget.toFloat()),
                                                onValueChange = { controller.setTarget(it.toInt()) }, valueRange = kind.minTarget.toFloat()..kind.maxTarget.toFloat(), enabled = enabled,
                                                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Target temperature"; stateDescription = "${Temperature.toDisplay(current, status?.fahrenheit == true)} degrees $unit" }, onValueChangeFinished = { notice = "Target sent to device." })
                                            HeaterControl(status, enabled, controller)
                                            if (kind.hasBoost) {
                                                listOf("normal" to HeaterMode.On, "boost" to HeaterMode.Boost, "superboost" to HeaterMode.SuperBoost).forEach { (title, mode) ->
                                                    FlatButton("$title / ${status?.let { Temperature.toDisplay(it.targetFor(mode), it.fahrenheit) } ?: "—"}°$unit", { controller.setHeaterMode(mode) }, enabled = enabled)
                                                }
                                                Label("Boost offsets, ramp editor and device settings are available in all controls below.", 11, Muted)
                                            }
                                            if (kind.hasPump) FlatButton(if (pump) "stop pump" else "start pump", { controller.setPump(!pump) }, enabled = enabled)
                                            if (ramp != null) FlatButton("Stop active ramp", { controller.stopRamp() }, enabled = enabled)
                                            presets.forEach { preset -> FlatButton("${preset.name} / ${Temperature.toDisplay(preset.tenthsC, status?.fahrenheit == true)}°$unit", { controller.setTarget(preset.tenthsC) }, enabled = enabled) }
                                            FlatButton("all controls / ramps / settings", { advanced = true }, enabled = connection is ConnectionState.Connected || connection is ConnectionState.Reconnecting)
                                        }
                                        2 -> {
                                            Label("local session archive")
                                            ArchiveScope(showAll, { showAll = !showAll }, demo)
                                            if (archive.isEmpty()) Label("Your archive is empty. Completed sessions will appear here.", 12, Muted)
                                            visibleEntries.forEach { e ->
                                                Label(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(e.startMs), 11)
                                                Label("${(e.endMs - e.startMs) / 60000} min / ${if(e.device.startsWith("demo:")) "demo" else "device"}", 11, Muted)
                                            }
                                            if (live != null) Label("Session in progress. It will be archived after the heater is off.", 11, Muted)
                                        }
                                        3 -> {
                                            ArchiveScope(showAll, { showAll = !showAll }, demo)
                                            val bands = archive.flatMap { it.bands }.distinct().sorted()
                                            Label("temperature atlas")
                                            if (bands.isEmpty()) Label("No reached temperature bands yet. A band is recorded only after the device reports reached.", 12, Muted)
                                            bands.chunked(3).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { row.forEach { band ->
                                                Box(Modifier.background(Caption).padding(6.dp)) { Label("${Temperature.toDisplay(band * 10, status?.fahrenheit == true)}°$unit", 11, Color.Black) }
                                            } } }
                                            Label("A map with no finish line. Bands never expire.", 11, Muted)
                                            Label("ember trail")
                                            EmberTrail(archive.size)
                                            Label("${archive.size} beads / one per recorded session", 11, Muted)
                                            Label("The permanent archive is independent of recent history.", 11, Muted)
                                        }
                                        4 -> {
                                            ArchiveScope(showAll, { showAll = !showAll }, demo)
                                            Label("field notes / personal journal")
                                            if (archive.isEmpty()) Label("Notes attach to completed sessions. Nothing to write until you have an entry.", 12, Muted)
                                            visibleEntries.forEach { entry -> NoteEditor(entry, app.collections::saveNote) }
                                        }
                                        5 -> WeeklyPostcard(archive)
                                        6 -> {
                                            Label("local options")
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(checked = paused, onCheckedChange = { value ->
                                                    app.sessions.setTrackingPaused(value)
                                                    if (!value) controller.reportedStatus.value?.takeIf { !it.heaterMode.isHeating }
                                                        ?.let { app.sessions.onStatus(it, device = key) }
                                                    app.collections.setPaused(value)
                                                })
                                                Label("do not track", 12)
                                            }
                                            Label(if (paused) "Collection paused. Your archive stays. Resume will wait until the heater is off; no backfill." else "Pause new automatic session and battery logs. Existing stamps, beads and notes stay.", 11, Muted)
                                            InfoLine("sound", "off")
                                            InfoLine("reminders", "none")
                                            InfoLine("storage", "local")
                                            FlatButton("all device & app settings", { advanced = true }, enabled = connected || connection is ConnectionState.Reconnecting)
                                            Label("SubVenty 0.4.0 / first native preview", 11, Muted)
                                        }
                                    }
                                    if ((page == 2 || page == 4) && archive.size > 20) {
                                        Label("page ${safePage + 1} / ${lastPage + 1}", 11, Muted)
                                        FlatButton("newer entries", { archivePage = safePage - 1 }, enabled = safePage > 0)
                                        FlatButton("older entries", { archivePage = safePage + 1 }, enabled = safePage < lastPage)
                                    }
                                }
                                FlatButton(if (page == 0) "temperature controls »" else "« device information", { page = if (page == 0) 1 else 0 }, Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.border(1.dp, Color.Gray).padding(4.dp)) { Label(if (demo) "demo" else if (connected) "online" else "offline", 10) }
                Box(Modifier.weight(1f).border(1.dp, Color.Gray).padding(4.dp)) { Label(notice.ifBlank { heaterLabel(status, connected) }, 10) }
            }
        }
        if (about) AlertDialog(onDismissRequest = { about = false }, title = { Label("about SubVenty", 17) },
            text = { Label("Personal thermal control.\n\nA native Venty companion with the original SubSeven 2.1 visual language.\n\nDevice actions use Bluetooth. Demo mode uses simulated devices. Collections remain on this phone.") },
            confirmButton = { FlatButton("OK", { about = false }) })
    }
}

@Composable private fun Modifier.horizontalScrollCompat() = this.then(Modifier.horizontalScroll(rememberScrollState()))

@Composable private fun Label(text: String, size: Int = 12, color: Color = Color.White) {
    Text(text, color = color, fontSize = size.sp, fontFamily = FontFamily.SansSerif, lineHeight = (size + 5).sp)
}
@Composable private fun InfoLine(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) { Label(label, 11, Muted); Label(value, 12) }
}
@Composable private fun FlatButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true) {
    Box(modifier.defaultMinSize(minHeight = 48.dp).semantics {
        if (text == "+") contentDescription = "Increase target temperature"
        if (text == "−") contentDescription = "Decrease target temperature"
    }.border(1.dp, if (enabled) Caption else Color.Gray)
        .background(Color.Black).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 7.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
        Label(text, 12, if (enabled) Color.White else Color.Gray)
    }
}
@Composable private fun MenuButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Row(modifier.heightIn(min = 48.dp).semantics { this.selected = selected }
        .border(1.dp, if (selected) Color.White else MenuBorder).background(Color.Black)
        .clickable(role = Role.Tab, onClick = onClick).padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = if (selected) Color.White else Cyan, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Label("▿", 11, Cyan)
    }
}
private fun heaterLabel(s: VentyStatus?, connected: Boolean): String = when {
    !connected -> "device disconnected"
    s == null -> "waiting for device status"
    !s.heaterMode.isHeating -> "heater off"
    s.setpointReached -> "target reached"
    else -> "heating"
}
@Composable private fun HeaterControl(s: VentyStatus?, enabled: Boolean, controller: VentyController) {
    FlatButton(if (s?.heaterMode?.isHeating == true) "turn heater off" else "turn heater on",
        { controller.setHeaterMode(if (s?.heaterMode?.isHeating == true) HeaterMode.Off else HeaterMode.On) },
        Modifier.fillMaxWidth(), enabled)
}
@Composable private fun ArchiveScope(all: Boolean, onToggle: () -> Unit, demo: Boolean) {
    Label(if (all) "all saved devices / includes demo entries" else if (demo) "demo archive / simulated sessions" else "this device's archive", 11, Muted)
    FlatButton(if (all) "show this device" else "show all devices", onToggle)
}
@Composable private fun NoteEditor(entry: CollectionEntry, save: (String, String) -> Unit) {
    var note by rememberSaveable(entry.id, entry.note) { mutableStateOf(entry.note) }
    Label(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(entry.startMs), 11, Muted)
    OutlinedTextField(value = note, onValueChange = { note = it.take(120) },
        label = { Label("one line / optional", 10) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    FlatButton("save note", { save(entry.id, note) }, Modifier.fillMaxWidth())
}
@Composable private fun EmberTrail(count: Int) {
    Canvas(Modifier.fillMaxWidth().height(145.dp)) {
        // A bounded constellation viewport keeps rendering cheap; the full bead count is retained.
        val visible = minOf(count, 120)
        var previous: androidx.compose.ui.geometry.Offset? = null
        repeat(visible) { i ->
            val point = androidx.compose.ui.geometry.Offset(size.width * (.08f + ((i * 73) % 83) / 100f), size.height * (.1f + ((i * 41) % 80) / 100f))
            previous?.let { drawLine(Caption.copy(alpha = .5f), it, point, 1.dp.toPx(), StrokeCap.Round) }
            drawCircle(Cyan, 3.dp.toPx(), point)
            previous = point
        }
    }
}
@Composable private fun WeeklyPostcard(entries: List<CollectionEntry>) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val week = entries.filter { val d = Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate(); !d.isBefore(monday) && !d.isAfter(today) }
    var dismissed by rememberSaveable(monday.toString()) { mutableStateOf(false) }
    Label("weekly postcard")
    if (dismissed) {
        Label("Postcard put away. Your archive stays.", 12, Muted)
        FlatButton("view postcard", { dismissed = false })
    } else {
        val band = week.flatMap { it.bands }.groupingBy { it }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key }).firstOrNull()?.key
        Label("${week.size} recorded sessions this week.")
        Label(band?.let { "favourite reached band: $it–${it + 4} °C" } ?: "A quiet week. Nothing to keep up with.", 12, Muted)
        EmberTrail(week.size)
        Label("A small picture of your week. No rankings or goals.", 11, Muted)
        FlatButton("put postcard away", { dismissed = true })
    }
}
