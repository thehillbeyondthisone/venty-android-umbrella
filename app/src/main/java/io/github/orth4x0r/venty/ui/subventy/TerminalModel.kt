package io.github.orth4x0r.venty.ui.subventy

import io.github.orth4x0r.venty.ui.Temperature
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt

/*
 * The after-hours terminal: a command line and an event feed over the existing controller.
 * Pure Kotlin so the parser, feed and calendar maths are unit-testable. Commands only *describe*
 * device actions; the screen performs them through the controller, the same calls the buttons use.
 */

enum class Tone { Echo, Out, Info, Ok, Warn, Err, Irc, Dim }

/** [key] lets a repeated kind of line (e.g. "target") replace itself instead of flooding the feed. */
data class TermLine(val text: String, val tone: Tone = Tone.Out, val key: String? = null)

enum class TermMode { Normal, Boost, Super }

sealed interface TermAction {
    data class SetTarget(val tenthsC: Int) : TermAction
    data class Heater(val on: Boolean) : TermAction
    data class Mode(val mode: TermMode) : TermAction
    data class Pump(val on: Boolean) : TermAction
    data object StopRamp : TermAction
    data object Connect : TermAction
    data object Disconnect : TermAction
    data class Demo(val volcano: Boolean) : TermAction
    data object Find : TermAction
    /** Open a page or window by name: ramp, log, notes, atlas, options, loot, icebreaker, settings. */
    data class Open(val target: String) : TermAction
    data object Clear : TermAction
    data class SetHandle(val handle: String) : TermAction
    data class Sound(val on: Boolean) : TermAction
    data object Lock : TermAction
}

/** A snapshot of everything a command may need to read. Temperatures are tenths of °C. */
data class TermContext(
    val connected: Boolean,
    val demo: Boolean = false,
    val deviceName: String = "Venty",
    val kindName: String = "Venty",
    val heating: Boolean = false,
    val mode: TermMode = TermMode.Normal,
    val reached: Boolean = false,
    val fahrenheit: Boolean = false,
    val targetTenths: Int = 1850,
    val effectiveTenths: Int = 1850,
    val minTenths: Int = 400,
    val maxTenths: Int = 2100,
    val liveTenths: Int? = null,
    val battery: Int? = null,
    val charging: Boolean = false,
    val hasBoost: Boolean = true,
    val hasPump: Boolean = false,
    val pumpOn: Boolean = false,
    val rampRunning: Boolean = false,
    val sessionStartMs: Long? = null,
    val puffs: Int? = null,
    val handle: String = DEFAULT_HANDLE,
    val nowMs: Long = System.currentTimeMillis(),
    val zone: ZoneId = ZoneId.systemDefault(),
    val lootCount: Int = 0,
    val secretsFound: Set<String> = emptySet(),
)

data class TermResult(val lines: List<TermLine>, val actions: List<TermAction> = emptyList(), val secret: String? = null)

const val DEFAULT_HANDLE = "guest"

/** Short commands for the macro strip; each is just typed into the parser. */
fun macroCommands(ctx: TermContext): List<String> = buildList {
    if (!ctx.connected) { add("connect"); add("demo") }
    else {
        add(if (ctx.heating) "off" else "heat")
        add("-"); add("+")
        if (ctx.hasBoost) { add("boost"); add("super") }
        if (ctx.hasPump) add(if (ctx.pumpOn) "pump off" else "pump on")
    }
    add("status"); add("icebreaker"); add("loot"); add("help")
}

fun prompt(handle: String) = "${handle.ifBlank { DEFAULT_HANDLE }}@subventy:~$"

object Terminal {
    private val HELP = listOf(
        "commands",
        "  status        heat [temp]",
        "  off           set <temp>",
        "  + / -  [n]    normal|boost|super",
        "  pump [on|off] ramp [stop]",
        "  connect       disconnect",
        "  demo [volcano]  find",
        "  log  notes  atlas  loot",
        "  icebreaker    lock",
        "  handle <name> modem <on|off>",
        "  clear         settings",
        "temps use the device's unit;",
        "add c or f to be explicit: heat 375f",
    )

