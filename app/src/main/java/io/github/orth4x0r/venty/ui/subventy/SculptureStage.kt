package io.github.orth4x0r.venty.ui.subventy

import android.animation.ValueAnimator
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import io.github.orth4x0r.venty.session.DrawBall
import io.github.orth4x0r.venty.session.InProgressDraw
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.random.Random

internal enum class DrawView(val label: String) {
    Resin("Resin Window"), Mobile("Kinetic Mobile"), People("By person"),
}

/** Spread the rig vertically on a phone while keeping every bead and wire at a uniform scale. */
internal class SculptureProjection(width: Float, height: Float) {
    val unit = minOf(width / SCULPTURE_WIDTH, height / SCULPTURE_HEIGHT).coerceAtLeast(.001f)
    private val side = (width / unit - SCULPTURE_WIDTH) / 2f
    private val vertical = height / unit / SCULPTURE_HEIGHT

    fun point(position: Offset) = Offset(position.x + side, position.y * vertical)

    fun mark(mark: SculptureMark, enlargement: Float) =
        mark.copy(center = point(mark.center), radius = mark.radius * enlargement)
}

internal class SculptureStage(private val view: DrawView) {
    private data class Flight(val ball: DrawBall, val start: Long, val grow: Long, val zoomFrom: Float) {
        val end: Long get() = start + grow + 470L
    }

    private data class Pull(val id: Long, var distance: Float = 0f, var speed: Float = 0f, var time: Long)

    private var records: List<DrawBall> = emptyList()
    private var active: InProgressDraw? = null
    private var mobile: KineticMobile? = if (view == DrawView.Mobile) KineticMobile(emptyList()) else null
    private var resin: List<SculptureMark> = emptyList()
    private var flight: Flight? = null
    private var pull: Pull? = null
    private var lastFrame = 0L
    private var animateUntil = 0L
    private var now = 0L
    private var elapsedDraw = 0L
    private var revision by mutableIntStateOf(0)
    private var pluckDirection = 1f
    var moving by mutableStateOf(false)
        private set
    var replayingId by mutableStateOf<Long?>(null)
        private set

    fun show(balls: List<DrawBall>, draw: InProgressDraw?) {
        val next = if (draw == null) balls else balls.filterNot { it.startMs == draw.startMs }
        val newest = next.lastOrNull()
        val arrival = draw == null && newest != null && active?.startMs == newest.startMs
        active = draw
        if (records != next) {
            val previousZoom = enlargement
            records = next
            clearFlight()
            pull = null
            animateUntil = 0L
            if (view == DrawView.Mobile) mobile = KineticMobile(next, previous = mobile.takeIf { arrival })
            else resin = next.mapIndexed { i, ball -> ball.resinMark(i) }
            if (arrival && ValueAnimator.areAnimatorsEnabled()) startFlight(newest, 0L, previousZoom)
        }
        invalidate()
    }

    fun replay(ball: DrawBall) {
        if (flight != null || ball !in records || !ValueAnimator.areAnimatorsEnabled()) return
        pull = null
        startFlight(ball, (620L + ball.durationMs / 8L).coerceAtMost(1_350L))
        invalidate()
    }

    fun pluck(id: Long) {
        if (id == flight?.ball?.startMs || !ValueAnimator.areAnimatorsEnabled()) return
        mobile?.pluck(id, pluckDirection * 1.8f)
        pluckDirection *= -1f
        animateUntil = SystemClock.uptimeMillis() + 5_500L
        invalidate()
    }

    fun hit(pixel: Offset, projection: SculptureProjection): DrawBall? = marks()
        .asSequence()
        .filterNot { it.ball.startMs == flight?.ball?.startMs }
        .map { projection.mark(it, enlargement) }
        .map { it to (it.center - pixel / projection.unit).getDistance() }
        .filter { (mark, distance) -> distance <= maxOf(mark.radius + 5f, 24f) }
        .minByOrNull { it.second }?.first?.ball

    fun beginPull(ball: DrawBall) {
        if (mobile == null || !ValueAnimator.areAnimatorsEnabled()) return
        pull = Pull(ball.startMs, time = SystemClock.uptimeMillis())
        invalidate()
    }

    fun pullBy(dx: Float) {
        val held = pull ?: return
        val time = SystemClock.uptimeMillis()
        held.distance = (held.distance + dx).coerceIn(-52f, 52f)
        held.speed = (dx * 8f / (time - held.time).coerceAtLeast(16L)).coerceIn(-1.2f, 1.2f)
        held.time = time
        mobile?.setPull(held.id, held.distance / 123f)
        invalidate()
    }

    fun endPull(cancelled: Boolean = false) {
        val held = pull ?: return
        pull = null
        val speed = if (!cancelled && SystemClock.uptimeMillis() - held.time < 105L) held.speed else 0f
        mobile?.pluck(held.id, speed)
        animateUntil = SystemClock.uptimeMillis() + 5_500L
        invalidate()
    }

