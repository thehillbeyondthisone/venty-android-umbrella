package io.github.orth4x0r.venty.lighting

import io.github.orth4x0r.venty.ble.VentyStatus
import io.github.orth4x0r.venty.session.PuffDetector

/** Emits edges from device reports. Draw inference is independent of session history. */
internal class LightingPhases {
    private val detector = PuffDetector()
    private var previous: LightingPhase? = null

    fun update(status: VentyStatus?, supportsDraw: Boolean, nowMs: Long): LightingPhase? {
        val next = if (status == null || !status.heaterMode.isHeating) {
            detector.reset()
            null
        } else {
            if (supportsDraw) detector.onStatus(status, nowMs) else detector.reset()
            when {
                !status.setpointReached -> LightingPhase.Heating
                supportsDraw && detector.puffing -> LightingPhase.Draw
                else -> LightingPhase.Ready
            }
        }
        if (previous == next) return null
        previous = next
        return next ?: LightingPhase.End
    }

    fun reset() {
        detector.reset()
        previous = null
    }
}
