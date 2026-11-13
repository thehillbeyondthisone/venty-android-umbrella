package io.github.orth4x0r.venty.ui.subventy

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * After-hours look: a phosphor monitor in a dark room. Pure Compose (no Android types) so the
 * terminal surfaces can also be rendered off-device for design review.
 */
enum class Phosphor(val label: String, val bright: Color, val dim: Color, val glow: Color) {
    Amber("amber", Color(0xFFFFB347), Color(0xFF9C6A2A), Color(0xFFFF8A1E)),
    Green("green", Color(0xFF5CFF8A), Color(0xFF2F8F52), Color(0xFF1EFF6A)),
    Cyan("cyan", Color(0xFF8CC8FF), Color(0xFF4A73A0), Color(0xFF3FA2FF));

    companion object {
        fun parse(name: String?): Phosphor? = entries.firstOrNull { it.name.equals(name, true) || it.label.equals(name, true) }
    }
}

/** Night palette for the window chrome. Semantic heat colours stay separate from the phosphor. */
internal object Night {
    val Room = Color(0xFF04050A)
    val CaptionStart = Color(0xFF241A4E)
    val CaptionEnd = Color(0xFF6A58B8)
    val Panel = Color(0xFF0B2238)
    val PanelHi = Color(0xFF123553)
    val Workspace = Color(0xFF07080B)
    val Tree = Color(0xFF06070A)
    val Selection = Color(0xFF173C66)
    val Text = Color(0xFFDCDCE6)
    val Muted = Color(0xFF8E95A6)
    val Edge = Color(0xFF3A3F55)
    /** The one warm light in the room: an element that is actively heating. */
    val Heat = Color(0xFFFF7A3D)
    val Ready = Color(0xFF7CFFB0)
    val Alarm = Color(0xFFFF5470)
}

internal val LocalPhosphor = staticCompositionLocalOf { Phosphor.Amber }
/** The terminal face. Android supplies the bundled VT323; previews fall back to monospace. */
internal val LocalTermFont = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }
/** Mirrors the platform "remove animations" setting; flicker, drift and scrambles stop when true. */
internal val LocalReducedMotion = staticCompositionLocalOf { false }

/** VT323 is 0.4 em per column; used to fit a fixed number of columns to the available width. */
internal const val TERM_ADVANCE = 0.4f

@Composable internal fun termStyle(size: TextUnit, color: Color = LocalPhosphor.current.bright, glow: Boolean = true): TextStyle {
    val phosphor = LocalPhosphor.current
    return TextStyle(fontFamily = LocalTermFont.current, fontSize = size, lineHeight = size * 1.05f, color = color,
        shadow = if (glow) Shadow(phosphor.glow.copy(alpha = .55f), Offset.Zero, size.value * .45f) else null)
}

/** One line of terminal text. Never wraps: terminals clip, they don't reflow. */
@Composable internal fun Term(text: String, size: TextUnit = 18.sp, color: Color = LocalPhosphor.current.bright,
    modifier: Modifier = Modifier, glow: Boolean = true, wrap: Boolean = false) {
    Text(text, modifier, style = termStyle(size, color, glow), softWrap = wrap,
        maxLines = if (wrap) Int.MAX_VALUE else 1, overflow = if (wrap) TextOverflow.Clip else TextOverflow.Clip)
}

/**
 * Scanlines, a soft vignette and (motion permitting) a faint refresh band rolling down the glass.
 * Drawn over the content so every surface reads as the same monitor.
 */
@Composable internal fun CrtGlass(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val reduced = LocalReducedMotion.current
    val roll = if (reduced) null else rememberInfiniteTransition(label = "crt").animateFloat(0f, 1f,
        infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "roll")
    Box(modifier.drawWithContent {
        drawContent()
        val pitch = 3.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawRect(Color.Black.copy(alpha = .16f), Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, pitch / 2.6f))
            y += pitch
        }
        roll?.let { r ->
            val band = size.height * .18f
            val top = (size.height + band) * r.value - band
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = .028f), Color.Transparent),
                startY = top, endY = top + band), Offset(0f, top), androidx.compose.ui.geometry.Size(size.width, band))
        }
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .42f)),
            center = center, radius = size.maxDimension * .78f))
    }, content = content)
}

/** A characters-per-line → font size fit for the bundled face. */
internal fun fitColumns(widthPx: Float, columns: Int, density: Float, fontScale: Float): TextUnit =
    (widthPx / (columns * TERM_ADVANCE) / density / fontScale).coerceIn(11f, 21f).sp
