package io.github.orth4x0r.venty.ui.subventy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.PersistableBundle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.orth4x0r.venty.lighting.HomeAssistantLighting
import io.github.orth4x0r.venty.lighting.LightCue
import io.github.orth4x0r.venty.lighting.LightingPhase
import io.github.orth4x0r.venty.lighting.LightingProfile
import io.github.orth4x0r.venty.lighting.LightingSettings
import io.github.orth4x0r.venty.lighting.homeAssistantWebhook
import java.util.UUID

private const val BLUEPRINT_URL =
    "https://raw.githubusercontent.com/thehillbeyondthisone/venty-android-umbrella/" +
        "design/visual-directions/docs/home-assistant/subventy-lighting.yaml"

@Composable
internal fun LightingDesk(bridge: HomeAssistantLighting, paused: Boolean) {
    val settings by bridge.settings.collectAsStateWithLifecycle()
    val message by bridge.message.collectAsStateWithLifecycle()
    val library by bridge.library.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var window by rememberSaveable { mutableStateOf<String?>(null) }
    val profile = settings.profile

    S7Text(profile.name, 19)
    S7Text(
        when {
            settings.endpoint == null -> "Home Assistant setup required"
            !settings.enabled -> "Automatic lighting off"
            paused -> "Automatic lighting paused by Do not track"
            else -> "Automatic lighting armed for connected hardware"
        },
        11, S7.Link,
    )
    Row(
        Modifier.fillMaxWidth().height(38.dp).border(1.dp, S7.Border).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        LightingPhase.entries.forEach { phase ->
            val color = cueColor(profile[phase])
            Box(
                Modifier.weight(1f).fillMaxHeight().background(
                    Brush.verticalGradient(listOf(color, color.copy(alpha = .3f))),
                ),
            )
        }
    }
    S7Button("Profiles…", { window = "profiles" }, Modifier.fillMaxWidth())
    S7Button("Edit looks…", { window = "edit" }, Modifier.fillMaxWidth())
    S7Button("Home Assistant setup…", { window = "setup" }, Modifier.fillMaxWidth())
    S7Check("React to my device", settings.enabled, enabled = settings.endpoint != null, bridge::setEnabled)
    if (message.isNotBlank()) S7Text(message, 11, S7.Link)

    S7Group("Preview on the room lights") {
        if (settings.endpoint == null) S7Text("Set up the bridge first.", 11, S7.Muted)
        LightingPhase.entries.forEach { phase ->
            val cue = profile[phase]
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(30.dp, 48.dp).border(1.dp, S7.Border).background(cueColor(cue)))
                S7Button(
                    "${phase.title} · ${if (cue.brightness == 0) "off" else "${cue.brightness}%"}",
                    { bridge.preview(phase) },
                    Modifier.weight(1f),
                    enabled = settings.endpoint != null,
                )
            }
        }
        S7Text("Draw uses the Venty’s timer as an estimate. Volcano Hybrid supports heating, ready and session end.", 11, S7.Muted)
    }

    when (window) {
        "setup" -> BridgeSetup(settings, bridge::configure, onClose = { window = null })
        "edit" -> ProfileEditor(profile, library, bridge::saveProfile, onClose = { window = null })
        "profiles" -> UtilityWindow("lighting profiles", { window = null }) {
            S7Text("Starter profiles", 11, S7.Link)
            LightingProfile.presets.forEach { preset ->
                S7Button(preset.name, { bridge.useProfile(preset); window = null }, Modifier.fillMaxWidth())
            }
            if (library.isNotEmpty()) S7Text("Saved profiles", 11, S7.Link)
            library.forEach { saved ->
                S7Button(saved.name, { bridge.useProfile(saved); window = null }, Modifier.fillMaxWidth())
            }
            S7Button("Import profile…", { window = "import" }, Modifier.fillMaxWidth())
            S7Button("Share current profile…", {
                val share = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "${profile.name} · SubVenty")
                    .putExtra(Intent.EXTRA_TEXT, profile.toJson().toString(2))
                context.startActivity(Intent.createChooser(share, "Share lighting profile"))
            }, Modifier.fillMaxWidth())
        }
        "import" -> ImportProfile(library, bridge::saveProfile, onClose = { window = null })
    }
}

