package io.github.orth4x0r.venty.lighting

import io.github.orth4x0r.venty.ble.HeaterMode
import io.github.orth4x0r.venty.ble.VentyStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LightingBridgeTest {
    @Test
    fun `Venty reports produce one cue per edge including draw and release`() {
        val phases = LightingPhases()
        val heating = status(120).copy(setpointReached = false)
        assertNull(phases.update(null, true, 0))
        assertEquals(LightingPhase.Heating, phases.update(heating, true, 1000))
        assertNull(phases.update(heating, true, 1500))
        assertEquals(LightingPhase.Ready, phases.update(status(120), true, 2000))
        assertNull(phases.update(status(119), true, 3000))
        assertEquals(LightingPhase.Draw, phases.update(status(120), true, 4000))
        assertNull(phases.update(status(120), true, 4500))
        assertEquals(LightingPhase.Ready, phases.update(status(119), true, 5000))
        assertEquals(LightingPhase.End, phases.update(null, true, 5500))
        assertNull(phases.update(null, true, 6000))
    }

    @Test
    fun `Volcano timer changes never trigger draw and cooling ends the scene`() {
        val phases = LightingPhases()
        assertEquals(LightingPhase.Ready, phases.update(status(119), false, 0))
        assertNull(phases.update(status(120), false, 1000))
        assertEquals(LightingPhase.End, phases.update(status(120).copy(heaterMode = HeaterMode.Off), false, 2000))
    }

    @Test
    fun `pause resets the phase and never emits a stale draw or end after resume`() {
        val phases = LightingPhases()
        phases.update(status(119), true, 0)
        phases.update(status(120), true, 1000)
        phases.reset()
        assertNull(phases.update(null, true, 2000))
        assertEquals(LightingPhase.Ready, phases.update(status(120), true, 3000))
    }

    @Test
    fun `webhook accepts Pi and HTTPS base URLs without accepting credentials or paths`() {
        val id = "subventy_test123456789012345678901"
        assertEquals("$DEFAULT_HA_ADDRESS/api/webhook/$id", homeAssistantWebhook(DEFAULT_HA_ADDRESS, id).toString())
        assertNotNull(homeAssistantWebhook("https://ha.example.test/", id))
        assertNull(homeAssistantWebhook("http://ha.example.test", id))
        assertNull(homeAssistantWebhook("http://192.168.1.170.example.test", id))
        assertNull(homeAssistantWebhook("https://ha.example.test/api", id))
        assertNull(homeAssistantWebhook("https://user@ha.example.test", id))
        assertNull(homeAssistantWebhook(DEFAULT_HA_ADDRESS, "$id/extra"))
        assertNull(homeAssistantWebhook(DEFAULT_HA_ADDRESS, "short"))
    }

    @Test
    fun `a look validates color and power bounds and rejects aggressive fades`() {
        assertEquals(LightCue(0x1A2B3C, 0, 3000), LightCue.fromInput("#1a2b3c", "0", "3000"))
        assertNull(LightCue.fromInput("#FFF", "100", "500"))
        assertNull(LightCue.fromInput("#FFFFFF", "101", "500"))
        assertNull(LightCue.fromInput("#FFFFFF", "20", "199"))
        assertNull(LightCue.fromInput("#FFFFFF", "20", "5001"))
    }

    private fun status(timer: Int) = VentyStatus(
        currentTenths = null, targetTenths = 1850, boostOffset = 15, superBoostOffset = 15,
        battery = 85, autoOffSeconds = timer, heaterMode = HeaterMode.On, charging = false,
        fahrenheit = false, setpointReached = true,
    )
}
