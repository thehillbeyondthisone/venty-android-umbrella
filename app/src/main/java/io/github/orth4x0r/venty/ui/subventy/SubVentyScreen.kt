package io.github.orth4x0r.venty.ui.subventy

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.orth4x0r.venty.VentyApp
import io.github.orth4x0r.venty.ble.*
import io.github.orth4x0r.venty.collections.CollectionEntry
import io.github.orth4x0r.venty.session.drawBallsJson
import io.github.orth4x0r.venty.ui.HeatHaptics
import io.github.orth4x0r.venty.ui.heatProgress
import io.github.orth4x0r.venty.ui.history.HistoryScreen
import io.github.orth4x0r.venty.ui.settings.SettingsScreen
import io.github.orth4x0r.venty.ui.wrapped.WrappedScreen
import io.github.orth4x0r.venty.update.AppUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat

private enum class Page(val title: String) {
    Device("Device"), Archive("Archive"), Collections("Collections"), Notes("Field notes"),
    Postcard("Weekly postcard"), DialIn("Dial-in"), DrawSessions("Draw sessions"), History("Session history"), Wrapped("Wrapped"),
    Settings("Settings"), Lighting("Lighting Desk"), DeviceSettings("Device settings"),
    Links("Link monitor");

    val tab: Page get() = when (this) {
        Device -> Device
        Settings, Lighting, DeviceSettings, Links -> Settings
        else -> Archive
    }

    val parent: Page get() = when (this) {
        Device, Archive, Settings -> Device
        Wrapped -> History
        else -> tab
    }
}

private data class Notice(val text: String, val undo: (() -> Unit)?)

