package io.github.orth4x0r.venty.ui.subventy

import android.animation.ValueAnimator
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.orth4x0r.venty.session.DrawBall
import io.github.orth4x0r.venty.session.InProgressDraw
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

internal enum class DrawView(val label: String) {
    Resin("Resin Window"), Mobile("Kinetic Mobile"), People("By person"),
}

internal class SculptureStage(private val view: DrawView) {
    private data class Flight(val ball: DrawBall, val start: Long, val grow: Long) {
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
            records = next
            clearFlight()
            pull = null
            animateUntil = 0L
            if (view == DrawView.Mobile) mobile = KineticMobile(next, previous = mobile.takeIf { arrival })
            else resin = next.mapIndexed { i, ball -> ball.resinMark(i) }
            if (arrival && ValueAnimator.areAnimatorsEnabled()) startFlight(newest, 0L)
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

    fun hit(point: Offset): DrawBall? = marks()
        .asSequence()
        .filterNot { it.ball.startMs == flight?.ball?.startMs }
        .map { it to (it.center - point).getDistance() }
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

    private fun startFlight(ball: DrawBall, grow: Long) {
        flight = Flight(ball, SystemClock.uptimeMillis(), grow)
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
        scale(size.width / SCULPTURE_WIDTH, size.height / SCULPTURE_HEIGHT, pivot = Offset.Zero) {
            sculptureBackdrop(view == DrawView.Mobile)
            val inFlight = flight
            val rig = mobile
            val targets = if (rig != null) {
                val geometry = rig.geometry()
                drawRig(rig, geometry, hiddenId = inFlight?.ball?.startMs)
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
                if (rig == null) drawResin(current, selected, hardened) else drawGlass(current, selected)
            }
            active?.let { draw ->
                val marker = SculptureMark(
                    DrawBall(draw.startMs, elapsedDraw, draw.seat),
                    LiveOrigin,
                    sculptureRadius(if (ValueAnimator.areAnimatorsEnabled()) elapsedDraw else 800L),
                )
                if (rig == null) drawResin(marker, selected = false, hardened = 0f) else drawGlass(marker, selected = false)
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
    val drag = if (view == DrawView.Mobile) Modifier.pointerInput(stage) {
        detectHorizontalDragGestures(
            onDragStart = { point ->
                stage.hit(point.toStage(size))?.let(stage::beginPull)
            },
            onDragEnd = { stage.endPull() },
            onDragCancel = { stage.endPull(cancelled = true) },
        ) { _, dx ->
            stage.pullBy(dx * SCULPTURE_WIDTH / size.width.coerceAtLeast(1))
        }
    } else Modifier
    Canvas(
        Modifier.widthIn(max = 480.dp).aspectRatio(SCULPTURE_WIDTH / SCULPTURE_HEIGHT).clip(Deck.Shape)
            .semantics {
                contentDescription = if (view == DrawView.Mobile)
                    "Hanging sculpture. Each color has a branch. Bead size represents draw duration."
                else "Resin mosaic. Each piece represents one draw and its size represents duration."
                if (onBrowse != null) onClick(label = "Browse draws") { onBrowse(); true }
            }
            .then(drag)
            .pointerInput(stage) { detectTapGestures { stage.hit(it.toStage(size))?.let(inspect) } },
    ) { stage.draw(this, selectedId) }
}

private fun Offset.toStage(size: IntSize) = Offset(
    x * SCULPTURE_WIDTH / size.width.coerceAtLeast(1),
    y * SCULPTURE_HEIGHT / size.height.coerceAtLeast(1),
)

private val LiveOrigin = Offset(SCULPTURE_WIDTH / 2, 49f)
private val SceneSize = Size(SCULPTURE_WIDTH, SCULPTURE_HEIGHT)
private val Specks = Random(412).run { List(225) { Offset(nextFloat() * 384f + 18f, nextFloat() * 295f + 17f) } }
private val Brass = Color(0xFFD9C7A0)
private val Wire = Color(0xA6C1CBC0)

private fun DrawScope.sculptureBackdrop(mobile: Boolean) {
    val background = if (mobile) listOf(Color(0xFF304547), Color(0xFF182C30), Color(0xFF101719))
    else listOf(Color(0xFF44342B), Color(0xFF282320), Color(0xFF141316))
    drawRect(Brush.radialGradient(background, center = Offset(179f, 149f), radius = 249f), size = SceneSize)
    drawRoundRect(
        Color(0x33D2C4A5), Offset(13f, 13f), Size(394f, 304f), CornerRadius(10f),
        style = Stroke(1f),
    )
    for (point in Specks) drawCircle(Color(0x16E9D6BA), .55f, point)
}

private fun DrawScope.drawRig(model: KineticMobile, geometry: List<MobileBranch>, hiddenId: Long?) {
    drawLine(Wire, Offset(model.pivot.x, 0f), model.pivot, 1.5f)
    drawLine(Brass, model.pivot, model.beamPoint(0f), 1.5f)
    drawLine(Color(0xFF0D1517), model.beamPoint(-170f, 61f), model.beamPoint(170f, 61f), 4f)
    drawLine(Brass, model.beamPoint(-170f), model.beamPoint(170f), 2f)
    for (x in -150..150 step 10) drawLine(Wire, model.beamPoint(x.toFloat(), 61f), model.beamPoint(x.toFloat(), 64f), .7f)
    drawCircle(Brass, 3.2f, model.pivot)
    for (branch in geometry) {
        val hooks = branch.marks.map { it.hook }.sortedBy { it.x }
        drawLine(Wire, branch.beamPoint, branch.joint, 1f)
        drawLine(Brass, branch.joint, hooks.first(), 1.3f)
        drawLine(Brass, branch.joint, hooks.last(), 1.3f)
        if (hooks.size > 1) drawLine(Brass, hooks.first(), hooks.last(), 1.5f)
        drawCircle(Brass, 2.9f, branch.joint)
        for (item in branch.marks) {
            drawCircle(Brass, 1.65f, item.hook)
            if (item.mark.ball.startMs != hiddenId) drawLine(Wire, item.hook, item.mark.center, .9f)
        }
    }
    val weight = model.counterweight
    rotate(model.beamAngle * (180f / 3.1415927f), pivot = weight) {
        val top = weight - Offset(15f, 10f)
        val shade = Brush.verticalGradient(listOf(Color(0xFFF2D7A2), Color(0xFFAE813F), Color(0xFF715230)),
            startY = top.y, endY = top.y + 20f)
        drawRoundRect(shade, top, Size(30f, 20f), CornerRadius(4f))
        for (x in -6..6 step 4) drawLine(Color(0xFF695031), weight + Offset(x.toFloat(), -3f), weight + Offset(x.toFloat(), 4f), 1f)
    }
}

private fun DrawScope.drawGlass(mark: SculptureMark, selected: Boolean) {
    val (ball, center, radius) = mark
    val color = seatColor(ball.seat)
    val glow = lerp(color, Color.White, .68f)
    drawCircle(Color(0x83090E0E), radius + 1f, center + Offset(0f, 3f))
    drawCircle(Brush.radialGradient(
        listOf(glow, color, color, lerp(color, Color(0xFF10191B), .72f)),
        center - Offset(radius * .24f, radius * .30f), radius * 1.40f,
    ), radius, center)
    drawCircle(if (selected) glow else color.copy(alpha = .66f), radius, center, style = Stroke(if (selected) 1.7f else .8f))
    val shine = center - Offset(radius * .32f, radius * .36f)
    rotate(-25f, shine) {
        drawOval(glow.copy(alpha = .78f), shine - Offset(radius * .29f, radius * .12f), Size(radius * .58f, radius * .24f))
    }
}

private fun DrawScope.drawResin(mark: SculptureMark, selected: Boolean, hardened: Float) {
    val (ball, center, radius) = mark
    val color = seatColor(ball.seat)
    val pale = lerp(color, Color.White, .66f)
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
    drawPath(shape(center + Offset(2f, 4f)), Color(0x990E0B0C))
    val outline = shape(center)
    drawPath(outline, Brush.radialGradient(listOf(pale, color, lerp(color, Color(0xFF291D24), .63f)),
        center - Offset(radius * .28f, radius * .24f), radius * 1.43f))
    drawPath(outline, pale.copy(alpha = if (selected) .94f else .46f), style = Stroke(if (selected) 1.6f else .85f))
    val bubble = Color.White.copy(alpha = .37f)
    drawCircle(bubble, 1.7f, center + Offset(radius * .19f, -radius * .32f), style = Stroke(.65f))
    drawCircle(bubble, 1f, center + Offset(radius * .39f, -radius * .14f), style = Stroke(.65f))
}
