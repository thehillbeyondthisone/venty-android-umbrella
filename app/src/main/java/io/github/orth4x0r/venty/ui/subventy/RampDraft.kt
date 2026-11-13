package io.github.orth4x0r.venty.ui.subventy

import io.github.orth4x0r.venty.session.RampPlan

/** Validate all fields before an explicit Start action can write to a device. */
internal fun parseRampDraft(start: String, peak: String, step: String, seconds: String, holdMinutes: String,
    range: IntRange): RampPlan? {
    val a = start.toIntOrNull() ?: return null
    val b = peak.toIntOrNull() ?: return null
    val c = step.toIntOrNull() ?: return null
    val d = seconds.toLongOrNull() ?: return null
    val e = holdMinutes.toLongOrNull() ?: return null
    if (a !in range || b !in range || b < a || c !in RampPlan.MIN_STEP_C..RampPlan.MAX_STEP_C ||
        d !in RampPlan.MIN_STEP_EVERY_MS / 1000..RampPlan.MAX_STEP_EVERY_MS / 1000 ||
        e !in 0..RampPlan.MAX_HOLD_MS / 60000) return null
    return RampPlan(a, c, d * 1000, b, e * 60000)
}
