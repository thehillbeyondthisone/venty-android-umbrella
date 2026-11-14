package io.github.orth4x0r.venty.ui.subventy

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * The after-hours pieces add no new look: they draw with the SubSeven tokens in [S7]. These are the
 * few extra semantic colours the session console needs, picked to sit on the charcoal workspace.
 */

/** Mirrors the platform "remove animations" setting; the screensaver drift and spinners stop when true. */
internal val LocalReducedMotion = staticCompositionLocalOf { false }

internal object Console {
    val Box = Color(0xFF070B16)      // same well as S7Input
    val BoxEdge = Color(0xFF80859D)
    val Info = Color(0xFFA298D3)     // tree group lavender
    val Ok = Color(0xFF8FD6A0)
    val Warn = Color(0xFFE6C27A)
    val Err = Color(0xFFFF8C8C)
}

internal fun toneColor(tone: Tone): Color = when (tone) {
    Tone.Echo -> Color.White
    Tone.Out -> S7.Text
    Tone.Info -> Console.Info
    Tone.Ok -> Console.Ok
    Tone.Warn -> Console.Warn
    Tone.Err -> Console.Err
    Tone.Irc -> S7.Link
    Tone.Dim -> S7.Muted
}