@Composable
fun SubVentyScreen(
    controller: VentyController,
    permissions: Boolean,
    bluetoothOn: Boolean,
    onGrant: () -> Unit,
    onEnableBluetooth: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as VentyApp
    val connection by controller.connection.collectAsStateWithLifecycle()
    val status by controller.status.collectAsStateWithLifecycle()
    val kind by controller.kind.collectAsStateWithLifecycle()
    val prefs by app.prefs.app.collectAsStateWithLifecycle()
    val live by app.sessions.live.collectAsStateWithLifecycle()
    val entries by app.collections.entries.collectAsStateWithLifecycle()
    val paused by app.collections.paused.collectAsStateWithLifecycle()
    val comparisons by app.collections.comparisons.collectAsStateWithLifecycle()
    val putAwayWeek by app.collections.putAwayWeek.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val screenState = rememberSaveableStateHolder()
    var page by rememberSaveable { mutableStateOf(Page.Device) }
    var window by rememberSaveable { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<Notice?>(null) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var noteDraft by rememberSaveable { mutableStateOf("") }

    val connected = connection as? ConnectionState.Connected
    val activeConnection = connected != null || connection is ConnectionState.Reconnecting
    val enabled = connected != null && status != null
    val demo = connected?.demo == true
    val fahrenheit = status?.fahrenheit == true
    val deviceKey = if (demo) "demo:${kind.name}" else controller.lastDeviceAddress ?: kind.name
    val archiveDevice = deviceKey.takeUnless { showAll }
    val archiveScope = archiveDevice?.let { "device:$it" } ?: "all"
    val archive = remember(entries, archiveDevice) {
        if (archiveDevice == null) entries else entries.filter { it.device == archiveDevice }
    }

    val view = LocalView.current
    val heaterOn = status?.heaterMode?.isHeating == true
    DisposableEffect(heaterOn, prefs.keepScreenOn) {
        view.keepScreenOn = heaterOn && prefs.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    val currentLive by rememberUpdatedState(live)
    HeatHaptics(
        heating = heaterOn && enabled,
        ready = heaterOn && enabled && status?.setpointReached == true,
        progress = { currentLive?.heatProgress() },
        enabled = prefs.heatHaptics,
    )

    fun notify(text: String, undo: (() -> Unit)? = null) { notice = Notice(text, undo) }
    LaunchedEffect(controller) { controller.notices.collect { notify(it) } }
    LaunchedEffect(notice) { if (notice != null) { delay(5000); notice = null } }
    BackHandler(enabled = page != Page.Device) { page = page.parent }
    fun editNote(entry: CollectionEntry) {
        editingId = entry.id
        noteDraft = entry.note
        window = "note"
    }

    val exportArchive = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val array = JSONArray()
                    entries.forEach { e ->
                        array.put(JSONObject().put("id", e.id).put("startMs", e.startMs)
                            .put("endMs", e.endMs).put("device", e.device)
                            .put("reachedBandsC", JSONArray(e.bands)).put("note", e.note)
                            .apply { if (e.drawBalls.isNotEmpty()) put("drawBalls", e.drawBalls.drawBallsJson()) })
                    }
                    val dialIn = JSONObject()
                    comparisons.forEach { (key, comparison) -> dialIn.put(key, comparison.toJson()) }
                    val json = JSONObject().put("schema", "subventy.archive").put("version", 1).put("paused", paused)
                        .put("entries", array).put("dialInComparisons", dialIn).toString(2)
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                        ?: error("Could not open the selected file")
                }
                notify("Archive exported.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                notify("Could not save the archive. Choose another file.")
            }
        }
    }
    fun export() { exportArchive.launch("subventy-archive.json") }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Deck.Amber, onPrimary = Deck.Ink, background = S7.Workspace, surface = S7.Panel,
        onSurface = S7.Text, onBackground = S7.Text, secondary = Deck.Amber, outline = S7.Border,
    )) {
        Column(
            Modifier.fillMaxSize().background(Deck.Graphite).safeDrawingPadding(),
        ) {
            InstrumentHeader(connection, status, kind, controller)
            if (page != Page.Device) DeckStripe()
            Box(Modifier.weight(1f).fillMaxWidth().background(if (page == Page.Device) Deck.Ivory else S7.Workspace)) {
                screenState.SaveableStateProvider(page.name) {
                    when (page) {
                        Page.Device -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            val deviceStatus = status
                            if (deviceStatus != null && activeConnection) {
                                InstrumentDashboard(app, controller, deviceStatus, kind, enabled, demo, paused, ::notify)
                            } else InstrumentConnection(controller, connection, permissions, bluetoothOn, onGrant, onEnableBluetooth)
                        }
                        Page.DeviceSettings -> FullDeviceSettings(app, controller, status.takeIf { enabled }, kind,
                            onFind = { window = "find" }, onBack = { page = Page.Settings })
                        Page.History -> {
                            val history by app.sessions.history.collectAsStateWithLifecycle()
                            val lifetime by app.sessions.lifetimePuffs.collectAsStateWithLifecycle()
                            HistoryScreen(history = history, lifetimePuffs = lifetime, fahrenheit = fahrenheit,
                                onClear = app.sessions::clearHistory, onWrapped = { page = Page.Wrapped },
                                onBack = { page = Page.Archive })
                        }
                        Page.Wrapped -> {
                            val history by app.sessions.history.collectAsStateWithLifecycle()
                            WrappedScreen(history = history, fahrenheit = fahrenheit, onBack = { page = Page.History },
                                range = kind.targetRange)
                        }
                        else -> Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (page != page.tab) S7Button("‹ ${page.parent.title}", { page = page.parent })
                            S7Text(page.title, 22)
                            when (page) {
                                Page.Archive -> {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        S7Button("Collections", { page = Page.Collections }, Modifier.weight(1f))
                                        S7Button("Field notes", { page = Page.Notes }, Modifier.weight(1f))
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        S7Button("Dial-in", { page = Page.DialIn }, Modifier.weight(1f))
                                        S7Button("Postcard", { page = Page.Postcard }, Modifier.weight(1f))
                                    }
                                    S7Button("Session history and Wrapped", { page = Page.History }, Modifier.fillMaxWidth())
                                    S7Button("Draw sessions", { page = Page.DrawSessions }, Modifier.fillMaxWidth())
                                    ArchiveScope(showAll, demo) { showAll = !showAll }
                                    ArchiveList(archive, notes = false, scope = archiveScope, onNote = ::editNote)
                                }
                                Page.Notes -> {
                                    ArchiveScope(showAll, demo) { showAll = !showAll }
                                    ArchiveList(archive, notes = true, scope = archiveScope, onNote = ::editNote)
                                }
                                Page.Collections -> {
                                    ArchiveScope(showAll, demo) { showAll = !showAll }
                                    CollectionBrowser(archive, if (showAll) 230 else kind.maxTarget / 10,
                                        fahrenheit, archiveScope, ::editNote)
                                }
                                Page.Postcard -> {
                                    ArchiveScope(showAll, demo) { showAll = !showAll }
                                    WeeklyPostcard(archive, fahrenheit, putAwayWeek, app.collections::setPutAwayWeek)
                                }
                                Page.DialIn -> {
                                    ArchiveScope(showAll, demo) { showAll = !showAll }
                                    DialInPanel(archive, comparisons[archiveScope], archiveScope, fahrenheit) { draft ->
                                        app.collections.saveComparison(archiveDevice, draft).also { saved ->
                                            notify(if (saved) "Comparison saved." else "A selected session is unavailable.")
                                        }
                                    }
                                }
                                Page.DrawSessions -> {
                                    ArchiveScope(showAll, demo) { showAll = !showAll }
                                    DrawArchive(archive, archiveScope)
                                }
                                Page.Settings -> {
                                    S7Button("Device settings and pairing", { page = Page.DeviceSettings }, Modifier.fillMaxWidth())
                                    if (kind.hasVentyExtras) S7Button("Find my device", { window = "find" }, Modifier.fillMaxWidth(), enabled)
                                    S7Button("Link monitor", { page = Page.Links }, Modifier.fillMaxWidth())
                                    S7Button("Lighting Desk", { page = Page.Lighting }, Modifier.fillMaxWidth())
                                    S7Button("Client options", { window = "client" }, Modifier.fillMaxWidth())
                                    S7Check("Do not track", paused) { pause ->
                                        app.sessions.setTrackingPaused(pause)
                                        if (!pause) controller.reportedStatus.value?.takeIf { !it.heaterMode.isHeating }
                                            ?.let { app.sessions.onStatus(it, device = deviceKey, supportsDraws = kind.hasVentyExtras) }
                                        app.collections.setPaused(pause)
                                    }
                                    S7Text(if (paused) "Automatic collection paused. Recording resumes after the heater is off."
                                        else "Pause automatic session and battery logs and reactive room lighting.", 12, S7.Muted)
                                    S7Button("Export archive", ::export, Modifier.fillMaxWidth())
                                    S7Text("SubVenty ${AppUpdater.currentVersion(context)}", 11, S7.Muted)
                                }
                                Page.Lighting -> LightingDesk(app.lighting, paused)
                                Page.Links -> {
                                    val events by controller.linkEvents.collectAsStateWithLifecycle()
                                    if (events.isEmpty()) S7Text("No connection events yet.", 12, S7.Muted)
                                    events.forEach { event ->
                                        S7Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(event.atMs), 11, S7.Link)
                                        S7Text(event.text)
                                    }
                                }
                                else -> Unit
                            }
                        }
                    }
                }
            }
            notice?.let { message ->
                Row(
                    Modifier.fillMaxWidth().background(Deck.Graphite).semantics { liveRegion = LiveRegionMode.Polite }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DeckLabel(message.text, 11, Deck.Ivory, Modifier.weight(1f))
                    message.undo?.let { undo ->
                        DeckKey("Undo", { undo(); notice = null }, finish = KeyFinish.Graphite)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(7.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(Page.Device, Page.Archive, Page.Settings).forEach { tab ->
                    DeckKey(tab.title, { page = tab }, Modifier.weight(1f), active = page.tab == tab, finish = KeyFinish.Graphite)
                }
            }
        }

        when (window) {
            "find" -> LocateDevice(controller, enabled && kind.hasVentyExtras) { window = null }
            "client" -> ClientOptions(app, { window = null; export() }, { window = null })
            "note" -> UtilityWindow("Field note", { window = null }) {
                val entry = entries.firstOrNull { it.id == editingId }
                if (entry == null) S7Text("This entry is no longer available.")
                else {
                    S7Text(entryDate(entry))
                    S7Input("One line / optional", noteDraft, { noteDraft = it.take(120) })
                    S7Text("${noteDraft.length} / 120", 10, S7.Muted)
                    S7Button("Save note", {
                        app.collections.saveNote(entry.id, noteDraft)
                        window = null
                        notify("Note saved.")
                    })
                }
            }
        }
    }
}

