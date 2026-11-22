package io.github.orth4x0r.venty.ui.subventy

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

internal data class LiveExposure(val radius: Float, val light: Float, val phase: Float)

/** The recorded clock shapes the light. Its intensity levels off during an extended draw. */
internal fun exposureAt(elapsedMs: Long, motion: Boolean): LiveExposure {
    val seconds = if (motion) elapsedMs.coerceIn(0L, 30_000L) / 1_000f else 1.15f
    val spread = 1f - exp(-seconds / 1.28f)
    return LiveExposure(
        radius = 12f + 41f * spread,
        light = .48f + .52f * spread,
        phase = seconds * 2.42f,
    )
}

private const val Tau = (2 * PI).toFloat()
private val FilmViolet = Color(0xFFC481F4)
private val CoolLight = Color(0xFF79EDF0)

internal fun DrawScope.drawResinLight(viewport: Size, focus: Offset, color: Color, exposure: LiveExposure, fade: Float = 1f) {
    val power = exposure.light * fade
    val height = viewport.height
    val limit = minOf(viewport.width * .35f, 175f)
    val source = Offset(focus.x, height * .86f)
    val violet = lerp(FilmViolet, color, .28f)
    val glass = lerp(color, Color(0xFFFFF1E4), .61f)
    val halo = limit * 1.72f
    lightPool(focus, halo, violet, .40f * power)
    lightPool(source, limit * 1.2f, color, .56f * power)
    lightPool(focus + Offset(0f, height * .14f), limit * 1.35f, color, .24f * power)

    // Ribbons bend around the forming hemisphere, then converge on the bottom light source.
    fun fiber(progress: Float, offset: Float): Offset {
        val sweep = exposure.phase + progress * 7.8f + offset
        val envelope = sin(PI.toFloat() * progress).coerceAtLeast(0f)
        val width = limit * envelope * (.44f + .48f * exposure.light)
        val x = focus.x + sin(sweep) * width + cos(sweep * .73f) * width * .29f
        val end = focus.y - height * .22f
        val y = source.y + (end - source.y) * progress + cos(sweep) * width * .19f
        return Offset(x, y)
    }
    for (index in 0..14) {
        val offset = index.toFloat() / 15 * Tau
        val line = Path()
        for (step in 0..38) {
            val point = fiber(step / 38f, offset)
            if (step == 0) line.moveTo(point.x, point.y) else line.lineTo(point.x, point.y)
        }
        val pigment = if (index % 4 == 0) glass else if (index % 2 == 0) color else violet
        val ribbon = Brush.verticalGradient(
            0f to Color.Transparent,
            .19f to pigment.copy(alpha = .33f * power),
            .43f to pigment.copy(alpha = .77f * power),
            .67f to pigment.copy(alpha = .31f * power),
            .92f to pigment.copy(alpha = .54f * power),
            1f to Color.Transparent,
            startY = (focus.y - height * .22f).coerceAtLeast(0f), endY = source.y + 7f,
        )
        drawPath(line, ribbon, style = Stroke(if (index % 5 == 0) 5.0f else 2.5f), alpha = .19f, blendMode = BlendMode.Screen)
        drawPath(line, ribbon, style = Stroke(if (index % 5 == 0) 1.4f else .72f), blendMode = BlendMode.Screen)
    }
    for (index in 0 until 49) {
        val t = (index * .4173f + exposure.phase * (.037f + index % 4 * .004f)) % 1f
        val point = fiber(t, index * 2.39996f)
        val shine = sin(t * PI.toFloat()) * power
        drawCircle((if (index % 5 == 0) glass else color).copy(alpha = shine * .84f),
            if (index % 7 == 0) 1.5f else .67f, point, blendMode = BlendMode.Screen)
    }

    val lip = limit * .64f
    for (index in 0..4) {
        val radius = lip * (.47f + index * .18f)
        drawOval(color.copy(alpha = power * (.52f - index * .075f)),
            source - Offset(radius, radius * .24f), Size(radius * 2, radius * .48f),
            style = Stroke(if (index == 1) 1.3f else .64f), blendMode = BlendMode.Screen)
    }
    drawOval(
        Brush.radialGradient(listOf(glass.copy(alpha = .8f * power), Color.Transparent), source, lip),
        source - Offset(lip, 3f), Size(lip * 2, 6f), blendMode = BlendMode.Screen,
    )
}

