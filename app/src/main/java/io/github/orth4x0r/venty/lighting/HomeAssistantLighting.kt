package io.github.orth4x0r.venty.lighting

import android.content.Context
import android.os.SystemClock
import io.github.orth4x0r.venty.ble.ConnectionState
import io.github.orth4x0r.venty.ble.DeviceKind
import io.github.orth4x0r.venty.ble.VentyController
import io.github.orth4x0r.venty.ble.VentyStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL

internal const val DEFAULT_HA_ADDRESS = "http://192.168.1.170:8123"
private val cleartextHosts = setOf("192.168.1.170", "homeassistant.local")
private val webhookIdPattern = Regex("[A-Za-z0-9_-]{24,128}")

internal fun homeAssistantWebhook(address: String, id: String): URL? {
    if (!webhookIdPattern.matches(id)) return null
    return runCatching {
        val uri = URI(address.trim())
        val scheme = uri.scheme?.lowercase()
        val host = requireNotNull(uri.host).lowercase()
        require(scheme == "https" || (scheme == "http" && host in cleartextHosts))
        require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
        require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/")
        require(uri.port == -1 || uri.port in 1..65535)
        URI(scheme, null, host, uri.port, "/api/webhook/$id", null, null).toURL()
    }.getOrNull()
}

internal data class LightingSettings(
    val address: String,
    val webhookId: String,
    val enabled: Boolean,
    val profile: LightingProfile,
) {
    val endpoint: URL? = homeAssistantWebhook(address, webhookId)
}

internal class HomeAssistantLighting(
    context: Context,
    controller: VentyController,
    private val paused: StateFlow<Boolean>,
    scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences("subventy_lighting", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(
        LightingSettings(
            prefs.getString("address", DEFAULT_HA_ADDRESS) ?: DEFAULT_HA_ADDRESS,
            prefs.getString("webhookId", "").orEmpty(),
            prefs.getBoolean("enabled", false),
            prefs.getString("profile", null)?.let(LightingProfile::parse) ?: LightingProfile.presets.first(),
        ),
    )
    val settings = _settings.asStateFlow()
    private val _library = MutableStateFlow(loadLibrary())
    val library = _library.asStateFlow()
    private val _message = MutableStateFlow("")
    val message = _message.asStateFlow()

    private data class Request(val settings: LightingSettings, val phase: LightingPhase, val manual: Boolean)
    private data class Reading(
        val config: LightingSettings,
        val paused: Boolean,
        val connection: ConnectionState,
        val kind: DeviceKind,
        val status: VentyStatus?,
    )
    private val requests = Channel<Request>(Channel.CONFLATED)

    init {
        scope.launch {
            val phases = LightingPhases()
            var bridgeWasActive = false
            var previousSettings: LightingSettings? = null
            combine(_settings, paused, controller.connection, controller.kind, controller.reportedStatus, ::Reading)
                .collect { (config, trackingPaused, connection, kind, status) ->
                    val real = connection is ConnectionState.Connected && !connection.demo
                    val suspended = !config.enabled || config.endpoint == null || trackingPaused ||
                        (connection is ConnectionState.Connected && connection.demo)
                    if (suspended || previousSettings != config) {
                        phases.reset()
                        bridgeWasActive = false
                    }
                    previousSettings = config
                    if (suspended) return@collect
                    val previousLink = bridgeWasActive
                    bridgeWasActive = real
                    if (!real && !previousLink) return@collect
                    val edge = phases.update(status.takeIf { real }, kind == DeviceKind.Venty, SystemClock.elapsedRealtime())
                    if (edge != null) requests.trySend(Request(config, edge, manual = false))
                }
        }

        scope.launch(Dispatchers.IO) {
            for (request in requests) {
                if (request.settings != _settings.value || (!request.manual && paused.value)) continue
                val endpoint = request.settings.endpoint ?: continue
                _message.value = "Sending ${request.phase.title.lowercase()}…"
                val result = sendLightingCue(endpoint, request.settings.profile[request.phase], request.phase)
                if (request.settings == _settings.value) _message.value = result
            }
        }
    }

    @Synchronized fun configure(address: String, webhookId: String): Boolean {
        val previous = _settings.value
        val candidate = previous.copy(address = address.trim(), webhookId = webhookId.trim())
        if (candidate.endpoint == null) return false
        val changed = candidate.address != previous.address || candidate.webhookId != previous.webhookId
        save(if (changed) candidate.copy(enabled = false) else candidate)
        _message.value = "Bridge saved. Preview a look to test it."
        return true
    }

    @Synchronized fun setEnabled(enabled: Boolean) {
        val current = _settings.value
        if (!enabled || current.endpoint != null) save(current.copy(enabled = enabled))
    }

    @Synchronized fun useProfile(profile: LightingProfile) {
        save(_settings.value.copy(profile = profile))
    }

    @Synchronized fun saveProfile(profile: LightingProfile) {
        val updated = _library.value.filterNot { it.name.equals(profile.name, ignoreCase = true) } + profile
        save(_settings.value.copy(profile = profile), updated)
    }

    fun preview(phase: LightingPhase) {
        val current = _settings.value
        if (current.endpoint != null) requests.trySend(Request(current, phase, manual = true))
    }

    private fun save(value: LightingSettings, library: List<LightingProfile>? = null) {
        val edit = prefs.edit().putString("address", value.address).putString("webhookId", value.webhookId)
            .putBoolean("enabled", value.enabled).putString("profile", value.profile.toJson().toString())
        if (library != null) {
            val json = JSONArray().apply { library.forEach { put(it.toJson()) } }
            edit.putString("library", json.toString())
        }
        edit.apply()
        if (library != null) _library.value = library
        _settings.value = value
    }

    private fun loadLibrary(): List<LightingProfile> {
        val stored = prefs.getString("library", null) ?: return emptyList()
        val list = runCatching { JSONArray(stored) }.getOrNull() ?: return emptyList()
        return (0 until list.length()).mapNotNull { LightingProfile.parse(list.optString(it)) }
    }
}

internal fun sendLightingCue(url: URL, cue: LightCue, phase: LightingPhase): String {
    val connection = try {
        (if (url.protocol == "http") url.openConnection(Proxy.NO_PROXY) else url.openConnection()) as HttpURLConnection
    } catch (_: IOException) {
        return "Could not open the Home Assistant connection."
    }
    return try {
        val payload = cue.webhookBody(phase).toByteArray(Charsets.UTF_8)
        connection.requestMethod = "POST"
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 1800
        connection.readTimeout = 1800
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setFixedLengthStreamingMode(payload.size)
        connection.outputStream.use { it.write(payload) }
        when (val code = connection.responseCode) {
            in 200..299 -> "${phase.title} sent to Home Assistant."
            401, 403, 404, 405 -> "Webhook unavailable ($code). Check its ID, LAN access and POST setting."
            in 300..399 -> "Home Assistant redirected the request. Enter its final base address."
            else -> "Home Assistant returned HTTP $code."
        }
    } catch (_: SocketTimeoutException) {
        "Home Assistant timed out. Check that the phone is on the same network."
    } catch (_: IOException) {
        "Could not reach Home Assistant. Check its address and the phone’s network."
    } finally {
        connection.disconnect()
    }
}
