package io.github.orth4x0r.venty.ui.subventy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

internal object S7 {
    val Caption = Color(0xFF8076B4)
    val Panel = Color(0xFF124D76)
    val Workspace = Color(0xFF101112)
    val Selection = Color(0xFF004C80)
    val Text = Color(0xFFE4E4E9)
    val Muted = Color(0xFFA7ADB9)
    val Link = Color(0xFF83AAE3)
    val Border = Color(0xFF737580)
}

@Composable internal fun S7Text(text: String, size: Int = 12, color: Color = S7.Text, modifier: Modifier = Modifier) {
    Text(text, modifier, color = color, fontSize = size.sp, lineHeight = (size + 5).sp,
        fontFamily = FontFamily.SansSerif)
}

/** An original pixel-edged silver seven, drawn locally rather than using reference screenshots. */
@Composable internal fun SilverSeven(modifier: Modifier = Modifier, watermark: Boolean = false) {
    Canvas(modifier.semantics { contentDescription = if (watermark) "" else "SubVenty seven emblem" }) {
        val side = minOf(size.width, size.height)
        translate((size.width - side) / 2, (size.height - side) / 2) {
        scale(side / 108f, side / 108f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            val path = Path().apply {
                moveTo(13f, 22f); lineTo(31f, 22f); lineTo(31f, 17f); lineTo(87f, 17f)
                lineTo(87f, 21f); lineTo(96f, 21f); lineTo(96f, 35f); lineTo(90f, 35f)
                lineTo(90f, 42f); lineTo(82f, 42f); lineTo(82f, 51f); lineTo(75f, 51f)
                lineTo(75f, 70f); lineTo(69f, 70f); lineTo(69f, 91f); lineTo(43f, 91f)
                lineTo(43f, 85f); lineTo(48f, 85f); lineTo(48f, 73f); lineTo(53f, 73f)
                lineTo(53f, 62f); lineTo(59f, 62f); lineTo(59f, 47f); lineTo(29f, 47f)
                lineTo(29f, 43f); lineTo(20f, 43f); lineTo(20f, 37f); lineTo(13f, 37f); close()
            }
            val alpha = if (watermark) .06f else 1f
            drawPath(path, Color.Black.copy(alpha = alpha), style = Stroke(11f))
            drawPath(path, Color.White.copy(alpha = alpha), style = Stroke(5f))
            drawPath(path, Brush.verticalGradient(listOf(Color.White, Color(0xFFEEEEEE), Color(0xFF60646C), Color(0xFFD4D5D8), Color(0xFF747985)), startY = 17f, endY = 92f), alpha = alpha)
            drawPath(path, Color(0xFF202127).copy(alpha = alpha), style = Stroke(1.5f))
        }
        }
    }
}

@Composable internal fun S7Button(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(modifier.defaultMinSize(minHeight = 48.dp).semantics {
        if (text == "+") contentDescription = "Increase value"
        if (text == "−") contentDescription = "Decrease value"
    }
        .border(1.dp, if (enabled) Color(0xFF858AAF) else Color(0xFF454951))
        .background(Brush.verticalGradient(listOf(Color(0xFF164D74), Color(0xFF103C5B))))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 9.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        S7Text(text, color = if (enabled) S7.Text else Color(0xFF737987))
    }
}

@Composable internal fun S7IconButton(label: String, icon: ImageVector, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(modifier.size(48.dp).semantics { contentDescription = label }
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(18.dp), tint = if (enabled) Color(0xFFD6DFEA) else Color(0xFF5D7485))
    }
}

@Composable internal fun ClientToolbar(onNavigate: () -> Unit, onPage: (Int) -> Unit,
    onWindow: (String) -> Unit, locateEnabled: Boolean) {
    Row(Modifier.fillMaxWidth().background(S7.Panel)
        .border(1.dp, Color(0xFF15354D)).horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically) {
        S7IconButton("Navigation tree", Icons.Outlined.AccountTree, onNavigate)
        S7IconButton("Device information", Icons.Outlined.Computer, { onPage(0) })
        S7IconButton("Temperature controls", Icons.Outlined.Thermostat, { onPage(1) })
        S7IconButton("Temperature ramp", Icons.Outlined.Timeline, { onWindow("ramp") })
        S7IconButton("Session archive", Icons.Outlined.Storage, { onPage(2) })
        S7IconButton("Field notes", Icons.Outlined.Description, { onPage(4) })
        S7IconButton("Locate device", Icons.Outlined.MyLocation, { onWindow("find") }, enabled = locateEnabled)
        S7IconButton("Device settings", Icons.Outlined.Tune, { onWindow("settings") })
        S7IconButton("About SubVenty", Icons.Outlined.HelpOutline, { onWindow("about") })
    }
}