    fun run(input: String, ctx: TermContext): TermResult {
        val raw = input.trim()
        if (raw.isEmpty()) return TermResult(emptyList())
        val words = raw.lowercase(Locale.ROOT).split(Regex("\\s+"))
        val cmd = words[0]
        val arg = words.getOrNull(1)
        val unit = Temperature.unit(ctx.fahrenheit)
        fun out(vararg lines: String, tone: Tone = Tone.Out) = lines.map { TermLine(it, tone) }
        fun needLink(block: () -> TermResult): TermResult =
            if (ctx.connected) block() else TermResult(out("ERR no carrier. type connect or demo.", tone = Tone.Err))
        fun secret(id: String, vararg lines: String, actions: List<TermAction> = emptyList()) =
            TermResult(out(*lines, tone = Tone.Irc), actions, secret = id)

        return when (cmd) {
            "help", "?", "man" -> if (arg == "secrets") secret("help-secrets", "there are no secrets.", "(there are secrets.)")
                else TermResult(HELP.mapIndexed { i, s -> TermLine(s, if (i == 0) Tone.Info else Tone.Out) })
            "status", "stat" -> TermResult(status(ctx))
            "heat", "on", "run", "exec" -> needLink {
                if (arg == null) return@needLink TermResult(out("> exec heat.exe", tone = Tone.Ok), listOf(TermAction.Heater(true)))
                val t = parseTemp(arg, ctx) ?: return@needLink rangeError(ctx)
                TermResult(out("> exec heat.exe --target ${show(t, ctx)}", tone = Tone.Ok),
                    listOf(TermAction.SetTarget(t), TermAction.Heater(true)))
            }
            "off", "kill", "stop", "halt" -> needLink {
                TermResult(out("> kill heat.exe", tone = Tone.Warn), listOf(TermAction.Heater(false)))
            }
            "set", "target", "temp", "t" -> needLink {
                val t = arg?.let { parseTemp(it, ctx) } ?: return@needLink rangeError(ctx)
                TermResult(out("target <- ${show(t, ctx)}", tone = Tone.Ok), listOf(TermAction.SetTarget(t)))
            }
            "+", "-", "up", "down" -> needLink {
                val n = (arg?.toIntOrNull() ?: 1).coerceIn(1, 50)
                val sign = if (cmd == "+" || cmd == "up") 1 else -1
                val display = Temperature.toDisplay(ctx.targetTenths, ctx.fahrenheit) + sign * n
                val t = Temperature.fromDisplay(display, ctx.fahrenheit)
                if (t < ctx.minTenths || t > ctx.maxTenths) rangeError(ctx)
                else TermResult(out("target <- $display°$unit", tone = Tone.Ok), listOf(TermAction.SetTarget(t)))
            }
            "normal", "boost", "super", "superboost" -> needLink {
                if (!ctx.hasBoost) return@needLink TermResult(out("ERR ${ctx.kindName.lowercase()} has no boost.", tone = Tone.Err))
                val mode = when (cmd) { "normal" -> TermMode.Normal; "boost" -> TermMode.Boost; else -> TermMode.Super }
                TermResult(out("> heat.exe --${mode.name.lowercase()}", tone = Tone.Ok), listOf(TermAction.Mode(mode)))
            }
            "pump" -> needLink {
                if (!ctx.hasPump) return@needLink TermResult(out("ERR no pump on this device.", tone = Tone.Err))
                val on = when (arg) { "on" -> true; "off" -> false; else -> !ctx.pumpOn }
                TermResult(out("> pump ${if (on) "on" else "off"}", tone = Tone.Ok), listOf(TermAction.Pump(on)))
            }
            "ramp" -> if (arg == "stop") needLink {
                if (!ctx.rampRunning) TermResult(out("no ramp is running.", tone = Tone.Dim))
                else TermResult(out("> ramp stop. heater unchanged.", tone = Tone.Warn), listOf(TermAction.StopRamp))
            } else TermResult(out("opening ramp editor...", tone = Tone.Dim), listOf(TermAction.Open("ramp")))
            "connect", "dial", "atdt" -> if (ctx.connected) TermResult(out("already connected to ${ctx.deviceName}.", tone = Tone.Dim))
                else TermResult(out("ATZ", "OK", tone = Tone.Dim), listOf(TermAction.Connect), secret = if (cmd == "atdt") "atdt" else null)
            "disconnect", "hangup", "ath", "ath0" -> if (!ctx.connected) TermResult(out("nothing to hang up.", tone = Tone.Dim))
                else TermResult(out("ATH0", tone = Tone.Dim), listOf(TermAction.Disconnect), secret = if (cmd.startsWith("ath")) "ath" else null)
            "+++" -> secret("+++", "OK", "(escaped to command mode. ath0 hangs up.)")
            "demo" -> if (ctx.connected) TermResult(out("disconnect first.", tone = Tone.Dim))
                else TermResult(out("dialing the demo line...", tone = Tone.Dim), listOf(TermAction.Demo(volcano = arg == "volcano")))
            "find", "locate" -> needLink {
                if (!ctx.hasBoost) TermResult(out("ERR find is a venty feature.", tone = Tone.Err))
                else TermResult(out("> buzz ${ctx.deviceName.lowercase()}", tone = Tone.Ok), listOf(TermAction.Find))
            }
            "log", "sessions" -> TermResult(out("cd /var/log/sessions", tone = Tone.Dim), listOf(TermAction.Open("log")))
            "notes" -> TermResult(out("cd ~/notes", tone = Tone.Dim), listOf(TermAction.Open("notes")))
            "atlas" -> TermResult(out("cd ~/atlas", tone = Tone.Dim), listOf(TermAction.Open("atlas")))
            "loot" -> TermResult(out("cd ~/loot", tone = Tone.Dim), listOf(TermAction.Open("loot")))
            "settings", "options", "config" -> TermResult(out("opening device properties...", tone = Tone.Dim), listOf(TermAction.Open("settings")))
            "icebreaker", "crack", "icebreaker.exe", "./icebreaker" -> TermResult(out("loading icebreaker.exe...", tone = Tone.Dim), listOf(TermAction.Open("icebreaker")))
            "lock", "screensaver", "vapor.scr" -> TermResult(emptyList(), listOf(TermAction.Lock))
            "clear", "cls" -> TermResult(emptyList(), listOf(TermAction.Clear))
            "handle", "nick" -> {
                val name = raw.split(Regex("\\s+")).getOrNull(1)?.filter { it.isLetterOrDigit() || it in "_-." }?.take(12)
                if (name.isNullOrEmpty()) TermResult(out("usage: handle <name>  (letters, digits, _ - .)", tone = Tone.Dim))
                else TermResult(out("*** ${ctx.handle} is now known as $name", tone = Tone.Irc), listOf(TermAction.SetHandle(name)))
            }
            "modem", "sound" -> when (arg) {
                "on" -> TermResult(out("modem speaker on. it will sing when you connect.", tone = Tone.Ok), listOf(TermAction.Sound(true)))
                "off" -> TermResult(out("modem speaker off.", tone = Tone.Ok), listOf(TermAction.Sound(false)))
                else -> TermResult(out("usage: modem on|off  (default off)", tone = Tone.Dim))
            }
            // Things callers typed that did something. Never listed; recorded only once found.
            "whoami" -> secret("whoami", ctx.handle)
            "uptime" -> secret("uptime", uptime(ctx))
            "date", "time" -> secret("date", DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy", Locale.ENGLISH)
                .format(Instant.ofEpochMilli(ctx.nowMs).atZone(ctx.zone)))
            "fortune" -> secret("fortune", FORTUNES[((ctx.nowMs / 60_000) % FORTUNES.size).toInt()])
            "moon" -> { val m = Moon.at(ctx.nowMs); secret("moon", "moon: ${m.name}, ${m.illumination}% lit") }
            "finger" -> secret("finger", "login: ${ctx.handle}   tty: ble0",
                if (ctx.connected) "on since: ${ctx.deviceName}" else "never logged in to a device", "plan: stay up. stay kind.")
            "ping" -> if (ctx.connected) secret("ping", "PONG from ${ctx.deviceName.lowercase()} // link up")
                else secret("ping", "ping: no route to host")
            "sudo", "su" -> secret("sudo", "${ctx.handle} is not in the sudoers file.", "This incident will be reported.")
            "hack", "hack-the-planet" -> if (raw.lowercase(Locale.ROOT).contains("planet")) secret("planet", "HACK THE PLANET!", "(the sysop sighs, then smiles.)")
                else TermResult(out("hack what?", tone = Tone.Dim))
            "xyzzy", "plugh" -> secret("xyzzy", "Nothing happens.")
            "ls", "dir" -> secret("ls", "heat.exe  icebreaker.exe  vapor.scr", "loot/  notes/  atlas/  motd")
            "cd" -> secret("cd", "you're already home.")
            "rm", "del", "format" -> secret("rm", "nice try. your notes stay where they are.")
            "exit", "logout", "quit", "bye" -> secret("exit", "NO CARRIER", "(just kidding. the line stays open.)")
            "motd", "cat" -> if (cmd == "cat" && arg != "motd") TermResult(out("cat: ${arg ?: ""}: try loot to read files", tone = Tone.Dim))
                else secret("motd", *motd(ctx).toTypedArray())
            "42" -> secret("42", "the answer. what was the question?")
            "coffee", "make", "brew" -> secret("teapot", "418 I'm a teapot")
            "hello", "hi", "hey", "sup" -> secret("hello", "hi ${ctx.handle}. ${greeting(ctx)}")
            "secrets" -> if (ctx.secretsFound.isEmpty()) TermResult(out("nothing found yet. or is there.", tone = Tone.Dim))
                else TermResult(out("found ${ctx.secretsFound.size}:", tone = Tone.Info) +
                    ctx.secretsFound.sorted().chunked(3).map { TermLine("  " + it.joinToString("  "), Tone.Irc) })
            else -> TermResult(out("$cmd: command not found. try help", tone = Tone.Err))
        }
    }