    fun tick() {
        val frame = SystemClock.uptimeMillis()
        val animation = flight
        if (animation != null && frame >= animation.end) {
            mobile?.attach(animation.ball.startMs)
            clearFlight()
            if (mobile != null) animateUntil = frame + 7_600L
        }
        mobile?.step(if (lastFrame == 0L) 0f else (frame - lastFrame) / 1_000f, held = pull?.id)
        lastFrame = frame
        if (flight == null && pull == null && animateUntil != 0L && frame >= animateUntil) {
            mobile?.settle()
            animateUntil = 0L
        }
        invalidate()
    }

    private fun marks() = mobile?.geometry()?.flatMap { branch -> branch.marks.map { it.mark } } ?: resin
    private val enlargement: Float
        get() {
            val target = when (records.size) {
                0, 1 -> if (view == DrawView.Mobile) 2.05f else 2.18f
                2 -> if (view == DrawView.Mobile) 1.12f else 1.23f
                else -> 1f
            }
            val animation = flight ?: return target
            val phase = ((now - animation.start) / 470f).coerceIn(0f, 1f)
            val ease = phase * phase * (3f - 2f * phase)
            return animation.zoomFrom + (target - animation.zoomFrom) * ease
        }

    private fun startFlight(ball: DrawBall, grow: Long, zoomFrom: Float = enlargement) {
        flight = Flight(ball, SystemClock.uptimeMillis(), grow, zoomFrom)
        replayingId = ball.startMs
        mobile?.releaseForArrival(ball.startMs)
    }

    private fun clearFlight() {
        flight = null
        replayingId = null
    }

    private fun invalidate() {
        now = SystemClock.uptimeMillis()
        elapsedDraw = active?.let { (System.currentTimeMillis() - it.startMs).coerceAtLeast(0L) } ?: 0L
        moving = (active != null && ValueAnimator.areAnimatorsEnabled()) || flight != null ||
            pull != null || now < animateUntil
        if (!moving) lastFrame = 0L
        revision++
    }

    fun draw(scope: DrawScope, selectedId: Long?) = with(scope) {
        // Invalidate Canvas drawing directly as the simulation advances, without relaying out its controls.
        @Suppress("UNUSED_VARIABLE") val frame = revision
        val projection = SculptureProjection(size.width, size.height)
        scale(projection.unit, projection.unit, pivot = Offset.Zero) {
            val inFlight = flight
            val rig = mobile
            val targets = if (rig != null) {
                val geometry = rig.geometry()
                drawRig(rig, geometry, projection, hiddenId = inFlight?.ball?.startMs)
                geometry.flatMap { branch -> branch.marks.map { it.mark } }
            } else resin
            for (target in targets) {
                val selected = target.ball.startMs == selectedId
                var hardened = 1f
                val current = if (inFlight?.ball?.startMs == target.ball.startMs) {
                    val grow = if (inFlight.grow == 0L) 1f else ((now - inFlight.start) / inFlight.grow.toFloat()).coerceIn(0f, 1f)
                    val fall = ((now - inFlight.start - inFlight.grow) / 470f).coerceIn(0f, 1f)
                    val time = if (rig == null) fall * fall * (3f - 2f * fall) else fall * fall
                    hardened = ((fall - .55f) / .45f).coerceIn(0f, 1f)
                    val start = if (inFlight.grow == 0L || rig == null) LiveOrigin
                    else Offset(target.center.x + 9f, (target.center.y - 88f).coerceAtLeast(56f))
                    target.copy(center = start + (target.center - start) * time, radius = target.radius * sqrt(.10f + .90f * grow))
                } else target
                val displayed = projection.mark(current, enlargement)
                if (rig == null) drawResin(displayed, selected, hardened) else drawGlass(displayed, selected)
            }
            active?.let { draw ->
                val marker = SculptureMark(
                    DrawBall(draw.startMs, elapsedDraw, draw.seat),
                    LiveOrigin,
                    sculptureRadius(if (ValueAnimator.areAnimatorsEnabled()) elapsedDraw else 800L),
                )
                val displayed = projection.mark(marker, enlargement)
                if (rig == null) drawResin(displayed, selected = false, hardened = 0f) else drawGlass(displayed, selected = false)
            }
        }
    }
}

