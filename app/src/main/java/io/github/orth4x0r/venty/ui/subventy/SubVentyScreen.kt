package io.github.orth4x0r.venty.ui.subventy

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import io.github.orth4x0r.venty.update.AppUpdater
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

private val Caption = S7.Caption
private val Panel = S7.Panel
private val Cyan = S7.Link
private val MenuBorder = Color(0xFF0080C0)
private val Muted = S7.Muted
private val pages = listOf("connection", "temperature", "session log", "collections", "field notes", "rituals", "local options")

/** Native Compose client. Every device write goes through the existing controller. */
@Composable
fun SubVentyScreen(controller: VentyController, permissions: Boolean, bluetoothOn: Boolean,
    onGrant: () -> Unit, onEnableBluetooth: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as VentyApp
    val scope = rememberCoroutineScope()
    val activity = context.findActivity()
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
    val linkEvents by controller.linkEvents.collectAsStateWithLifecycle()
    val night by app.night.prefs.collectAsStateWithLifecycle()
    val loot by app.night.loot.collectAsStateWithLifecycle()
    val secrets by app.night.secrets.collectAsStateWithLifecycle()
    val feed by app.night.feed.collectAsStateWithLifecycle()
    var termInput by rememberSaveable { mutableStateOf("") }
    var saver by remember { mutableStateOf(false) }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var game by rememberSaveable(stateSaver = IcebreakerSaver) { mutableStateOf(IcebreakerGame.new()) }
    var pulled by rememberSaveable { mutableStateOf<String?>(null) }
    var nothingLeft by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableIntStateOf(PAGE_TERMINAL) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var window by rememberSaveable { mutableStateOf<String?>(null) }
    var focusMode by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var noteDraft by rememberSaveable { mutableStateOf("") }
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
    val filteredArchive = archive.filter { query.isBlank() || it.note.contains(query, ignoreCase = true) ||
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(it.startMs).contains(query, ignoreCase = true) }
    val lastPage = ((filteredArchive.size - 1).coerceAtLeast(0) / 20)
    val safePage = archivePage.coerceIn(0, lastPage)
    val visibleEntries = filteredArchive.drop(safePage * 20).take(20)
    val unit = Temperature.unit(status?.fahrenheit == true)
    val view = LocalView.current
    DisposableEffect(status?.heaterMode?.isHeating, prefs.keepScreenOn) {
        view.keepScreenOn = status?.heaterMode?.isHeating == true && prefs.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(controller) { controller.notices.collect { notice = it } }
    LaunchedEffect(notice) { if (notice.isNotBlank()) { kotlinx.coroutines.delay(5000); notice = "" } }
    fun navigate(index: Int) {
        page = if (index == PAGE_ICEBREAKER) PAGE_LOOT else index
        archivePage = 0
        if (index == 8) window = "ramp"
        if (index == 9) window = "settings"
        if (index == PAGE_ICEBREAKER) window = "icebreaker"
    }
    val sessionTick = if (live != null) rememberNow(1000) else 0L
    val termCtx = TermContext(
        connected = connected && status != null, demo = demo, deviceName = name, kindName = kind.displayName,
        heating = status?.heaterMode?.isHeating == true, mode = status?.heaterMode.toTermMode(),
        reached = status?.setpointReached == true, fahrenheit = status?.fahrenheit == true,
        targetTenths = status?.targetTenths ?: 1850, effectiveTenths = status?.effectiveTargetTenths ?: status?.targetTenths ?: 1850,
        minTenths = kind.minTarget, maxTenths = kind.maxTarget,
        liveTenths = if (kind.liveTemperature) status?.currentTenths else null,
        battery = if (kind.hasBattery) status?.battery else null, charging = status?.charging == true,
        hasBoost = kind.hasBoost, hasPump = kind.hasPump, pumpOn = pump, rampRunning = ramp != null,
        sessionStartMs = live?.startMs, puffs = live?.puffs, handle = night.handle,
        lootCount = loot.size, secretsFound = secrets,
    )
    fun runCommand(line: String) {
        val result = Terminal.run(line, termCtx)
        app.night.appendFeed(listOf(TermLine("${prompt(night.handle)} $line", Tone.Echo)) + result.lines)
        result.secret?.let(app.night::found)
        termInput = ""
        for (action in result.actions) when (action) {
            is TermAction.SetTarget -> controller.setTarget(action.tenthsC)
            is TermAction.Heater -> if (action.on) { if (status?.heaterMode?.isHeating != true) controller.setHeaterMode(HeaterMode.On) }
                else controller.setHeaterMode(HeaterMode.Off)
            is TermAction.Mode -> controller.setHeaterMode(when (action.mode) {
                TermMode.Normal -> HeaterMode.On; TermMode.Boost -> HeaterMode.Boost; TermMode.Super -> HeaterMode.SuperBoost })
            is TermAction.Pump -> controller.setPump(action.on)
            TermAction.StopRamp -> controller.stopRamp()
            TermAction.Connect -> when {
                !permissions -> { app.night.appendFeed(listOf(TermLine("ERR bluetooth access needed. asking...", Tone.Err))); onGrant() }
                !bluetoothOn -> { app.night.appendFeed(listOf(TermLine("ERR bluetooth is off. asking...", Tone.Err))); onEnableBluetooth() }
                !controller.connectToLast() -> {
                    app.night.appendFeed(listOf(TermLine("no saved device. scanning on the connection page.", Tone.Dim)))
                    navigate(0); controller.startScan()
                }
            }
            TermAction.Disconnect -> controller.disconnect()
            is TermAction.Demo -> if (action.volcano) controller.startVolcanoDemo() else controller.startDemo()
            TermAction.Find -> controller.findMyDevice()
            is TermAction.Open -> when (action.target) {
                "ramp" -> window = "ramp"
                "settings" -> window = "settings"
                "log" -> navigate(2)
                "notes" -> navigate(4)
                "atlas" -> navigate(3)
                "loot" -> navigate(PAGE_LOOT)
                "icebreaker" -> window = "icebreaker"
            }
            TermAction.Clear -> app.night.clearFeed()
            is TermAction.SetHandle -> app.night.setPrefs(night.copy(handle = action.handle))
            is TermAction.Sound -> app.night.setPrefs(night.copy(modemSound = action.on))
            TermAction.Lock -> saver = true
        }
    }
    fun onGame(next: IcebreakerGame) {
        if (game.state == BreakState.Running && next.state == BreakState.Cracked) {
            val file = Loot.next(loot.map { it.first }.toSet())
            nothingLeft = file == null
            pulled = file?.id
            if (file != null) {
                app.night.pull(file.id)
                app.night.appendFeed(listOf(TermLine("*** icebreaker: pulled ${file.name}", Tone.Irc)))
                notice = "Pulled ${file.name}. Read it in loot."
            } else notice = "Node cracked. No new files."
        }
        game = next
    }
    // The feed narrates device-confirmed changes, so a dragged slider doesn't flood it.
    LaunchedEffect(controller) {
        if (app.night.feed.value.isEmpty()) app.night.appendFeed(Terminal.motd(termCtx).map { TermLine(it, Tone.Info) })
        kotlinx.coroutines.flow.combine(controller.connection, controller.reportedStatus, app.sessions.live, controller.pump) { c, s, l, p ->
            feedSnapshot(c, s, l, p, controller.lastDeviceName ?: controller.kind.value.displayName)
        }.collect { snap -> app.night.observe(snap) }
    }
    LaunchedEffect(controller) {
        var previous: ConnectionState? = null
        controller.connection.collect { c ->
            if (c is ConnectionState.Connected && previous is ConnectionState.Connecting && app.night.prefs.value.modemSound) ModemSound.play()
            previous = c
        }
    }
    val idleOnTerminal = page == PAGE_TERMINAL && window == null && !advanced && night.screensaver
    // Typing goes through the IME window, so the command line counts as activity too.
    LaunchedEffect(idleOnTerminal, lastTouch, termInput) {
        if (idleOnTerminal) { kotlinx.coroutines.delay(SCREENSAVER_AFTER_MS); saver = true }
    }
    val exportArchive = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val array = JSONArray()
                    entries.forEach { e -> array.put(JSONObject().put("id", e.id).put("startMs", e.startMs)
                        .put("endMs", e.endMs).put("device", e.device).put("reachedBandsC", JSONArray(e.bands)).put("note", e.note)) }
                    val json = JSONObject().put("schema", "subventy.archive").put("version", 1).put("paused", paused)
                        .put("entries", array).toString(2)
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                        ?: error("Could not open the selected file")
                }
                notice = "Archive exported."
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
            } catch (_: Exception) { notice = "Could not save the archive. Please choose another file." }
        }
    }
    BackHandler(enabled = saver || advanced || page != PAGE_TERMINAL || focusMode) {
        when { saver -> saver = false; advanced -> advanced = false; page != PAGE_TERMINAL -> page = PAGE_TERMINAL; else -> focusMode = false }
    }
    val reducedMotion = remember(context) {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Caption, onPrimary = Color.Black,
        background = Color.Black, surface = Panel, onSurface = Color.White,
        onBackground = Color.White, secondary = Cyan, outline = Caption),
        shapes = Shapes(extraSmall = RoundedCornerShape(0.dp), small = RoundedCornerShape(0.dp), medium = RoundedCornerShape(0.dp),
            large = RoundedCornerShape(0.dp), extraLarge = RoundedCornerShape(0.dp))) {
      CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
       Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
           // Watch (never consume) touches so vapor.scr only appears after real idleness.
           awaitPointerEventScope { while (true) { awaitPointerEvent(PointerEventPass.Initial); lastTouch = System.currentTimeMillis() } }
       }) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(5.dp)
            .border(1.dp, Color.Gray)) {
            Row(Modifier.fillMaxWidth().background(Caption).heightIn(min = 48.dp).padding(start = 7.dp),
                verticalAlignment = Alignment.CenterVertically) {
                SilverSeven(Modifier.size(20.dp))
                Text("SubVenty", color = Color.White, fontSize = 14.sp,
                    modifier = Modifier.weight(1f).padding(start = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                S7IconButton("Minimize app", Icons.Outlined.Minimize, { activity?.moveTaskToBack(true) })
                S7IconButton(if (focusMode) "Restore sidebar" else "Expand workspace", Icons.Outlined.CropSquare, { focusMode = !focusMode })
                S7IconButton("Close client window", Icons.Outlined.Close, { window = "close" })
            }
            Row(Modifier.fillMaxWidth().background(Panel).padding(6.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(Modifier.weight(1f)) {
                    Label("destination:", 10, Color(0xFFC7D8E8))
                    Text(name, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().border(1.dp, Color.Gray).background(Color(0xFF070B16)).padding(6.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Label("link:", 10); Label("BLE", 12) }
                FlatButton(if (connected) "disconnect" else "connect", {
                    if (connected) controller.disconnect()
                    else { page = 0; if (permissions && bluetoothOn) controller.connectToLast() }
                }, Modifier.widthIn(max = 110.dp))
                Box(Modifier.size(42.dp).semantics { contentDescription = "About SubVenty" }
                    .clickable(role = Role.Button) { window = "about" }, contentAlignment = Alignment.Center) {
                    Text("S7", fontSize = 24.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
                        letterSpacing = (-4).sp, color = Color(0xFF052D48), style = androidx.compose.ui.text.TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(Color(0xFF3975A6), androidx.compose.ui.geometry.Offset(1f, 1f), 1f)))
                }
            }
            ClientToolbar(onNavigate = { window = "navigation" }, onPage = ::navigate,
                onWindow = { window = it }, locateEnabled = connected && kind.hasVentyExtras)
            if (advanced) {
                FlatButton("« return to SubVenty", { advanced = false }, Modifier.fillMaxWidth())
                Box(Modifier.weight(1f)) { DashboardScreen(controller, connection) }
            } else {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    // Large fonts and narrow phones use a top menu, preserving readable content.
                    val stacked = maxWidth < 350.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.weight(1f).fillMaxWidth().padding(5.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            if (!stacked && !focusMode) NavigationTree(page, ::navigate,
                                Modifier.width(116.dp).fillMaxHeight().border(1.dp, Color(0xFF34343D)))
                            Column(Modifier.weight(1f).fillMaxHeight()) {
                                ClientWorkspace(page, Modifier.weight(1f)) {
                                    Label(listOf("device information", "temperature control", "session archive", "collection browser",
                                        "field notes", "weekly postcard", "local options", "link monitor", "ramp manager", "device properties",
                                        "session terminal", "icebreaker", "loot").getOrElse(page) { "device information" }, 13)
                                    Label("${kind.displayName.lowercase()} / ${if (demo) "demo connection" else if (connected) "device connected" else "not connected"}", 10, Muted)
                                    when (page) {
                                        PAGE_TERMINAL -> TerminalHome(
                                            HomeState(termCtx, feed, live?.let { Terminal.clock(java.time.Duration.ofMillis(sessionTick - it.startMs)) },
                                                live?.etaSeconds, loot.size),
                                            HomeActions(onCommand = ::runCommand, onTargetDrag = { controller.setTarget(it) },
                                                onTargetDone = { notice = "Target sent to device." }),
                                            termInput, { termInput = it })
                                        PAGE_LOOT -> LootListing(loot.mapNotNull { (id, at) -> Loot.byId(id)?.let { it to at } }, secrets,
                                            onOpen = { window = "loot:${it.id}" }, onIcebreaker = { window = "icebreaker" })
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
                                                connected -> Unit
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
                                                FlatButton("device properties / offsets…", { window = "settings" })
                                            }
                                            if (kind.hasPump) FlatButton(if (pump) "stop pump" else "start pump", { controller.setPump(!pump) }, enabled = enabled)
                                            if (ramp != null) FlatButton("Stop active ramp", { controller.stopRamp() }, enabled = enabled)
                                            presets.forEach { preset -> FlatButton("${preset.name} / ${Temperature.toDisplay(preset.tenthsC, status?.fahrenheit == true)}°$unit", { controller.setTarget(preset.tenthsC) }, enabled = enabled) }
                                            FlatButton("ramp editor…", { window = "ramp" })
                                            FlatButton("legacy controls", { advanced = true }, enabled = connection is ConnectionState.Connected || connection is ConnectionState.Reconnecting)
                                        }
                                        2 -> {
                                            Label("local session archive")
                                            ArchiveScope(showAll, { showAll = !showAll; archivePage = 0 }, demo)
                                            if (archive.isEmpty()) Label("Your archive is empty. Completed sessions will appear here.", 12, Muted)
                                            S7Input("find an entry / date or note", query, { query = it; archivePage = 0 })
                                            visibleEntries.forEach { e ->
                                                ArchiveRow(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(e.startMs),
                                                    "${(e.endMs - e.startMs) / 60000} min / ${if(e.device.startsWith("demo:")) "demo" else "device"}" + if(e.note.isNotBlank()) " / ${e.note}" else "",
                                                    { editingId = e.id; noteDraft = e.note; window = "note" })
                                            }
                                            if (archive.isNotEmpty() && filteredArchive.isEmpty()) Label("No entries match this search.", 11, Muted)
                                            if (live != null) Label("Session in progress. It will be archived after the heater is off.", 11, Muted)
                                        }
                                        3 -> {
                                            ArchiveScope(showAll, { showAll = !showAll; archivePage = 0 }, demo)
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
                                            ArchiveScope(showAll, { showAll = !showAll; archivePage = 0 }, demo)
                                            Label("field notes / personal journal")
                                            if (archive.isEmpty()) Label("Notes attach to completed sessions. Nothing to write until you have an entry.", 12, Muted)
                                            S7Input("find a note / text or date", query, { query = it; archivePage = 0 })
                                            visibleEntries.forEach { entry -> ArchiveRow(
                                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(entry.startMs),
                                                entry.note.ifBlank { "no note yet / open to write one" },
                                                { editingId = entry.id; noteDraft = entry.note; window = "note" }) }
                                            if (archive.isNotEmpty() && filteredArchive.isEmpty()) Label("No notes match this search.", 11, Muted)
                                        }
                                        5 -> WeeklyPostcard(archive)
                                        6 -> {
                                            Label("local options")
                                            S7Check("do not track", paused) { value ->
                                                app.sessions.setTrackingPaused(value)
                                                if (!value) controller.reportedStatus.value?.takeIf { !it.heaterMode.isHeating }
                                                    ?.let { app.sessions.onStatus(it, device = key) }
                                                app.collections.setPaused(value)
                                            }
                                            Label(if (paused) "Collection paused. Your archive stays. Resume will wait until the heater is off; no backfill." else "Pause new automatic session and battery logs. Existing stamps, beads and notes stay.", 11, Muted)
                                            S7Group("terminal") {
                                                S7Check("modem sound on connect", night.modemSound) { app.night.setPrefs(night.copy(modemSound = it)) }
                                                S7Check("screensaver when idle on the terminal", night.screensaver) { app.night.setPrefs(night.copy(screensaver = it)) }
                                                Label("handle: ${night.handle} / change it at the prompt with handle <name>", 11, Muted)
                                                FlatButton("forget loot and found commands", { app.night.clearGame(); notice = "Loot cleared. Your archive is untouched." })
                                            }
                                            InfoLine("sound", if (night.modemSound) "modem only" else "off")
                                            InfoLine("reminders", "none")
                                            InfoLine("storage", "local")
                                            FlatButton("client options…", { window = "client" })
                                            FlatButton("device properties…", { window = "settings" })
                                            FlatButton("export archive…", { window = null; exportArchive.launch("subventy-archive.json") })
                                            Label("${AppUpdater.currentVersion(context)} / native client", 11, Muted)
                                        }
                                        7 -> {
                                            Label("connection activity / newest first", 11, Muted)
                                            if (linkEvents.isEmpty()) Label("No connection events yet.", 12, Muted)
                                            linkEvents.forEach { event ->
                                                Label(DateFormat.getTimeInstance(DateFormat.SHORT).format(event.atMs), 10, S7.Link)
                                                Label(event.text, 11)
                                            }
                                        }
                                        8 -> {
                                            S7Group("temperature automation") {
                                                Label(ramp?.let { "${it.phase.name.lowercase()} / ${it.targetC} °C" } ?: "No ramp is running.")
                                                FlatButton("open ramp editor…", { window = "ramp" })
                                                if (ramp != null) FlatButton("stop ramp", { controller.stopRamp() })
                                                Label("Plans are edited and saved in the utility window. Heating starts only when you choose Start.", 11, Muted)
                                            }
                                        }
                                        9 -> {
                                            InfoLine("device", kind.displayName)
                                            InfoLine("firmware", info.firmware ?: "—")
                                            FlatButton("open device properties…", { window = "settings" })
                                            if (kind.hasVentyExtras) FlatButton("find my device…", { window = "find" }, enabled = connected)
                                        }
                                    }
                                    if ((page == 2 || page == 4) && filteredArchive.size > 20) {
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
            val clock = rememberNow()
            Row(Modifier.fillMaxWidth().background(Panel).semantics { liveRegion = LiveRegionMode.Polite }.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.border(1.dp, S7.Border).padding(4.dp)) { Label(if (demo) "demo" else if (connected) "online" else "idle", 10) }
                Box(Modifier.border(1.dp, S7.Border).padding(4.dp)) { Label("0.5.0", 10) }
                Box(Modifier.weight(1f).border(1.dp, S7.Border).padding(4.dp)) { Label(notice.ifBlank { heaterLabel(status, connected) }, 10) }
                Box(Modifier.border(1.dp, S7.Border).padding(4.dp)) { Label(hhmm.format(java.time.Instant.ofEpochMilli(clock).atZone(ZoneId.systemDefault())), 10) }
            }
        }
        if (saver) VaporScreensaver(termCtx, onWake = { saver = false; lastTouch = System.currentTimeMillis() })
       }
        when (window) {
            "about" -> UtilityWindow("about SubVenty", { window = null }) {
                SilverSeven(Modifier.fillMaxWidth().height(115.dp))
                Label("SubVenty ${AppUpdater.currentVersion(context)}", 16)
                Label("Personal thermal control. A native companion for Venty and Volcano Hybrid.")
                Label("Interface study after the SubSeven client. ICEBREAKER and its loot files are a side game: they never read or reward your sessions. Your archive and notes remain on this phone.", 11, Muted)
                FlatButton("OK", { window = null })
            }
            "navigation" -> UtilityWindow("navigator", { window = null }) {
                NavigationTree(page, { index -> window = null; navigate(index) }, Modifier.fillMaxWidth().heightIn(max = 430.dp))
            }
            "ramp" -> RampEditor(app, controller, enabled, { window = null }, { notice = it })
            "settings" -> DeviceProperties(app, controller, enabled, { window = null })
            "find" -> LocateDevice(controller, connected && kind.hasVentyExtras, { window = null })
            "client" -> ClientOptions(app, { window = null; exportArchive.launch("subventy-archive.json") }, { window = null })
            "note" -> UtilityWindow("field note", { window = null }) {
                entries.firstOrNull { it.id == editingId }?.let { entry ->
                    Label(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(entry.startMs), 11, Muted)
                    S7Input("one line / optional", noteDraft, { noteDraft = it.take(120) })
                    Label("${noteDraft.length} / 120", 10, Muted)
                    FlatButton("save note", { app.collections.saveNote(entry.id, noteDraft); window = null; notice = "Note saved." })
                } ?: Label("This entry is no longer available.")
                FlatButton("cancel", { window = null })
            }
            "icebreaker" -> UtilityWindow("icebreaker.exe", { window = null }) {
                IcebreakerBody(game, ::onGame, pulled?.let(Loot::byId), nothingLeft,
                    onRead = { window = "loot:${it.id}" }, onNewNode = { game = IcebreakerGame.new(); pulled = null; nothingLeft = false })
            }
            "close" -> UtilityWindow("close client", { window = null }) {
                Label("Close the application window?")
                Label("Heating and active ramps continue in the background. Use heater off in the control panel to stop heating.", 11, Muted)
                FlatButton("close app window", { activity?.finish() })
                FlatButton("keep open", { window = null })
            }
            else -> window?.takeIf { it.startsWith("loot:") }?.let { Loot.byId(it.removePrefix("loot:")) }?.let { file ->
                UtilityWindow(file.name, { window = null }) {
                    LootReader(file)
                    FlatButton("close", { window = null })
                }
            }
        }
      }
    }
}

@Composable private fun Label(text: String, size: Int = 12, color: Color = S7.Text) { S7Text(text, size, color) }
@Composable private fun InfoLine(label: String, value: String) {
    if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f) {
        Column { Label(label, 11, Muted); Label(value, 12) }
    } else Row(Modifier.fillMaxWidth().heightIn(min = 26.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(value, color = S7.Text, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}
@Composable private fun FlatButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true) { S7Button(text, onClick, modifier, enabled) }

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

private fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val SCREENSAVER_AFTER_MS = 120_000L

private fun HeaterMode?.toTermMode(): TermMode = when (this) {
    HeaterMode.Boost -> TermMode.Boost
    HeaterMode.SuperBoost -> TermMode.Super
    else -> TermMode.Normal
}

private fun feedSnapshot(c: ConnectionState, s: VentyStatus?, live: io.github.orth4x0r.venty.session.LiveSession?, pump: Boolean,
    fallbackName: String): FeedSnapshot {
    val (link, name, detail) = when (c) {
        is ConnectionState.Idle -> Triple("idle", fallbackName, null)
        is ConnectionState.Scanning -> Triple("scanning", fallbackName, null)
        is ConnectionState.Connecting -> Triple("connecting", c.name, null)
        is ConnectionState.Connected -> Triple("connected", c.name, null)
        is ConnectionState.Reconnecting -> Triple("reconnecting", c.name, "attempt ${c.attempt}")
        is ConnectionState.Failed -> Triple("failed", fallbackName, c.message)
    }
    return FeedSnapshot(link, name, demo = (c as? ConnectionState.Connected)?.demo == true, detail = detail,
        heating = s?.heaterMode?.isHeating == true, mode = s?.heaterMode.toTermMode(), reached = s?.setpointReached == true,
        targetTenths = s?.targetTenths, effectiveTenths = s?.effectiveTargetTenths, fahrenheit = s?.fahrenheit == true,
        battery = s?.battery, charging = s?.charging == true, puffs = live?.puffs ?: 0, pump = pump)
}

/** Saves an ICEBREAKER run across rotation: secret, node, draft and guesses (probes are recomputed). */
private val IcebreakerSaver = androidx.compose.runtime.saveable.listSaver<IcebreakerGame, String>(
    save = { listOf(it.secret, it.node, it.draft) + it.attempts.map { a -> a.guess } },
    restore = { l -> IcebreakerGame(l[0], l[1], l.drop(3).map { g -> Attempt(g, IcebreakerGame.probe(l[0], g)) }, l[2]) },
)
