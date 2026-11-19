package io.github.orth4x0r.venty.lighting

import org.json.JSONArray
import org.json.JSONObject

internal enum class LightingPhase(val id: String, val title: String) {
    Heating("heating", "Heating"),
    Ready("ready", "Ready"),
    Draw("draw", "Draw"),
    End("end", "Session end"),
}

internal data class LightCue(val rgb: Int, val brightness: Int, val fadeMs: Int) {
    val hex: String get() = "#" + rgb.toString(16).padStart(6, '0').uppercase()

    init {
        require(rgb in 0..0xFFFFFF && brightness in 0..100 && fadeMs in 200..5000)
    }

    fun toJson() = JSONObject().put("color", hex).put("brightness", brightness).put("fade_ms", fadeMs)

    fun webhookBody(phase: LightingPhase) = JSONObject()
        .put("schema", "subventy.lighting-cue.v1")
        .put("phase", phase.id)
        .put("rgb", JSONArray(listOf(rgb shr 16, rgb shr 8 and 255, rgb and 255)))
        .put("brightness_pct", brightness)
        .put("transition", fadeMs / 1000.0)
        .toString()

    companion object {
        fun fromInput(color: String, brightness: String, fadeMs: String): LightCue? {
            val hex = color.trim().removePrefix("#")
            if (!hex.matches(Regex("[0-9a-fA-F]{6}"))) return null
            val level = brightness.trim().toIntOrNull()?.takeIf { it in 0..100 } ?: return null
            val fade = fadeMs.trim().toIntOrNull()?.takeIf { it in 200..5000 } ?: return null
            return LightCue(hex.toInt(16), level, fade)
        }

        fun fromJson(json: JSONObject): LightCue = requireNotNull(fromInput(
            json.getString("color"),
            json.get("brightness").toString(),
            json.get("fade_ms").toString(),
        ))
    }
}

internal data class LightingProfile(val name: String, val cues: Map<LightingPhase, LightCue>) {
    operator fun get(phase: LightingPhase) = cues.getValue(phase)

    fun toJson(): JSONObject = JSONObject()
        .put("schema", "subventy.lighting-profile.v1")
        .put("name", name)
        .put("looks", JSONObject().also { looks ->
            LightingPhase.entries.forEach { phase -> looks.put(phase.id, get(phase).toJson()) }
        })

    companion object {
        fun parse(text: String): LightingProfile? {
            if (text.length > 4096) return null
            return runCatching {
                val profile = JSONObject(text)
                require(profile.getString("schema") == "subventy.lighting-profile.v1")
                val name = profile.getString("name").trim()
                require(name.length in 1..40 && name.none(Char::isISOControl))
                val looks = profile.getJSONObject("looks")
                LightingProfile(name, LightingPhase.entries.associateWith { LightCue.fromJson(looks.getJSONObject(it.id)) })
            }.getOrNull()
        }

        val presets = listOf(
            preset("Ultraviolet", 0x401880, 0x432DFC, 0xED43D2, 0x101559),
            preset("Phosphor Terminal", 0x172C2C, 0x14AC66, 0xB8F2B7, 0x0D3024),
            preset("Ember Afterglow", 0x901417, 0xE84E13, 0xFFBC68, 0x2D0A14),
        )

        private fun preset(name: String, heating: Int, ready: Int, draw: Int, end: Int) = LightingProfile(
            name,
            mapOf(
                LightingPhase.Heating to LightCue(heating, 18, 1300),
                LightingPhase.Ready to LightCue(ready, 38, 700),
                LightingPhase.Draw to LightCue(draw, 62, 250),
                LightingPhase.End to LightCue(end, 12, 3000),
            ),
        )
    }
}
