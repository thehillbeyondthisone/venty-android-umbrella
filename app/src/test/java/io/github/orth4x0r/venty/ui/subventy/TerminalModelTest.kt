package io.github.orth4x0r.venty.ui.subventy

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneOffset

class TerminalModelTest {
    private val venty = TermContext(connected = true, targetTenths = 1850, effectiveTenths = 1850, minTenths = 400, maxTenths = 2100,
        battery = 80, zone = ZoneOffset.UTC, nowMs = 0)

    @Test fun heatWithTargetSetsTargetThenTurnsHeaterOn() {
        val r = Terminal.run("heat 190", venty)
        assertEquals(listOf(TermAction.SetTarget(1900), TermAction.Heater(true)), r.actions)
    }

    @Test fun temperaturesFollowTheDeviceUnitUnlessExplicit() {
        val f = venty.copy(fahrenheit = true)
        assertEquals(listOf(TermAction.SetTarget(1900)), Terminal.run("set 374", f).actions)
        assertEquals(listOf(TermAction.SetTarget(1900)), Terminal.run("set 190c", f).actions)
        assertEquals(listOf(TermAction.SetTarget(1900)), Terminal.run("set 374f", venty).actions)
    }

    @Test fun outOfRangeTargetsAreRejectedNotClamped() {
        for (cmd in listOf("set 211", "set 39", "heat 999", "set", "set hot")) {
            val r = Terminal.run(cmd, venty)
            assertTrue(cmd, r.actions.isEmpty())
            assertEquals(cmd, Tone.Err, r.lines.single().tone)
        }
        assertTrue(Terminal.run("+", venty.copy(targetTenths = 2100)).actions.isEmpty())
        assertTrue(Terminal.run("- 5", venty.copy(targetTenths = 420)).actions.isEmpty())
    }

    @Test fun stepsMoveByDisplayDegrees() {
        assertEquals(listOf(TermAction.SetTarget(1860)), Terminal.run("+", venty).actions)
        assertEquals(listOf(TermAction.SetTarget(1800)), Terminal.run("down 5", venty).actions)
    }

    @Test fun deviceCommandsNeedACarrier() {
        val off = venty.copy(connected = false)
        for (cmd in listOf("heat", "off", "set 180", "+", "boost", "pump", "find")) {
            assertTrue(cmd, Terminal.run(cmd, off).actions.isEmpty())
        }
        assertEquals(listOf(TermAction.Connect), Terminal.run("connect", off).actions)
        assertEquals(listOf(TermAction.Demo(volcano = true)), Terminal.run("demo volcano", off).actions)
    }

    @Test fun capabilitiesAreRespected() {
        val volcano = venty.copy(hasBoost = false, hasPump = true, battery = null, maxTenths = 2300)
        assertTrue(Terminal.run("boost", volcano).actions.isEmpty())
        assertTrue(Terminal.run("find", volcano).actions.isEmpty())
        assertEquals(listOf(TermAction.Pump(true)), Terminal.run("pump", volcano).actions)
        assertEquals(listOf(TermAction.SetTarget(2250)), Terminal.run("set 225", volcano).actions)
        assertTrue(Terminal.run("pump", venty).actions.isEmpty())
    }

    @Test fun secretsAreReportedOnlyWhenFound() {
        assertNull(Terminal.run("help", venty).secret)
        assertEquals("whoami", Terminal.run("whoami", venty).secret)
        assertEquals("planet", Terminal.run("hack the planet", venty).secret)
        assertNull(Terminal.run("hack", venty).secret)
        assertEquals("xyzzy", Terminal.run("XYZZY", venty).secret)
        assertNull(Terminal.run("frobnicate", venty).secret)
    }

    @Test fun helpNeverListsSecrets() {
        val help = Terminal.run("help", venty).lines.joinToString("\n") { it.text }
        for (s in listOf("whoami", "fortune", "xyzzy", "sudo", "planet", "uptime")) assertFalse(s, help.contains(s))
    }

    @Test fun handlesAreSanitisedAndBounded() {
        assertEquals(listOf(TermAction.SetHandle("n1ght_jar")), Terminal.run("handle n1ght_jar!!", venty).actions)
        assertEquals(12, (Terminal.run("handle abcdefghijklmnopq", venty).actions.single() as TermAction.SetHandle).handle.length)
        assertTrue(Terminal.run("handle", venty).actions.isEmpty())
    }