@Composable
private fun InstrumentHeader(
    connection: ConnectionState,
    status: VentyStatus?,
    kind: DeviceKind,
    controller: VentyController,
) {
    var menu by remember { mutableStateOf(false) }
    val connected = connection as? ConnectionState.Connected
    val detail = when (connection) {
        is ConnectionState.Connected -> if (connection.demo) connection.name else "${kind.displayName} connected"
        is ConnectionState.Connecting -> "Connecting…"
        is ConnectionState.Reconnecting -> "Reconnecting…"
        is ConnectionState.Scanning -> if (connection.active) "Scanning…" else "Select a device"
        else -> "Disconnected"
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DeckLabel("SUBVENTY", 17, Deck.Ivory)
        DeckLabel(detail, 11, Deck.Ivory, Modifier.weight(1f))
        if (status != null && connected != null && kind.hasBattery) {
            DeckLabel("${status.battery}%${if (status.charging) " + " else ""}", 12, Deck.Ivory)
        }
        if (connected != null || connection is ConnectionState.Reconnecting) {
            Box {
                DeckKey("⋯", { menu = true }, Modifier.width(48.dp), finish = KeyFinish.Graphite,
                    description = "Device connection menu")
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Disconnect") }, onClick = { menu = false; controller.disconnect() })
                    if (connected?.demo != true) DropdownMenuItem(
                        text = { Text("Forget device") }, onClick = { menu = false; controller.forgetDevice() },
                    )
                }
            }
        }
    }
}

