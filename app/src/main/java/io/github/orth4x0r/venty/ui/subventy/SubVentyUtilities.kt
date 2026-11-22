package io.github.orth4x0r.venty.ui.subventy

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.orth4x0r.venty.VentyApp
import io.github.orth4x0r.venty.ble.*
import io.github.orth4x0r.venty.ui.settings.AppPrefs
import io.github.orth4x0r.venty.update.AppUpdater
import kotlinx.coroutines.delay

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