@Composable
private fun BridgeSetup(settings: LightingSettings, onSave: (String, String) -> Boolean, onClose: () -> Unit) {
    val clipboard = LocalContext.current.getSystemService(ClipboardManager::class.java)
    var address by rememberSaveable { mutableStateOf(settings.address) }
    // Keep the webhook secret out of Android's saved view state.
    var secret by remember { mutableStateOf(settings.webhookId.ifEmpty { newWebhookId() }) }
    var hint by rememberSaveable { mutableStateOf("") }
    val valid = homeAssistantWebhook(address, secret.trim()) != null

    UtilityWindow("Home Assistant bridge", onClose) {
        S7Text("Import the blueprint in Home Assistant, choose your light entities, and paste this webhook ID.", 12)
        S7Input("Home Assistant base address", address, { address = it.take(300) })
        S7Input("Webhook ID", secret, { secret = it.take(128) }, secret = true)
        S7Button("Copy webhook ID", {
            clipboard?.setPrimaryClip(ClipData.newPlainText("Home Assistant webhook ID", secret.trim()).apply {
                description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
            })
            hint = "Webhook ID copied."
        }, enabled = valid)
        S7Button("Generate a new ID", { secret = newWebhookId(); hint = "Update the automation with the new ID before saving." })
        S7Button("Copy blueprint URL", {
            clipboard?.setPrimaryClip(ClipData.newPlainText("SubVenty lighting blueprint", BLUEPRINT_URL))
            hint = "Blueprint URL copied."
        })
        if (hint.isNotBlank()) S7Text(hint, 11, S7.Link)
        if (!valid) S7Text("Enter the Pi’s HTTP address or an HTTPS base URL, and a webhook ID of at least 24 letters, digits, dashes or underscores.", 11, S7.Muted)
        S7Button("Save bridge", { if (onSave(address, secret)) onClose() }, enabled = valid)
    }
}

private fun newWebhookId() = "subventy_" + UUID.randomUUID().toString().replace("-", "")

@Composable
private fun ProfileEditor(
    profile: LightingProfile,
    library: List<LightingProfile>,
    onSave: (LightingProfile) -> Unit,
    onClose: () -> Unit,
) {
    var name by rememberSaveable(profile) { mutableStateOf(profile.name) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var fields by rememberSaveable(profile) {
        mutableStateOf(ArrayList(LightingPhase.entries.flatMap { phase ->
            val cue = profile[phase]
            listOf(cue.hex, cue.brightness.toString(), cue.fadeMs.toString())
        }))
    }
    val looks = LightingPhase.entries.associateWith { phase ->
        val i = phase.ordinal * 3
        LightCue.fromInput(fields[i], fields[i + 1], fields[i + 2])
    }
    val offset = selected * 3
    val phase = LightingPhase.entries[selected]
    val canSave = name.isNotBlank() && name.none(Char::isISOControl) && looks.values.all { it != null }
    fun edit(index: Int, value: String) { fields = ArrayList(fields).apply { this[index] = value } }

    UtilityWindow("profile editor", onClose) {
        S7Input("Profile name", name, { name = it.take(40) })
        LightingPhase.entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { option ->
                    val mark = if (option == phase) "› " else ""
                    S7Button("$mark${option.title}", { selected = option.ordinal }, Modifier.weight(1f))
                }
            }
        }
        S7Group(phase.title) {
            looks[phase]?.let { cue ->
                Box(Modifier.fillMaxWidth().height(32.dp).background(cueColor(cue)).border(1.dp, S7.Border))
            }
            S7Input("RGB hex color", fields[offset], { edit(offset, it.take(8)) })
            S7Input("Brightness % / 0 turns off", fields[offset + 1], { edit(offset + 1, it.take(3)) }, numeric = true)
            S7Input("Fade in milliseconds / 200–5000", fields[offset + 2], { edit(offset + 2, it.take(4)) }, numeric = true)
        }
        if (!canSave) S7Text("Check the profile name and the color, brightness and fade for each look.", 11, S7.Link)
        val replaces = library.any { it.name.equals(name.trim(), ignoreCase = true) }
        S7Button(if (replaces) "Replace saved profile" else "Save and select", {
            val cues = looks.mapValues { requireNotNull(it.value) }
            onSave(LightingProfile(name.trim(), cues))
            onClose()
        }, enabled = canSave)
    }
}

@Composable
private fun ImportProfile(
    library: List<LightingProfile>,
    onSave: (LightingProfile) -> Unit,
    onClose: () -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    val preview = remember(input) { LightingProfile.parse(input) }
    UtilityWindow("import lighting profile", onClose) {
        S7Input("Paste the shared SubVenty profile JSON", input, { input = it.take(4097) }, singleLine = false)
        if (input.isNotBlank()) S7Text(preview?.name ?: "This is not a valid SubVenty lighting profile.", 12)
        val replaces = preview != null && library.any { it.name.equals(preview.name, ignoreCase = true) }
        S7Button(if (replaces) "Replace saved profile" else "Import and select", {
            preview?.let(onSave)
            onClose()
        }, enabled = preview != null)
    }
}

private fun cueColor(cue: LightCue): Color =
    if (cue.brightness == 0) S7.Workspace else Color(0xFF000000 or cue.rgb.toLong())