@Composable
private fun InstrumentConnection(
    controller: VentyController,
    connection: ConnectionState,
    permissions: Boolean,
    bluetoothOn: Boolean,
    onGrant: () -> Unit,
    onEnableBluetooth: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        DeckLCD {
            DeckLabel("DEVICE LINK", 11)
            DeckLabel(when (connection) {
                is ConnectionState.Connecting -> "Connecting to ${connection.name}"
                is ConnectionState.Connected -> "Waiting for device status"
                is ConnectionState.Reconnecting -> "Reconnecting to ${connection.name}"
                is ConnectionState.Scanning -> if (connection.active) "Scanning for devices…" else "Select your device"
                is ConnectionState.Failed -> connection.message
                else -> "Connect a device"
            }, 20)
        }
        when {
            connection is ConnectionState.Connecting || connection is ConnectionState.Connected ||
                connection is ConnectionState.Reconnecting ->
                DeckKey("Cancel connection", controller::disconnect, Modifier.fillMaxWidth())
            !permissions -> DeckKey("Allow Bluetooth access", onGrant, Modifier.fillMaxWidth())
            !bluetoothOn -> DeckKey("Turn on Bluetooth", onEnableBluetooth, Modifier.fillMaxWidth())
            else -> {
                val scan = connection as? ConnectionState.Scanning
                if (scan?.active == true) DeckKey("Stop scanning", controller::stopScan, Modifier.fillMaxWidth())
                else DeckKey("Scan for devices", controller::startScan, Modifier.fillMaxWidth(), finish = KeyFinish.Rust)
                scan?.found?.forEach { device ->
                    DeckKey("${device.name} · ${device.rssi} dBm", { controller.connect(device) }, Modifier.fillMaxWidth())
                }
            }
        }
        if (connection !is ConnectionState.Connecting && connection !is ConnectionState.Connected &&
            connection !is ConnectionState.Reconnecting) {
            DeckKey("Try demo Venty", controller::startDemo, Modifier.fillMaxWidth())
            DeckKey("Try demo Volcano", controller::startVolcanoDemo, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun FullDeviceSettings(
    app: VentyApp,
    controller: VentyController,
    status: VentyStatus?,
    kind: DeviceKind,
    onFind: () -> Unit,
    onBack: () -> Unit,
) {
    val info by controller.info.collectAsStateWithLifecycle()
    val settings by controller.deviceSettings.collectAsStateWithLifecycle()
    val prefs by app.prefs.app.collectAsStateWithLifecycle()
    val events by controller.linkEvents.collectAsStateWithLifecycle()
    SettingsScreen(
        kind = kind, status = status, deviceSettings = settings, firmware = info.firmware,
        deviceName = controller.lastDeviceName, appPrefs = prefs, linkEvents = events,
        onFahrenheit = controller::setFahrenheit, onEcoCharge = controller::setEcoCharge,
        onEcoVoltage = controller::setEcoVoltage, onBoostVisualization = controller::setBoostVisualization,
        onBrightness = controller::setBrightness, onVibration = controller::setVibration,
        onBoostTimeoutDisabled = controller::setBoostTimeoutDisabled, onFindMy = onFind,
        deviceAddress = controller.lastDeviceAddress, onPairingHold = controller::holdForPairing,
        onPairingDone = controller::releasePairingHold, onAppPrefs = app.prefs::setApp, onBack = onBack,
    )
}

@Composable
private fun ArchiveScope(all: Boolean, demo: Boolean, onToggle: () -> Unit) {
    S7Text(when {
        all -> "All archives / includes demo sessions"
        demo -> "Demo archive"
        else -> "This device’s archive"
    }, 11, S7.Muted)
    S7Button(if (all) "Show this device" else "Show all devices", onToggle)
}

@Composable
private fun ArchiveList(archive: List<CollectionEntry>, notes: Boolean, scope: String, onNote: (CollectionEntry) -> Unit) {
    var query by rememberSaveable(scope) { mutableStateOf("") }
    var page by rememberSaveable(scope) { mutableIntStateOf(0) }
    val found = remember(archive, query) {
        if (query.isBlank()) archive else archive.filter {
            it.note.contains(query, ignoreCase = true) || entryDate(it).contains(query, ignoreCase = true)
        }
    }
    val last = (found.size - 1).coerceAtLeast(0) / 20
    val current = page.coerceAtMost(last)
    S7Input(if (notes) "Find a note" else "Find a session", query, { query = it; page = 0 })
    if (found.isEmpty()) S7Text(
        if (archive.isEmpty()) "Completed sessions will appear here." else "No entries match this search.",
        12, S7.Muted,
    )
    found.drop(current * 20).take(20).forEach { entry ->
        val summary = if (notes) entry.note.ifBlank { "No note yet" } else
            "${(entry.endMs - entry.startMs) / 60_000} min · ${if (entry.device.startsWith("demo:")) "Demo" else "Device"}" +
                entry.note.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        ArchiveRow(entryDate(entry), summary, { onNote(entry) })
    }
    if (last > 0) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        S7Button("Newer", { page = current - 1 }, enabled = current > 0)
        S7Button("Older", { page = current + 1 }, enabled = current < last)
    }
}

private fun entryDate(entry: CollectionEntry): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(entry.startMs)
