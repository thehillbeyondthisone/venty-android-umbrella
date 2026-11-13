package io.github.orth4x0r.venty.session

import org.junit.Assert.*
import org.junit.Test

class SessionTrackingGateTest {
    @Test fun joiningAnAlreadyHotDeviceDoesNotInventASession() {
        val gate = SessionTrackingGate()
        assertFalse(gate.allows(true))
        assertTrue(gate.allows(false))
        assertTrue(gate.allows(true))
    }
    @Test fun pausedPeriodsAreNotRecordedOrBackfilled() {
        val gate = SessionTrackingGate()
        gate.allows(false)
        assertTrue(gate.allows(true))
        gate.setPaused(true)
        assertFalse(gate.allows(true))
        assertFalse(gate.allows(false))
        gate.setPaused(false)
        assertFalse(gate.allows(true))
        assertTrue(gate.allows(false))
        assertTrue(gate.allows(true))
    }
    @Test fun aNewConnectionDoesNotDuplicateAnAlreadyHotSession() {
        val gate = SessionTrackingGate()
        gate.allows(false)
        assertTrue(gate.allows(true))
        gate.resetConnection()
        assertFalse(gate.allows(true))
        assertTrue(gate.allows(false))
        assertTrue(gate.allows(true))
    }
    @Test fun repeatedConsentUpdatesDoNotInterruptAnEligibleSession() {
        val gate = SessionTrackingGate()
        gate.allows(false)
        gate.setPaused(false)
        assertTrue(gate.allows(true))
        gate.setPaused(false)
        assertTrue(gate.allows(true))
    }
}