internal fun DrawScope.drawMobileLight(
    viewport: Size,
    mount: Offset,
    focus: Offset,
    color: Color,
    exposure: LiveExposure,
    fade: Float = 1f,
) {
    val power = exposure.light * fade
    val ice = lerp(color, CoolLight, .48f)
    val metal = lerp(color, Color(0xFFFFF1D0), .69f)
    val glowRadius = minOf(viewport.width * .38f, 175f)
    lightPool(mount, glowRadius * .62f, ice, .41f * power)
    lightPool(focus, glowRadius * 1.2f, color, .52f * power)
    lightPool(focus + Offset(0f, viewport.height * .17f), glowRadius, ice, .21f * power)

    val wire = Brush.linearGradient(
        listOf(metal.copy(alpha = .86f * power), ice.copy(alpha = .55f * power), metal.copy(alpha = power)),
        start = mount, end = focus,
    )
    drawLine(wire, mount, focus, 11f, alpha = .08f, cap = StrokeCap.Round, blendMode = BlendMode.Screen)
    drawLine(wire, mount, focus, 4.4f, alpha = .30f, cap = StrokeCap.Round, blendMode = BlendMode.Screen)
    drawLine(wire, mount, focus, 1.5f, cap = StrokeCap.Round, blendMode = BlendMode.Screen)
    val filament = focus - mount
    val curve = Offset((focus.x - mount.x) * .11f, -viewport.height * .035f)
    val amplitude = (37f + exposure.radius * 1.37f)
    for (index in 0..7) {
        val fraction = index / 7f
        val alpha = power * (.15f + fraction * .26f)
        val strand = Path()
        for (step in 0..28) {
            val t = step / 28f
            val x = sin(exposure.phase + t * 6.6f + fraction * Tau) * amplitude *
                sin(t * PI.toFloat()).coerceAtLeast(0f)
            val point = mount + filament * t + curve * (t * (1f - t)) + Offset(x, 0f)
            if (step == 0) strand.moveTo(point.x, point.y) else strand.lineTo(point.x, point.y)
        }
        drawPath(strand, ice.copy(alpha = alpha), style = Stroke(if (index % 3 == 0) 1.05f else .62f),
            blendMode = BlendMode.Screen)
    }
    for (i in 0..4) {
        val span = amplitude * (.88f + i * .31f)
        val pivot = focus + Offset(0f, exposure.radius * .15f)
        rotate(i * 22f - 36f, pivot) {
            drawArc(
                if (i % 2 == 0) ice.copy(alpha = power * .76f) else color.copy(alpha = power * .71f),
                exposure.phase * (28f + i * 2f) + i * 51f,
                84f + i * 4f, false, pivot - Offset(span, span * .46f),
                Size(span * 2, span * .92f), style = Stroke(if (i == 0) 1.25f else .84f), blendMode = BlendMode.Screen,
            )
        }
    }
    for (i in 0 until 25) {
        val angle = i * 2.39996f + exposure.phase * (if (i % 2 == 0) .22f else -.14f)
        val span = amplitude * (.87f + (i % 7) * .16f)
        val at = focus + Offset(cos(angle) * span, sin(angle) * span * .7f)
        val tint = if (i % 3 == 0) metal else ice
        drawCircle(tint.copy(alpha = power * (.29f + (i % 4) * .16f)), if (i % 5 == 0) 1.6f else .67f, at,
            blendMode = BlendMode.Screen)
    }
    drawCircle(metal.copy(alpha = power), 3f, mount, blendMode = BlendMode.Screen)
}

internal fun DrawScope.drawStandby(viewport: Size, focus: Offset, color: Color, mobile: Boolean) {
    val radius = minOf(viewport.width * .32f, 120f)
    val port = if (mobile) focus else Offset(focus.x, viewport.height * .86f)
    lightPool(port, radius, color, .12f)
    if (mobile) {
        drawCircle(color.copy(alpha = .26f), 11f, port, style = Stroke(.75f))
        drawCircle(color.copy(alpha = .66f), 2f, port)
    } else {
        for (scale in listOf(.42f, .62f, .81f)) {
            val width = radius * scale
            drawOval(color.copy(alpha = .2f), port - Offset(width, width * .18f),
                Size(width * 2, width * .36f), style = Stroke(.75f))
        }
        drawLine(color.copy(alpha = .54f), port - Offset(11f, 0f), port + Offset(11f, 0f), 1.4f, StrokeCap.Round)
    }
}

private fun DrawScope.lightPool(center: Offset, radius: Float, color: Color, strength: Float) {
    drawCircle(
        Brush.radialGradient(
            0f to color.copy(alpha = strength),
            .28f to color.copy(alpha = strength * .53f),
            .73f to color.copy(alpha = strength * .09f),
            1f to Color.Transparent,
            center = center, radius = radius.coerceAtLeast(1f),
        ), radius, center, blendMode = BlendMode.Screen,
    )
}