    fun status(ctx: TermContext): List<TermLine> {
        val unit = Temperature.unit(ctx.fahrenheit)
        if (!ctx.connected) return listOf(TermLine("link    : no carrier", Tone.Warn), TermLine("type connect, or demo to try it.", Tone.Dim))
        return buildList {
            add(TermLine("link    : ${ctx.deviceName}${if (ctx.demo) " (demo)" else ""}", Tone.Info))
            add(TermLine("target  : ${Temperature.toDisplay(ctx.targetTenths, ctx.fahrenheit)}°$unit" +
                if (ctx.effectiveTenths != ctx.targetTenths) " -> ${Temperature.toDisplay(ctx.effectiveTenths, ctx.fahrenheit)}°$unit ${ctx.mode.name.lowercase()}" else ""))
            ctx.liveTenths?.let { add(TermLine("live    : ${Temperature.toDisplay(it, ctx.fahrenheit)}°$unit")) }
            add(TermLine("heater  : " + when { !ctx.heating -> "idle"; ctx.reached -> "locked"; else -> "warming" },
                if (ctx.reached) Tone.Ok else Tone.Out))
            ctx.battery?.let { add(TermLine("battery : ${bar(it, 10)} $it%${if (ctx.charging) " +chg" else ""}", if (it <= 15) Tone.Warn else Tone.Out)) }
            if (ctx.hasPump) add(TermLine("pump    : ${if (ctx.pumpOn) "on" else "off"}"))
            if (ctx.rampRunning) add(TermLine("ramp    : running"))
        }
    }

