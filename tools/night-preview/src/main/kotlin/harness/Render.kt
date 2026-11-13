package harness

// Scenes for design review. The Client() shell mirrors SubVentyScreen without a controller.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.orth4x0r.venty.ui.subventy.*
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.random.Random

lateinit var vt323: FontFamily
const val W = 411; const val H = 860

fun shot(name: String, phosphor: Phosphor = Phosphor.Amber, w: Int = W, h: Int = H, frames: Int = 1, content: @Composable () -> Unit) {
    val scale = 2f
    val scene = ImageComposeScene((w * scale).toInt(), (h * scale).toInt(), Density(scale)) {
        CompositionLocalProvider(LocalPhosphor provides phosphor, LocalTermFont provides vt323, LocalReducedMotion provides false) { content() }
    }
    var img = scene.render(0)
    if (frames > 1) { scene.render(1_000_000L); img = scene.render(frames * 100_000_000L) }
    File("out").mkdirs()
    File("out/$name.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
    scene.close()
    println("wrote out/$name.png")
}

/** The SubVenty shell as SubVentyScreen composes it, minus the controller. */
@Composable fun Client(page: Int = PAGE_TERMINAL, notice: String = "heater off", link: String = "demo", battery: Int? = 87,
    tree: Boolean = page != PAGE_TERMINAL, body: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Night.Room)) {
        CrtGlass(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(top = 24.dp).padding(5.dp).border(1.dp, Color(0xFF4A4E66))) {
                Row(Modifier.fillMaxWidth().background(S7.CaptionBrush).heightIn(min = 48.dp).padding(start = 7.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    SilverSeven(Modifier.size(20.dp))
                    Text("SubVenty", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 6.dp))
                    S7IconButton("min", Icons.Outlined.Minimize, {})
                    S7IconButton("max", Icons.Outlined.CropSquare, {})
                    S7IconButton("close", Icons.Outlined.Close, {})
                }
                Row(Modifier.fillMaxWidth().background(S7.Panel).padding(6.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(Modifier.weight(1f)) {
                        S7Text("destination:", 10, Color(0xFFC7D8E8))
                        Text("Demo Venty", color = Color.White, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().border(1.dp, Color.Gray).background(Color(0xFF070B16)).padding(6.dp))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { S7Text("link:", 10); S7Text("BLE", 12) }
                    S7Button("disconnect", {}, Modifier.widthIn(max = 110.dp))
                }
                ClientToolbar({}, {}, {}, true)
                Row(Modifier.weight(1f).fillMaxWidth().padding(5.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (tree) NavigationTree(page, {}, Modifier.width(116.dp).fillMaxHeight().border(1.dp, Color(0xFF34343D)))
                    ClientWorkspace(page, Modifier.weight(1f).fillMaxHeight(), body)
                }
                Taskbar(notice, link, battery, false, {})
            }
        }
    }
}

val bootFeed = listOf(
    "+----------------------------+", "|  S U B V E N T Y  // 0.5   |", "|  after hours terminal      |",
    "+----------------------------+", "after hours. the line is quiet.", "moon waxing crescent. ~/loot is empty.",
    "type help. or don't. no rush.").map { TermLine(it, Tone.Info) } + listOf(
    TermLine("ATZ", Tone.Dim), TermLine("OK", Tone.Dim), TermLine("ATDT DEMO VENTY", Tone.Dim),
    TermLine("CONNECT // demo line", Tone.Ok), TermLine("*** Demo Venty (demo) has joined #session", Tone.Irc))

fun ctx(heating: Boolean = false, reached: Boolean = false, mode: TermMode = TermMode.Normal, connected: Boolean = true) = TermContext(
    connected = connected, demo = true, deviceName = "Demo Venty", heating = heating, reached = reached, mode = mode,
    targetTenths = 1850, effectiveTenths = when (mode) { TermMode.Normal -> 1850; TermMode.Boost -> 1950; TermMode.Super -> 2100 },
    battery = 87, sessionStartMs = if (heating) 0 else null, puffs = if (reached) 3 else 0, handle = "nightjar", nowMs = 0)

val noop = HomeActions({}, {}, {})

fun main(args: Array<String>) {
    vt323 = FontFamily(Font(File(args.firstOrNull() ?: "../../app/src/main/res/font/vt323.ttf")))
    shot("01-home-idle") { Client { TerminalHome(HomeState(ctx(), bootFeed), noop, "", {}) } }
    shot("02-home-warming", frames = 3) { Client(notice = "heating") {
        TerminalHome(HomeState(ctx(heating = true, mode = TermMode.Boost), bootFeed + listOf(
            TermLine("nightjar@subventy:~$ boost", Tone.Echo), TermLine("> heat.exe --boost", Tone.Ok),
            TermLine("> exec heat.exe --target 195°C", Tone.Ok), TermLine("  coil warming. icebreaker passes the time.", Tone.Dim)),
            session = "00:00:41", etaSeconds = 23), noop, "icebr", {}) } }
    shot("03-home-locked-green", Phosphor.Green) { Client(notice = "target reached") {
        TerminalHome(HomeState(ctx(heating = true, reached = true), bootFeed + listOf(
            TermLine("> exec heat.exe --target 185°C", Tone.Ok), TermLine("*** target locked: 185°C", Tone.Ok),
            TermLine("<<< packet #1 received", Tone.Irc), TermLine("<<< packet #2 received", Tone.Irc), TermLine("<<< packet #3 received", Tone.Irc)),
            session = "00:06:12"), noop, "", {}) } }
    shot("04-home-nocarrier") { Client(link = "idle", battery = null, notice = "device disconnected", tree = false) {
        TerminalHome(HomeState(ctx(connected = false), bootFeed.take(7) + Terminal.run("help", ctx(connected = false)).lines), noop, "", {}) } }
    val g0 = IcebreakerGame("CAFE", "7F.3A.ember.line")
    val g = listOf("ABCD", "AEFE", "CEFA", "CAFD").fold(g0) { acc, s -> s.fold(acc) { a, c -> a.type(c) }.submit() }.type('C').type('A')
    shot("05-icebreaker", h = 760) { Box(Modifier.fillMaxSize().background(Color(0xCC000000)).padding(14.dp), contentAlignment = Alignment.Center) {
        WindowFrame("icebreaker.exe", {}) { IcebreakerBody(g, {}, null, false, {}, {}) } } }
    val won = "CAFE".fold(g.backspace().backspace()) { a, c -> a.type(c) }.submit()
    shot("06-icebreaker-cracked", h = 760) { Box(Modifier.fillMaxSize().background(Color(0xCC000000)).padding(14.dp), contentAlignment = Alignment.Center) {
        WindowFrame("icebreaker.exe", {}) { IcebreakerBody(won, {}, Loot.FILES[3], false, {}, {}) } } }
    shot("07-loot", Phosphor.Green) { Client(page = PAGE_LOOT) {
        S7Text("loot", 13); LootListing(Loot.FILES.take(5).mapIndexed { i, f -> f to 1_700_000_000_000L + i * 86_400_000L },
            setOf("whoami", "fortune", "xyzzy", "moon", "sudo"), {}, {}) } }
    shot("08-loot-reader", h = 760) { Box(Modifier.fillMaxSize().background(Color(0xCC000000)).padding(14.dp), contentAlignment = Alignment.Center) {
        WindowFrame(Loot.FILES[2].name, {}) { LootReader(Loot.FILES[2]); S7Button("close", {}) } } }
    shot("09-vapor-scr", frames = 90) { VaporScreensaver(ctx(heating = true, reached = true), {}) }
    shot("10-volcano-cyan", Phosphor.Cyan) { Client(battery = null, notice = "heating") {
        TerminalHome(HomeState(ctx(heating = true).copy(kindName = "Volcano", deviceName = "Demo Volcano", hasBoost = false, hasPump = true,
            battery = null, liveTenths = 1630, minTenths = 400, maxTenths = 2300), bootFeed), noop, "", {}) } }
    shot("11-help-fortune") { Client { TerminalHome(HomeState(ctx(), (Terminal.run("help", ctx()).lines + Terminal.run("fortune", ctx()).lines + Terminal.run("sudo rm", ctx()).lines)), noop, "", {}) } }
}