    @Test fun modemIsACosmeticCommand() {
        assertEquals(listOf(TermAction.Sound(true)), Terminal.run("modem on", venty).actions)
        assertTrue(Terminal.run("modem loud", venty).actions.isEmpty())
    }

    @Test fun heatAlreadyOnStillOnlyAsksForHeaterOn() {
        // The screen leaves a running boost alone when "heat" arrives with the heater already on.
        assertEquals(listOf(TermAction.Heater(true)), Terminal.run("heat", venty.copy(heating = true, mode = TermMode.Boost)).actions)
    }

    @Test fun macrosFollowConnectionAndCapabilities() {
        assertTrue("connect" in macroCommands(venty.copy(connected = false)))
        assertTrue("heat" in macroCommands(venty))
        assertTrue("off" in macroCommands(venty.copy(heating = true)))
        assertFalse("boost" in macroCommands(venty.copy(hasBoost = false)))
        assertTrue("pump on" in macroCommands(venty.copy(hasBoost = false, hasPump = true)))
        // Every macro is a command the parser understands.
        for (m in macroCommands(venty) + macroCommands(venty.copy(connected = false)))
            assertFalse(m, Terminal.run(m, venty).lines.any { it.text.contains("command not found") })
    }

    @Test fun barRoundsAndClamps() {
        assertEquals("[----------]", Terminal.bar(0, 10))
        assertEquals("[#####-----]", Terminal.bar(50, 10))
        assertEquals("[##########]", Terminal.bar(140, 10))
    }

    @Test fun clockFormatsHoursPastADay() {
        assertEquals("26:01:05", Terminal.clock(java.time.Duration.ofSeconds(26 * 3600 + 65)))
    }
}

class FeedTest {
    private val base = FeedSnapshot("connected", "Venty", targetTenths = 1850, effectiveTenths = 1850, battery = 50)

    @Test fun connectingReadsAsADialUp() {
        val lines = Feed.diff(FeedSnapshot("idle", "Venty"), FeedSnapshot("connecting", "Venty")).map { it.text }
        assertEquals(listOf("ATZ", "OK", "ATDT VENTY"), lines)
        assertTrue(Feed.diff(FeedSnapshot("connecting", "Venty"), base).any { it.text.contains("has joined") })
        assertTrue(Feed.diff(base, FeedSnapshot("idle", "Venty")).single().text.contains("has quit"))
    }

    @Test fun repeatedSnapshotsAreSilent() {
        assertTrue(Feed.diff(base, base).isEmpty())
    }

    @Test fun heaterReachedAndPacketsAreNarrated() {
        val on = base.copy(heating = true)
        assertTrue(Feed.diff(base, on).first().text.startsWith("> exec heat.exe"))
        assertTrue(Feed.diff(on, on.copy(reached = true)).single().text.contains("target locked"))
        assertEquals("<<< packet #1 received", Feed.diff(on, on.copy(puffs = 1)).single().text)
        assertTrue(Feed.diff(on, base).single().text.contains("exited"))
    }

    @Test fun targetChangesCollapseIntoOneLine() {
        var buffer = emptyList<TermLine>()
        var prev = base
        for (t in 1860..1900 step 10) {
            val next = base.copy(targetTenths = t)
            buffer = Feed.append(buffer, Feed.diff(prev, next)); prev = next
        }
        assertEquals(listOf("target <- 190°C"), buffer.map { it.text })
    }

    @Test fun batteryWarnsOnceAtEachThresholdAndNotWhileCharging() {
        assertEquals("!!! battery at 20%", Feed.diff(base.copy(battery = 21), base.copy(battery = 20)).single().text)
        assertTrue(Feed.diff(base.copy(battery = 20), base.copy(battery = 19)).isEmpty())
        assertTrue(Feed.diff(base.copy(battery = 21, charging = true), base.copy(battery = 20, charging = true)).isEmpty())
    }

    @Test fun bufferIsBounded() {
        val lines = (1..500).map { TermLine("$it") }
        assertEquals(200, Feed.append(emptyList(), lines).size)
        assertEquals("500", Feed.append(emptyList(), lines).last().text)
    }
}

class MoonTest {
    @Test fun knownPhases() {
        // 2024-04-08 18:21 UTC new moon (total eclipse); 2024-04-23 23:49 UTC full moon.
        assertEquals("new", Moon.at(1712600460000L).name)
        assertEquals("full", Moon.at(1713916140000L).name)
        assertTrue(Moon.at(1713916140000L).illumination >= 98)
        assertTrue(Moon.at(1712600460000L).illumination <= 2)
    }
}