@Composable
internal fun SculptureCanvas(
    stage: SculptureStage,
    view: DrawView,
    selectedId: Long?,
    onBrowse: (() -> Unit)?,
    onInspect: (DrawBall) -> Unit,
) {
    val inspect by rememberUpdatedState(onInspect)
    LaunchedEffect(stage, stage.moving) {
        while (stage.moving) withFrameNanos { stage.tick() }
    }
    val gestures = Modifier.pointerInput(stage, view) {
        if (view != DrawView.Mobile) {
            detectTapGestures { position ->
                stage.hit(position, SculptureProjection(size.width.toFloat(), size.height.toFloat()))?.let(inspect)
            }
        } else awaitEachGesture {
            val down = awaitFirstDown()
            val projection = SculptureProjection(size.width.toFloat(), size.height.toFloat())
            // Lock onto the bead before the pointer crosses slop. A quick flick can cross its entire width.
            val bead = stage.hit(down.position, projection) ?: return@awaitEachGesture
            var pulling = false
            try {
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (change.isConsumed) break
                    val offset = change.position - down.position
                    if (!change.pressed) {
                        if (pulling) stage.endPull()
                        else if (offset.getDistance() <= viewConfiguration.touchSlop) inspect(bead)
                        change.consume()
                        break
                    }
                    if (pulling) {
                        stage.pullBy(change.positionChange().x / projection.unit)
                        change.consume()
                    } else if (abs(offset.x) > viewConfiguration.touchSlop && abs(offset.x) >= abs(offset.y)) {
                        stage.beginPull(bead)
                        stage.pullBy(offset.x / projection.unit)
                        pulling = true
                        change.consume()
                    } else if (abs(offset.y) > viewConfiguration.touchSlop) {
                        break
                    }
                }
            } finally {
                if (pulling) stage.endPull(cancelled = true)
            }
        }
    }
    Canvas(
        Modifier.fillMaxSize()
            .semantics {
                contentDescription = if (view == DrawView.Mobile)
                    "Hanging sculpture. Each color has a branch. Bead size represents draw duration."
                else "Resin mosaic. Each piece represents one draw and its size represents duration."
                if (onBrowse != null) onClick(label = "Browse draws") { onBrowse(); true }
            }
            .then(gestures),
    ) { stage.draw(this, selectedId) }
}

private val LiveOrigin = Offset(SCULPTURE_WIDTH / 2, 43f)
private val Brass = Color(0xFFD9C7A0)
private val Wire = Color(0xA6C1CBC0)

private fun DrawScope.drawRig(
    model: KineticMobile,
    geometry: List<MobileBranch>,
    projection: SculptureProjection,
    hiddenId: Long?,
) {
    fun beam(x: Float, y: Float = 59f) = projection.point(model.beamPoint(x, y))
    val pivot = projection.point(model.pivot)
    drawLine(Wire, Offset(pivot.x, 0f), pivot, 1.35f)
    drawLine(Brass, pivot, beam(0f), 1.3f)
    val left = beam(-170f)
    val right = beam(170f)
    drawLine(Color(0xB440855F), left + Offset(0f, 3f), right + Offset(0f, 3f), 5f)
    drawLine(Color(0xFF3D2D21), left, right, 5.5f)
    drawLine(Brush.horizontalGradient(listOf(Color(0xFFE0AC68), Color(0xFFFFE8AD), Color(0xFFC58F5C)),
        startX = left.x, endX = right.x), left - Offset(0f, 1f), right - Offset(0f, 1f), 2.4f)
    for (x in -150..150 step 10) {
        val notch = beam(x.toFloat()) + Offset(0f, 3f)
        drawLine(Wire.copy(alpha = .48f), notch, notch + Offset(0f, if (x % 50 == 0) 5f else 2.5f), .65f)
    }
    for (end in listOf(left, right)) {
        drawCircle(Color(0xFF4C3321), 4.7f, end)
        drawCircle(Brass, 2.1f, end - Offset(0f, 1f))
    }
    drawCircle(Color(0xFF694A30), 5f, pivot)
    drawCircle(Brass, 2.8f, pivot)
    for (branch in geometry) {
        val hooks = branch.marks.map { projection.point(it.hook) }.sortedBy { it.x }
        val joint = projection.point(branch.joint)
        drawLine(Wire, projection.point(branch.beamPoint), joint, 1f)
        drawLine(Brass, joint, hooks.first(), 1.3f)
        drawLine(Brass, joint, hooks.last(), 1.3f)
        if (hooks.size > 1) drawLine(Brass, hooks.first(), hooks.last(), 1.45f)
        drawCircle(Brass, 2.9f, joint)
        for (item in branch.marks) {
            val hook = projection.point(item.hook)
            drawCircle(Brass, 1.65f, hook)
            if (item.mark.ball.startMs != hiddenId) drawLine(Wire, hook, projection.point(item.mark.center), .9f)
        }
    }
    val weight = projection.point(model.counterweight)
    val slope = atan2(right.y - left.y, right.x - left.x)
    rotate(slope * (180f / 3.1415927f), pivot = weight) {
        val top = weight - Offset(15f, 10f)
        val shade = Brush.verticalGradient(listOf(Color(0xFFF2D7A2), Color(0xFFAE813F), Color(0xFF715230)),
            startY = top.y, endY = top.y + 20f)
        drawRoundRect(shade, top, Size(30f, 20f), CornerRadius(4f))
        drawRoundRect(Color(0xA7FFE4AD), top, Size(30f, 20f), CornerRadius(4f), style = Stroke(.65f))
        for (x in -6..6 step 4) drawLine(Color(0xFF695031), weight + Offset(x.toFloat(), -3f), weight + Offset(x.toFloat(), 4f), 1f)
    }
}

