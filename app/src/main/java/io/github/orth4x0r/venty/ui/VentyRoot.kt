package io.github.orth4x0r.venty.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.orth4x0r.venty.ble.ConnectionState
import io.github.orth4x0r.venty.ble.VentyController

private val requiredPermissions: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

@Composable
fun VentyRoot(controller: VentyController) {
    val context = LocalContext.current
    fun hasPermissions() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    var granted by remember { mutableStateOf(hasPermissions()) }
    var bluetoothOn by remember { mutableStateOf(granted && controller.bluetoothEnabled) }
    var triedAutoConnect by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        granted = hasPermissions()
        bluetoothOn = granted && controller.bluetoothEnabled
    }
    val enableBluetooth = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { bluetoothOn = granted && controller.bluetoothEnabled }

    // Permissions and Bluetooth can change while we're in the background.
    LifecycleResumeEffect(Unit) {
        granted = hasPermissions()
        bluetoothOn = granted && controller.bluetoothEnabled
        onPauseOrDispose { }
    }

    val connection by controller.connection.collectAsStateWithLifecycle()

    // Ask for notifications once Bluetooth access is sorted, for the ready and low-battery alerts.
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(granted) {
        if (granted && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(granted, bluetoothOn) {
        if (granted && bluetoothOn && !triedAutoConnect && connection == ConnectionState.Idle) {
            triedAutoConnect = true
            controller.connectToLast()
        }
    }

    io.github.orth4x0r.venty.ui.subventy.SubVentyScreen(
        controller = controller,
        permissions = granted,
        bluetoothOn = bluetoothOn,
        onGrant = { permissionLauncher.launch(requiredPermissions) },
        onEnableBluetooth = { enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
    )
}