    private fun parseTemp(arg: String, ctx: TermContext): Int? {
        val m = Regex("^(\\d{2,3})(°?[cf])?$").find(arg) ?: return null
        val n = m.groupValues[1].toInt()
        val f = when (m.groupValues[2].lastOrNull()) { 'c' -> false; 'f' -> true; else -> ctx.fahrenheit }
        val t = Temperature.fromDisplay(n, f)
        return t.takeIf { it in ctx.minTenths..ctx.maxTenths }
    }

    private fun show(tenths: Int, ctx: TermContext) = "${Temperature.toDisplay(tenths, ctx.fahrenheit)}°${Temperature.unit(ctx.fahrenheit)}"

    private fun rangeError(ctx: TermContext) = TermResult(listOf(TermLine(
        "ERR target must be ${show(ctx.minTenths, ctx)}-${show(ctx.maxTenths, ctx)}", Tone.Err)))

    private fun uptime(ctx: TermContext): String {
        val s = ctx.sessionStartMs?.let { Duration.ofMillis(ctx.nowMs - it) }
        return if (s == null) "up, 1 user, no session" else "up ${clock(s)}, 1 user, ${ctx.puffs ?: 0} packets"
    }

    /** H:MM:SS without the API-31-only Duration part accessors (minSdk is 26). */
    fun clock(d: Duration): String {
        val s = d.seconds.coerceAtLeast(0)
        return "%02d:%02d:%02d".format(Locale.ROOT, s / 3600, s / 60 % 60, s % 60)
    }