private fun DrawScope.drawGlass(mark: SculptureMark, selected: Boolean) {
    val (ball, center, radius) = mark
    val color = seatColor(ball.seat)
    val glow = lerp(color, Color(0xFFF7FFF8), .76f)
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = .28f), Color.Transparent), center, radius * 2.35f),
        radius * 2.35f, center)
    drawCircle(Color(0x83090E0E), radius + 1f, center + Offset(0f, 3f))
    drawCircle(Brush.radialGradient(
        listOf(glow, color, lerp(color, Color(0xFF123538), .24f), lerp(color, Color(0xFF10191B), .86f)),
        center - Offset(radius * .24f, radius * .30f), radius * 1.40f,
    ), radius, center)
    drawCircle(if (selected) glow else color.copy(alpha = .75f), radius, center, style = Stroke(if (selected) 1.7f else .8f))
    val bevel = radius * .86f
    drawArc(color.copy(alpha = .86f), 19f, 117f, false,
        center - Offset(bevel, bevel), Size(bevel * 2, bevel * 2), style = Stroke(1f))
    val shine = center - Offset(radius * .32f, radius * .36f)
    rotate(-25f, shine) {
        drawOval(glow.copy(alpha = .78f), shine - Offset(radius * .29f, radius * .12f), Size(radius * .58f, radius * .24f))
        drawOval(Color.White.copy(alpha = .66f), shine - Offset(radius * .21f, radius * .08f), Size(radius * .34f, radius * .07f))
    }
}

private fun DrawScope.drawResin(mark: SculptureMark, selected: Boolean, hardened: Float) {
    val (ball, center, radius) = mark
    val color = seatColor(ball.seat)
    val pale = lerp(color, Color(0xFFFFECD3), .68f)
    fun shape(at: Offset): Path {
        val corners = resinOutline(at, radius, ball.startMs, hardened)
        val first = (corners.last() + corners.first()) / 2f
        val path = Path().apply { moveTo(first.x, first.y) }
        corners.forEachIndexed { index, corner ->
            val middle = (corner + corners[(index + 1) % corners.size]) / 2f
            path.quadraticTo(corner.x, corner.y, middle.x, middle.y)
        }
        path.close()
        return path
    }
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = .28f), Color.Transparent), center, radius * 2.45f),
        radius * 2.45f, center)
    drawPath(shape(center + Offset(2f, 5f)), Color(0x990E0B0C))
    val outline = shape(center)
    drawPath(outline, Brush.radialGradient(listOf(pale, color, lerp(color, Color(0xFF291D24), .8f)),
        center - Offset(radius * .39f, radius * .49f), radius * 1.63f))
    clipPath(outline) {
        val depth = center + Offset(radius * .17f, radius * .36f)
        drawCircle(Brush.radialGradient(listOf(Color(0x91422B62), Color.Transparent), depth, radius), radius, depth)
        for (layer in 0..3) {
            val sweep = radius * (.76f + layer * .11f)
            drawArc(pale.copy(alpha = .22f - layer * .037f), 14f + layer * 7f, 117f, false,
                center - Offset(sweep, sweep), Size(sweep * 2, sweep * 1.9f), style = Stroke(.75f))
        }
        val reflection = center - Offset(radius * .27f, radius * .40f)
        rotate(-28f, reflection) {
            drawOval(Brush.radialGradient(listOf(Color(0xE6FFF6DB), Color.Transparent), reflection, radius * .37f),
                reflection - Offset(radius * .39f, radius * .13f), Size(radius * .78f, radius * .26f))
        }
        val inclusions = Random(ball.startMs)
        repeat(if (radius > 22f) 12 else 5) {
            val pore = center + Offset(inclusions.nextFloat() - .5f, inclusions.nextFloat() - .5f) * radius * 1.56f
            val poreRadius = (.65f + inclusions.nextFloat() * 1.7f) * (radius / 27f).coerceIn(.48f, 1.4f)
            drawCircle(pale.copy(alpha = .51f), poreRadius, pore, style = Stroke(.55f))
            drawCircle(Color(0x76FFF7DF), poreRadius * .24f, pore - Offset(poreRadius * .27f, poreRadius * .32f))
        }
    }
    drawPath(outline, pale.copy(alpha = if (selected) .94f else .59f), style = Stroke(if (selected) 1.6f else .9f))
}
