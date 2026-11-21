package io.github.orth4x0r.venty.ui.subventy

import io.github.orth4x0r.venty.session.DrawBall
import io.github.orth4x0r.venty.session.DrawSeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class KineticMobileTest {
    private val lopsided = listOf(
        DrawBall(1L, 1_100L, DrawSeat.Amber),
        DrawBall(2L, 1_800L, DrawSeat.Amber),
        DrawBall(3L, 1_200L, DrawSeat.Amber),
        DrawBall(4L, 2_600L, DrawSeat.Amber),
        DrawBall(5L, 1_500L, DrawSeat.Amber),
        DrawBall(6L, 3_700L, DrawSeat.Amber),
        DrawBall(7L, 2_600L, DrawSeat.Teal),
        DrawBall(8L, 2_700L, DrawSeat.Rose),
        DrawBall(9L, 1_100L, DrawSeat.Rose),
    )

    @Test
    fun counterweightMovesAfterTheBeadLandsAndTheBeamReturnsToLevel() {
        val model = KineticMobile(lopsided)
        val fullLoad = model.counterweight.x
        assertTrue(fullLoad > model.pivot.x)
        assertEquals(0f, model.beamAngle, .001f)

        model.releaseForArrival(6L)
        repeat(150) { model.step(.016f, held = null) }
        val unloaded = model.counterweight.x
        assertTrue(unloaded < fullLoad - 15f)

        model.attach(6L)
        repeat(30) { model.step(.016f, held = null) }
        assertTrue(abs(model.beamAngle) > .04f)
        assertTrue(model.counterweight.x < fullLoad - 12f)

        repeat(550) { model.step(.016f, held = null) }
        assertEquals(fullLoad, model.counterweight.x, .25f)
        assertEquals(0f, model.beamAngle, .01f)
    }

    @Test
    fun aPluckOnTheQuietBranchMovesTheHeavyBranch() {
        val model = KineticMobile(lopsided)
        fun heavyCenter() = model.geometry().first().marks.last().mark.center
        val rest = heavyCenter()
        model.pluck(8L, 1.8f)
        var movement = 0f
        repeat(75) {
            model.step(.016f, held = null)
            movement = maxOf(movement, (rest - heavyCenter()).getDistance())
        }
        assertTrue(movement > 2f)
        model.settle()
        assertEquals(rest, heavyCenter())
    }

    @Test
    fun crowdedBranchKeepsEveryRecordedBeadInViewAtRest() {
        val others = listOf(DrawSeat.Teal, DrawSeat.Rose, DrawSeat.Violet, null)
        val crowded = (1L..8L).map { DrawBall(it, 1_500L + it * 200L, DrawSeat.Amber) } +
            others.mapIndexed { index, seat -> DrawBall(index + 9L, 2_100L, seat) }
        val marks = KineticMobile(crowded).geometry().flatMap(MobileBranch::marks).map(MobileMark::mark)
        assertEquals(crowded.size, marks.size)
        assertEquals(crowded.toSet(), marks.map(SculptureMark::ball).toSet())
        assertEquals(marks.size, marks.map(SculptureMark::center).distinct().size)
        assertTrue(marks.all { (ball, center, radius) ->
            ball.durationMs > 0 && center.x in radius..(SCULPTURE_WIDTH - radius) &&
                center.y in radius..(SCULPTURE_HEIGHT - radius)
        })
    }
}
