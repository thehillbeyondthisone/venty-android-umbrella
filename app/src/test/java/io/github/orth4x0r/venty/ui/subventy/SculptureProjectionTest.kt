package io.github.orth4x0r.venty.ui.subventy

import androidx.compose.ui.geometry.Offset
import io.github.orth4x0r.venty.session.DrawBall
import io.github.orth4x0r.venty.session.DrawSeat
import org.junit.Assert.assertEquals
import org.junit.Test

class SculptureProjectionTest {
    @Test
    fun portraitUsesFullStageHeightWithUniformBeadSize() {
        val projection = SculptureProjection(360f, 640f)
        val bead = SculptureMark(DrawBall(500L, 2_000L, DrawSeat.Amber), Offset(210f, 165f), 20f)
        val mark = projection.mark(bead, 2f)

        assertEquals(180f, mark.center.x * projection.unit, .001f)
        assertEquals(320f, mark.center.y * projection.unit, .001f)
        assertEquals(40f, mark.radius, .001f)
        assertEquals(640f, projection.point(Offset(210f, 330f)).y * projection.unit, .001f)
    }

    @Test
    fun landscapeRetainsTheRigCenteredWithinTheViewport() {
        val projection = SculptureProjection(760f, 262f)
        val center = projection.point(Offset(210f, 165f)) * projection.unit

        assertEquals(380f, center.x, .001f)
        assertEquals(131f, center.y, .001f)
        assertEquals(262f / 330f, projection.unit, .001f)
    }
}
