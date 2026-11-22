package io.github.orth4x0r.venty.ui.subventy

import androidx.compose.ui.geometry.Offset
import io.github.orth4x0r.venty.session.DrawBall
import io.github.orth4x0r.venty.session.DrawSeat
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal const val SCULPTURE_WIDTH = 420f
internal const val SCULPTURE_HEIGHT = 330f
internal const val SCULPTURE_PAGE_SIZE = 12

internal val sculptureSeats = DrawSeat.entries + listOf<DrawSeat?>(null)

internal data class SculptureMark(val ball: DrawBall, val center: Offset, val radius: Float)

internal data class MobileMark(val mark: SculptureMark, val hook: Offset)

internal data class MobileBranch(val beamPoint: Offset, val joint: Offset, val marks: List<MobileMark>)

internal fun sculptureRadius(durationMs: Long): Float =
    (10.5f * sqrt(durationMs.coerceAtLeast(0) / 1_000f)).coerceIn(5f, 26f)

/** A short, fixed-step model of a beam, its branches, and the suspended beads. */
internal class KineticMobile(balls: List<DrawBall>, previous: KineticMobile? = null) {
    private class Swing(var angle: Float = 0f, var velocity: Float = 0f)

    private class Bead(val ball: DrawBall, val x: Float, val length: Float, val radius: Float) {
        val swing = Swing()
        val mass = (ball.durationMs / 1_000f * .67f).coerceIn(.08f, 6f)
    }

    private class Branch(val seat: DrawSeat?, val x: Float, val beads: List<Bead>) {
        val swing = Swing()
        var mass = 0f
        var balance = 0f
        var pull = 0f
    }

    val pivot = Offset(SCULPTURE_WIDTH / 2, 30f)
    private val beam = Swing()
    private val branches: List<Branch>
    private var suspended: Long? = null
    private var balanceDelay = 0f
    private var remainder = 0f
    private var slider = 0f
    private var torque = 0f
    private var hanging = 0f

    val beamAngle: Float get() = beam.angle
    val counterweight: Offset get() = beamPoint(slider)

    init {
        val groups = balls.groupBy { it.seat }
        val seats = sculptureSeats.filter { groups.containsKey(it) }
        val span = 352f / seats.size.coerceAtLeast(1)
        branches = seats.mapIndexed { index, seat ->
            val group = groups.getValue(seat)
            val branchWidth = (span - 12f).coerceAtMost(245f)
            val columns = (branchWidth / 29f).toInt().coerceIn(2, 6)
            val rows = (group.size + columns - 1) / columns
            val rowStep = (91f / (rows - 1).coerceAtLeast(1)).coerceAtMost(48f)
            val beads = group.chunked(columns).flatMapIndexed { row, chunk ->
                val pitch = (branchWidth / chunk.size).coerceAtMost(44f)
                chunk.mapIndexed { column, ball ->
                    val x = (column - (chunk.size - 1) / 2f) * pitch
                    val length = 64f + row * rowStep + column % 2 * 22f
                    Bead(ball, x, length, minOf(sculptureRadius(ball.durationMs), pitch * .42f))
                }
            }
            Branch(seat, ((index + .5f) - seats.size / 2f) * span, beads)
        }
        settle()
        if (previous != null) {
            beam.angle = previous.beam.angle
            beam.velocity = previous.beam.velocity
            slider = previous.slider
            for (branch in branches) {
                val oldBranch = previous.branches.firstOrNull { it.seat == branch.seat } ?: continue
                branch.swing.angle = oldBranch.swing.angle
                branch.swing.velocity = oldBranch.swing.velocity
                for (bead in branch.beads) {
                    val old = oldBranch.beads.firstOrNull { it.ball.startMs == bead.ball.startMs } ?: continue
                    bead.swing.angle = old.swing.angle
                    bead.swing.velocity = old.swing.velocity
                }
            }
        }
    }

    fun releaseForArrival(id: Long) {
        suspended = id
        balanceDelay = 0f
    }

    fun attach(id: Long) {
        suspended = null
        val branch = branches.firstOrNull { arm -> arm.beads.any { it.ball.startMs == id } } ?: return
        val bead = branch.beads.first { it.ball.startMs == id }
        beam.velocity += bead.mass * branch.x / 365f
        branch.swing.velocity += bead.mass * (bead.x / 125f + .16f)
        bead.swing.velocity += if (bead.x < 0f) .56f else -.56f
        balanceDelay = .95f
    }

    fun setPull(id: Long, angle: Float) {
        val hook = bead(id) ?: return
        hook.swing.angle = angle.coerceIn(-.38f, .38f)
        hook.swing.velocity = 0f
    }

    fun pluck(id: Long, velocity: Float) {
        if (id == suspended) return
        val swing = bead(id)?.swing ?: return
        swing.velocity = (swing.velocity + velocity).coerceIn(-3.4f, 3.4f)
    }

    fun step(seconds: Float, held: Long?) {
        remainder += seconds.coerceIn(0f, .036f)
        while (remainder >= Step) {
            advance(held)
            remainder -= Step
        }
    }

