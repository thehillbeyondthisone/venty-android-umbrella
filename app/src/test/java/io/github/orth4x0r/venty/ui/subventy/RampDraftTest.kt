package io.github.orth4x0r.venty.ui.subventy

import org.junit.Assert.*
import org.junit.Test

class RampDraftTest {
    @Test fun validPlanConvertsSecondsAndMinutesWithoutStartingAnything() {
        val p = parseRampDraft("180", "200", "2", "60", "3", 40..210)!!
        assertEquals(180, p.startC)
        assertEquals(200, p.peakC)
        assertEquals(60_000L, p.stepEveryMs)
        assertEquals(180_000L, p.holdMs)
    }
    @Test fun incompleteAndOverflowingInputsCannotEnableStart() {
        assertNull(parseRampDraft("", "200", "2", "60", "3", 40..210))
        assertNull(parseRampDraft("180", "200", "2", "99999999999999999999999", "3", 40..210))
        assertNull(parseRampDraft("180", "200", "2", "60", "-1", 40..210))
    }
    @Test fun descendingAndOutOfDeviceRangePlansAreRejected() {
        assertNull(parseRampDraft("200", "180", "2", "60", "3", 40..210))
        assertNull(parseRampDraft("39", "180", "2", "60", "3", 40..210))
        assertNull(parseRampDraft("180", "230", "2", "60", "3", 40..210))
    }
    @Test fun volcanoUpperLimitIsAcceptedWithoutUsingVentyLimit() {
        assertNotNull(parseRampDraft("180", "230", "2", "60", "3", 40..230))
    }
    @Test fun invalidStepIntervalsAndHoldDoNotGetSilentlyCoerced() {
        assertNull(parseRampDraft("180", "200", "0", "60", "3", 40..210))
        assertNull(parseRampDraft("180", "200", "11", "60", "3", 40..210))
        assertNull(parseRampDraft("180", "200", "2", "29", "3", 40..210))
        assertNull(parseRampDraft("180", "200", "2", "1201", "3", 40..210))
        assertNull(parseRampDraft("180", "200", "2", "60", "16", 40..210))
    }
    @Test fun boundaryValuesAndZeroHoldAreSupported() {
        assertNotNull(parseRampDraft("40", "210", "1", "30", "0", 40..210))
        assertNotNull(parseRampDraft("210", "210", "10", "1200", "15", 40..210))
    }
}
