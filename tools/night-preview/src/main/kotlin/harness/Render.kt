package harness

// Scenes for design review. Client() mirrors SubVentyScreen's preview-2 shell without a controller.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.orth4x0r.venty.ui.subventy.*
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

const val W = 480; const val H = 854 // the preview-2 emulator screenshot size

fun shot(name: String, w: Int = W, h: Int = H, frames: Int = 1, content: @Composable () -> Unit) {
    val scale = 2f
    val scene = ImageComposeScene((w * scale).toInt(), (h * scale).toInt(), Density(scale)) {
        MaterialTheme(colorScheme = darkColorScheme(primary = S7.Caption, onPrimary = Color.Black, background = Color.Black,
            surface = S7.Panel, onSurface = Color.White, onBackground = Color.White, secondary = S7.Link, outline = S7.Caption)) { content() }
    }
    var img = scene.render(0)
    if (frames > 1) { scene.render(1_000_000L); img = scene.render(frames * 100_000_000L) }
    File("out").mkdirs()
    File("out/$name.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
    scene.close()
    println("wrote out/$name.png")
}

@Composable fun Client(page: Int = PAGE_TERMINAL, title: String = "session terminal", notice: String = "heater off",
    body: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Color.Black).padding(top = 24.dp).padding(5.dp).border(1.dp, Color.Gray)) {
        Row(Modifier.fillMaxWidth().background(S7.Caption).heightIn(min = 48.dp).padding(start = 7.dp),
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
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                Text("S7", fontSize = 24.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = (-4).sp,
                    color = Color(0xFF052D48), style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(
                        Color(0xFF3975A6), androidx.compose.ui.geometry.Offset(1f, 1f), 1f)))
            }
        }
        ClientToolbar({}, {}, {}, true)
        Row(Modifier.weight(1f).fillMaxWidth().padding(5.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            NavigationTree(page, {}, Modifier.width(116.dp).fillMaxHeight().border(1.dp, Color(0xFF34343D)))
            ClientWorkspace(page, Modifier.weight(1f).fillMaxHeight()) {
                S7Text(title, 13); S7Text("venty / demo connection", 10, S7.Muted); body()
            }
        }
        Row(Modifier.fillMaxWidth().background(S7.Panel).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf("demo", "0.5.0").forEach { Box(Modifier.border(1.dp, S7.Border).padding(4.dp)) { S7Text(it, 10) } }
            Box(Modifier.weight(1f).border(1.dp, S7.Border).padding(4.dp)) { S7Text(notice, 10) }
            Box(Modifier.border(1.dp, S7.Border).padding(4.dp)) { S7Text("00:41", 10) }
        }
    }
}

val bootFeed = listOf("SubVenty 0.5 / session console", "after hours. the line is quiet.", "moon waxing crescent. ~/loot is empty.",
    "type help. or don't. no rush.").map { TermLine(it, Tone.Info) } + listOf(
    TermLine("ATZ", Tone.Dim), TermLine("OK", Tone.Dim), TermLine("ATDT DEMO VENTY", Tone.Dim),
    TermLine("CONNECT // demo line", Tone.Ok), TermLine("*** Demo Venty (demo) has joined #session", Tone.Irc))

fun ctx(heating: Boolean = false, reached: Boolean = false, mode: TermMode = TermMode.Normal, connected: Boolean = true) = TermContext(
    connected = connected, demo = true, deviceName = "Demo Venty", heating = heating, reached = reached, mode = mode,
    targetTenths = 1850, effectiveTenths = when (mode) { TermMode.Normal -> 1850; TermMode.Boost -> 1950; TermMode.Super -> 2100 },
    battery = 87, puffs = if (reached) 3 else 0, handle = "nightjar", nowMs = 0)

val noop = HomeActions({}, {}, {})

@Composable fun Dialogish(content: @Composable () -> Unit) =
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)).padding(14.dp), contentAlignment = Alignment.Center) { content() }

fun main() {
    shot("01-terminal") { Client { TerminalHome(HomeState(ctx(), bootFeed), noop, "", {}) } }
    shot("02-terminal-heating") { Client(notice = "heating") {
        TerminalHome(HomeState(ctx(heating = true, mode = TermMode.Boost), bootFeed + listOf(
            TermLine("nightjar@subventy:~$ boost", Tone.Echo), TermLine("> heat.exe --boost", Tone.Ok),
            TermLine("> exec heat.exe --target 195°C", Tone.Ok), TermLine("  coil warming. icebreaker passes the time.", Tone.Dim)),
            session = "00:00:41", etaSeconds = 23), noop, "icebr", {}) } }
    shot("03-terminal-reached") { Client(notice = "target reached") {
        TerminalHome(HomeState(ctx(heating = true, reached = true), bootFeed + listOf(TermLine("*** target locked: 185°C", Tone.Ok),
            TermLine("<<< packet #3 received", Tone.Irc)), session = "00:06:12"), noop, "", {}) } }
    val g = listOf("ABCD", "AEFE", "CEFA", "CAFD").fold(IcebreakerGame("CAFE", "7F.3A.ember.line")) { acc, s -> s.fold(acc) { a, c -> a.type(c) }.submit() }.type('C').type('A')
    shot("04-icebreaker") { Dialogish { WindowFrame("icebreaker", {}) { IcebreakerBody(g, {}, null, false, {}, {}) } } }
    val won = "CAFE".fold(g.backspace().backspace()) { a, c -> a.type(c) }.submit()
    shot("05-icebreaker-cracked") { Dialogish { WindowFrame("icebreaker", {}) { IcebreakerBody(won, {}, Loot.FILES[3], false, {}, {}) } } }
    shot("06-loot") { Client(page = PAGE_LOOT, title = "loot") {
        LootListing(Loot.FILES.take(4).mapIndexed { i, f -> f to 1_700_000_000_000L + i * 86_400_000L }, setOf("whoami", "fortune", "xyzzy", "moon"), {}, {}) } }
    shot("07-loot-reader") { Dialogish { WindowFrame(Loot.FILES[2].name, {}) { LootReader(Loot.FILES[2]); S7Button("close", {}) } } }
    shot("08-screensaver", frames = 90) { VaporScreensaver(ctx(heating = true, reached = true), {}) }
    shot("09-volcano") { Client(notice = "heating") {
        TerminalHome(HomeState(ctx(heating = true).copy(kindName = "Volcano", deviceName = "Demo Volcano", hasBoost = false, hasPump = true,
            battery = null, liveTenths = 1630, maxTenths = 2300), bootFeed), noop, "", {}) } }
}