    fun settle() {
        updateLoads()
        slider = balancedSlider()
        beam.angle = balancedBeam()
        beam.velocity = 0f
        remainder = 0f
        balanceDelay = 0f
        for (branch in branches) {
            branch.swing.angle = branch.balance
            branch.swing.velocity = 0f
            branch.beads.forEach {
                it.swing.angle = 0f
                it.swing.velocity = 0f
            }
        }
    }

    fun beamPoint(x: Float, y: Float = RailHeight): Offset = pivot + rotated(x, y, beam.angle)

    fun geometry(): List<MobileBranch> = branches.map { branch ->
        val connection = beamPoint(branch.x)
        val joint = connection + Offset(0f, 15f)
        val marks = branch.beads.map { bead ->
            val hook = joint + rotated(bead.x, 17f + abs(bead.x) * .055f, branch.swing.angle)
            val center = hook + Offset(bead.length * sin(bead.swing.angle), bead.length * cos(bead.swing.angle))
            MobileMark(SculptureMark(bead.ball, center, bead.radius), hook)
        }
        MobileBranch(connection, joint, marks)
    }

    private fun bead(id: Long): Bead? = branches.firstNotNullOfOrNull { branch ->
        branch.beads.firstOrNull { it.ball.startMs == id }
    }

    private fun updateLoads() {
        torque = 0f
        hanging = BrassMass
        for (branch in branches) {
            // The painted hardware has its own weight, even with a bead in flight.
            var mass = 3f
            var side = 0f
            var drop = 95f
            var pull = 0f
            for (bead in branch.beads) {
                if (bead.ball.startMs == suspended) continue
                mass += bead.mass
                side += bead.mass * bead.x
                drop += bead.mass * (25f + bead.length * .23f)
                pull += bead.mass * (sin(bead.swing.angle) * 4.25f + bead.swing.velocity * .24f)
            }
            branch.mass = mass
            branch.balance = atan2(side, drop)
            branch.pull = pull
            torque += mass * branch.x
            hanging += mass
        }
    }

    private fun balancedSlider() = (-torque / BrassMass).coerceIn(-151f, 151f)

    private fun balancedBeam() = atan2(torque + BrassMass * slider, 350f + hanging * RailHeight)

    private fun advance(held: Long?) {
        updateLoads()
        if (balanceDelay > 0f) balanceDelay -= Step
        else slider += ((balancedSlider() - slider) * 4.2f).coerceIn(-69f, 69f) * Step
        val coupling = branches.sumOf { branch ->
            ((branch.swing.angle - branch.balance) * 2.4f + branch.swing.velocity * .21f + branch.pull * .19f).toDouble()
        }.toFloat()
        val upper = 15f * sin(balancedBeam() - beam.angle) - Damping * beam.velocity + coupling
        integrate(beam, upper, .34f)
        for (branch in branches) {
            val stiffness = 192f / (5f + branch.mass)
            val force = stiffness * sin(branch.balance - branch.swing.angle) - Damping * 1.13f * branch.swing.velocity +
                branch.pull * .31f - upper * .25f
            integrate(branch.swing, force, .42f)
            for (bead in branch.beads) {
                if (bead.ball.startMs == suspended || bead.ball.startMs == held) continue
                val gravity = -2010f / bead.length * sin(bead.swing.angle)
                val drag = Damping * .98f * bead.swing.velocity
                integrate(bead.swing, gravity - drag - upper * .12f - force * .24f, .45f)
            }
        }
    }

    private fun integrate(body: Swing, acceleration: Float, limit: Float) {
        body.velocity = (body.velocity + acceleration * Step).coerceIn(-3.7f, 3.7f)
        val angle = body.angle + body.velocity * Step
        body.angle = angle.coerceIn(-limit, limit)
        if (angle != body.angle) body.velocity *= -.13f
    }

    private companion object {
        const val Step = 1f / 120f
        const val RailHeight = 59f
        const val Damping = 1.8f
        const val BrassMass = 11f
    }
}

private fun rotated(x: Float, y: Float, angle: Float) =
    Offset(x * cos(angle) - y * sin(angle), x * sin(angle) + y * cos(angle))

internal fun DrawBall.resinMark(index: Int): SculptureMark =
    SculptureMark(this, resinCenters[index], (sculptureRadius(durationMs) * 1.25f).coerceAtMost(29f))

private val resinCenters = listOf(
    Offset(210f, 156f), Offset(271f, 179f), Offset(154f, 182f),
    Offset(170f, 106f), Offset(225f, 222f), Offset(266f, 110f),
    Offset(115f, 137f), Offset(127f, 237f), Offset(322f, 142f),
    Offset(300f, 232f), Offset(189f, 270f), Offset(283f, 278f),
)

internal fun resinOutline(center: Offset, radius: Float, seed: Long, irregularity: Float): List<Offset> =
    List(8) { index ->
        val angle = (index / 8f * (2 * PI).toFloat()) + (seed % 11) * .23f
        val variation = ((seed ushr (index * 5)) and 15L) / 15f - .5f
        val reach = radius * (1.10f + variation * .38f * irregularity)
        center + Offset(cos(angle) * reach, sin(angle) * reach)
    }