    fun bar(percent: Int, width: Int): String {
        val filled = (percent.coerceIn(0, 100) * width + 50) / 100
        return "[" + "#".repeat(filled) + "-".repeat(width - filled) + "]"
    }

    fun greeting(ctx: TermContext): String = when (LocalDateTime.ofInstant(Instant.ofEpochMilli(ctx.nowMs), ctx.zone).hour) {
        in 0..4 -> "after hours. the line is quiet."
        in 5..8 -> "dawn patrol. the sysop is asleep."
        in 9..16 -> "daylight. nobody hacks in daylight."
        in 17..21 -> "evening. modems warming up."
        else -> "late shift. the board is all yours."
    }

    fun motd(ctx: TermContext): List<String> {
        val m = Moon.at(ctx.nowMs)
        return listOf(
            "SubVenty 0.5 / session console",
            greeting(ctx),
            "moon ${m.name}. ${if (ctx.lootCount > 0) "${ctx.lootCount} files in ~/loot." else "~/loot is empty."}",
            "type help. or don't. no rush.",
        )
    }

    val FORTUNES = listOf(
        "you are not in a hurry. neither is the coil.",
        "the best exploit is a good night's sleep.",
        "real hackers read the manual. at 3am.",
        "the sysop is afk. the line stays open.",
        "connection is a state of mind. also a GATT characteristic.",
        "every BBS has a last caller. tonight it might be you.",
        "there is no cloud. just someone else's 486.",
        "a watched coil still heats. it just feels longer.",
        "drink some water. the terminal will wait.",
        "packets are puffs of data.",
        "nothing is due. nothing is late.",
        "the porch light is on.",
    )
}

/**
 * Turns state changes into feed lines: dial-up for connecting, IRC for joins and quits, a process
 * view of the heater. Only device-confirmed status should be passed in, so a dragged slider does not
 * spam the feed; repeated target lines also collapse by key.
 */
data class FeedSnapshot(
    val link: String, // idle | scanning | connecting | connected | reconnecting | failed
    val name: String,
    val demo: Boolean = false,
    val detail: String? = null,
    val heating: Boolean = false,
    val mode: TermMode = TermMode.Normal,
    val reached: Boolean = false,
    val targetTenths: Int? = null,
    val effectiveTenths: Int? = null,
    val fahrenheit: Boolean = false,
    val battery: Int? = null,
    val charging: Boolean = false,
    val puffs: Int = 0,
    val pump: Boolean = false,
)

