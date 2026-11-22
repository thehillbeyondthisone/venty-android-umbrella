package io.github.orth4x0r.venty.session

import io.github.orth4x0r.venty.ble.HeaterMode
import io.github.orth4x0r.venty.ble.VentyStatus
import org.junit.Assert.*
import org.junit.Test

class DrawLedgerTest {
    @Test fun `seat changes during a draw only affect the next draw`() {
        val ledger = DrawLedger()
        ledger.select(DrawSeat.Amber)
        ledger.beginSession()
        ledger.record(PuffEvent.Started(1_000), true)
        ledger.select(DrawSeat.Teal)
        assertEquals(DrawSeat.Amber, ledger.tray.active?.seat)
        ledger.record(PuffEvent.Ended(3_000, 2_000), false)

        assertEquals(listOf(DrawBall(1_000, 2_000, DrawSeat.Amber)), ledger.tray.balls)
        assertEquals(DrawSeat.Teal, ledger.tray.selected)
        assertNull(ledger.tray.active)
    }

    @Test fun `a nearby reset on the same seat updates one ball`() {
        val ledger = DrawLedger()
        val detector = PuffDetector()
        ledger.select(DrawSeat.Rose)
        feed(detector, ledger, status(116), 0)
        feed(detector, ledger, status(120), 1_000)
        feed(detector, ledger, status(119), 3_000)
        assertEquals(DrawBall(1_000, 2_000, DrawSeat.Rose), ledger.tray.balls.single())
        assertNull(feed(detector, ledger, status(120), 4_000))
        assertEquals(InProgressDraw(1_000, DrawSeat.Rose), ledger.tray.active)
        feed(detector, ledger, status(119), 6_000)
        assertEquals(DrawBall(1_000, 5_000, DrawSeat.Rose), ledger.tray.balls.single())
    }

    @Test fun `a seat handoff prevents merging even inside two seconds`() {
        val ledger = DrawLedger()
        val detector = PuffDetector()
        ledger.select(DrawSeat.Amber)
        feed(detector, ledger, status(116), 0)
        feed(detector, ledger, status(120), 1_000)
        feed(detector, ledger, status(119), 3_000)
        ledger.select(DrawSeat.Teal)
        assertEquals(PuffEvent.Started(4_000), feed(detector, ledger, status(120), 4_000))
        feed(detector, ledger, status(119), 7_000)
        assertEquals(listOf(DrawBall(1_000, 2_000, DrawSeat.Amber), DrawBall(4_000, 3_000, DrawSeat.Teal)), ledger.tray.balls)
    }

    @Test fun `end keeps the completed balls but discards an unfinished draw and clears the seat`() {
        val ledger = DrawLedger()
        ledger.select(DrawSeat.Violet)
        ledger.record(PuffEvent.Started(1_000), true)
        ledger.record(PuffEvent.Ended(3_000, 2_000), false)
        ledger.record(PuffEvent.Started(6_000), true)
        ledger.closeSession()
        assertNull(ledger.tray.selected)
        assertNull(ledger.tray.active)
        assertEquals(DrawBall(1_000, 2_000, DrawSeat.Violet), ledger.tray.balls.single())
        ledger.clear()
        assertEquals(DrawTray(), ledger.tray)
    }

    @Test fun `heater idle after a finished draw does not extend its duration`() {
        val stats = SessionStatsAccumulator(0)
        stats.onPuffStarted(1_000)
        stats.onPuffEnded(2_100)
        stats.finish(60_000)
        assertEquals(2_100L, stats.longestDrawMs)
        assertEquals(2_100L, stats.totalDrawMs)
    }

    private fun status(timer: Int) = VentyStatus(
        currentTenths = null, targetTenths = 1850, boostOffset = 15, superBoostOffset = 15,
        battery = 87, autoOffSeconds = timer, heaterMode = HeaterMode.On, charging = false,
        fahrenheit = false, setpointReached = true,
    )

    private fun feed(detector: PuffDetector, ledger: DrawLedger, status: VentyStatus, at: Long): PuffEvent? {
        val event = detector.onStatus(status, at, allowMerge = ledger.canMerge)
        ledger.record(event, detector.puffing)
        return event
    }
}