private data class TreeGroup(val name: String, val children: List<Pair<Int, String>>)
private val groups = listOf(
    TreeGroup("system", listOf(0 to "connection", 7 to "link monitor")),
    TreeGroup("control", listOf(1 to "temperature", 8 to "ramp editor", 9 to "device settings")),
    TreeGroup("archive", listOf(2 to "session log", 3 to "collections", 4 to "field notes")),
    TreeGroup("personal", listOf(5 to "postcard", 6 to "local options")),
)

@Composable internal fun NavigationTree(page: Int, onPage: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.background(Color(0xFF0C0D0F)).verticalScroll(rememberScrollState()).padding(vertical = 5.dp)) {
        groups.forEach { group ->
            var expanded by rememberSaveable(group.name) { mutableStateOf(true) }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .clickable(role = Role.Button) { expanded = !expanded }.padding(horizontal = 5.dp),
                verticalAlignment = Alignment.CenterVertically) {
                S7Text(if (expanded) "−" else "+", 10, S7.Muted)
                S7Text(group.name, 11, Color(0xFFA298D3), Modifier.padding(start = 4.dp))
            }
            if (expanded) group.children.forEach { (id, title) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .background(if (page == id) S7.Selection else Color.Transparent)
                    .semantics { selected = page == id }
                    .clickable(role = Role.Tab) { onPage(id) }.padding(start = 10.dp, end = 4.dp)
                    .drawBehind {
                        drawLine(Color(0xFF524B68), androidx.compose.ui.geometry.Offset(2.dp.toPx(), 0f), androidx.compose.ui.geometry.Offset(2.dp.toPx(), size.height), 1f)
                        drawLine(Color(0xFF524B68), androidx.compose.ui.geometry.Offset(2.dp.toPx(), size.height / 2), androidx.compose.ui.geometry.Offset(8.dp.toPx(), size.height / 2), 1f)
                    }, verticalAlignment = Alignment.CenterVertically) {
                    S7Text(title, 11, if (page == id) Color.White else Color(0xFFC2C4D1), Modifier.padding(start = 10.dp))
                }
            }
        }
    }
}

@Composable internal fun UtilityWindow(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(14.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().heightIn(max = maxHeight)
                .border(2.dp, S7.Border).background(S7.Panel).semantics { paneTitle = title }) {
                Row(Modifier.fillMaxWidth().background(S7.Caption).heightIn(min = 48.dp).padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    S7Text(title, 13, Color.White, Modifier.weight(1f))
                    S7IconButton("Close $title", Icons.Outlined.Close, onClose)
                }
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp), content = content)
            }
        }
    }
}

@Composable internal fun S7Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().border(1.dp, Color(0xFF42444E)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)) {
        S7Text(title, 11, S7.Link)
        content()
    }
}

@Composable internal fun S7Check(text: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled,
            colors = CheckboxDefaults.colors(checkedColor = S7.Link, uncheckedColor = S7.Muted, checkmarkColor = Color.Black))
        S7Text(text, 12, if (enabled) S7.Text else S7.Muted, Modifier.weight(1f).padding(start = 7.dp))
    }
}

@Composable internal fun S7Input(label: String, value: String, onChange: (String) -> Unit,
    modifier: Modifier = Modifier, numeric: Boolean = false, singleLine: Boolean = true) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        S7Text(label, 11, S7.Muted)
        BasicTextField(value, onChange, Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .border(1.dp, Color(0xFF80859D)).background(Color(0xFF070B16)).padding(9.dp)
            .semantics { contentDescription = label },
            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
            cursorBrush = SolidColor(Color.White), singleLine = singleLine,
            keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text))
    }
}

@Composable internal fun ArchiveRow(title: String, summary: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).border(.5.dp, Color(0xFF343842))
        .clickable(role = Role.Button, onClick = onClick).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Description, null, Modifier.size(15.dp), S7.Link)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            S7Text(title, 11)
            Text(summary, color = S7.Muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable internal fun ClientWorkspace(page: Int, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    LaunchedEffect(page) { scroll.scrollTo(0) }
    Box(modifier.fillMaxWidth().background(S7.Workspace)) {
        SilverSeven(Modifier.align(Alignment.Center).size(180.dp), watermark = true)
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp), content = content)
    }
}