object Feed {
    fun diff(prev: FeedSnapshot?, now: FeedSnapshot): List<TermLine> {
        val out = mutableListOf<TermLine>()
        fun t(tenths: Int) = "${Temperature.toDisplay(tenths, now.fahrenheit)}°${Temperature.unit(now.fahrenheit)}"
        if (prev?.link != now.link) when (now.link) {
            "scanning" -> out += TermLine("scanning the airwaves...", Tone.Dim)
            "connecting" -> { out += TermLine("ATZ", Tone.Dim); out += TermLine("OK", Tone.Dim); out += TermLine("ATDT ${now.name.uppercase(Locale.ROOT)}", Tone.Dim) }
            "connected" -> {
                out += TermLine("CONNECT // ${if (now.demo) "demo line" else "BLE"}", Tone.Ok)
                out += TermLine("*** ${now.name}${if (now.demo) " (demo)" else ""} has joined #session", Tone.Irc)
            }
            "reconnecting" -> out += TermLine("NO CARRIER // redialing${now.detail?.let { " ($it)" } ?: ""}", Tone.Warn)
            "failed" -> out += TermLine("BUSY // ${now.detail ?: "could not connect"}", Tone.Err)
            "idle" -> if (prev?.link == "connected" || prev?.link == "reconnecting")
                out += TermLine("*** ${prev.name} has quit (hung up)", Tone.Irc)
        }
        if (now.link != "connected" || prev?.link != "connected") return out
        if (prev.heating != now.heating) {
            if (now.heating) {
                out += TermLine("> exec heat.exe --target ${now.effectiveTenths?.let(::t) ?: "?"}", Tone.Ok)
                if (!now.reached) out += TermLine("  coil warming. icebreaker passes the time.", Tone.Dim)
            } else out += TermLine("> heat.exe exited (0)", Tone.Dim)
        }
        else if (now.heating && prev.mode != now.mode)
            out += TermLine("> heat.exe --${now.mode.name.lowercase()} ${now.effectiveTenths?.let(::t) ?: ""}".trimEnd(), Tone.Ok)
        if (now.targetTenths != null && prev.targetTenths != null && prev.targetTenths != now.targetTenths && prev.heating == now.heating)
            out += TermLine("target <- ${t(now.targetTenths)}", Tone.Out, key = "target")
        if (now.heating && now.reached && !prev.reached)
            out += TermLine("*** target locked: ${now.effectiveTenths?.let(::t) ?: ""}".trimEnd(), Tone.Ok)
        if (now.puffs > prev.puffs) out += TermLine("<<< packet #${now.puffs} received", Tone.Irc, key = null)
        if (now.charging && !prev.charging) out += TermLine("*** charger connected", Tone.Irc)
        if (!now.charging && prev.charging) out += TermLine("*** charger disconnected", Tone.Irc)
        val b = now.battery; val pb = prev.battery
        if (b != null && pb != null && !now.charging) listOf(20, 10, 5).firstOrNull { b <= it && pb > it }
            ?.let { out += TermLine("!!! battery at $b%", Tone.Warn) }
        if (now.pump != prev.pump) out += TermLine(if (now.pump) "> pump.exe (filling)" else "> pump.exe exited (0)", Tone.Dim)
        return out
    }

    /** Appends with collapse-by-key and a bounded buffer. */
    fun append(buffer: List<TermLine>, lines: List<TermLine>, max: Int = 200): List<TermLine> {
        var out = buffer
        for (line in lines) out = if (line.key != null && out.lastOrNull()?.key == line.key) out.dropLast(1) + line else out + line
        return out.takeLast(max)
    }
}

/** Moon phase from a mean synodic month. Good to about a day, which is fine for a tray icon. */
object Moon {
    data class Phase(val fraction: Double, val name: String, val illumination: Int)
    private const val SYNODIC = 29.530588853
    private const val EPOCH_NEW_MOON_MS = 947182440000L // 2000-01-06 18:14 UTC
    private val NAMES = listOf("new", "waxing crescent", "first quarter", "waxing gibbous",
        "full", "waning gibbous", "last quarter", "waning crescent")

    fun at(ms: Long): Phase {
        val days = (ms - EPOCH_NEW_MOON_MS) / 86_400_000.0
        val f = ((days % SYNODIC) + SYNODIC) % SYNODIC / SYNODIC
        val name = NAMES[((f * 8) + .5).toInt() % 8]
        val lit = ((1 - cos(2 * Math.PI * f)) / 2 * 100).roundToInt()
        return Phase(f, name, lit)
    }
}
