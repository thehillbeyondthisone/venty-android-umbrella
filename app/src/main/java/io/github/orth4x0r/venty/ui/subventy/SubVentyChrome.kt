package io.github.orth4x0r.venty.ui.subventy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal object S7 {
    val Caption = Deck.Amber
    val Panel = Deck.Graphite
    val Workspace = Color(0xFF191917)
    val Selection = Color(0xFF54351C)
    val Text = Deck.Ivory
    val Muted = Color(0xFFC0B6A7)
    val Link = Color(0xFFF4BA5B)
    val Border = Color(0xFF7C6E5D)
}

@Composable internal fun S7Text(text: String, size: Int = 12, color: Color = S7.Text, modifier: Modifier = Modifier) {
    Text(text, modifier, color = color, fontSize = size.sp, lineHeight = (size + 5).sp,
        fontFamily = FontFamily.SansSerif)
}

@Composable
internal fun S7Button(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    DeckKey(label, onClick, modifier, enabled)
}

@Composable internal fun UtilityWindow(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(14.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().heightIn(max = maxHeight)
                .border(2.dp, S7.Border).background(S7.Panel).semantics { paneTitle = title }) {
                Row(Modifier.fillMaxWidth().background(Deck.Amber).heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    DeckLabel(title, 15, Deck.Ink, Modifier.weight(1f))
                    DeckKey("×", onClose, Modifier.width(48.dp), description = "Close $title")
                }
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp), content = content)
            }
        }
    }
}

@Composable internal fun S7Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().border(1.dp, S7.Border).padding(10.dp),
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
    modifier: Modifier = Modifier, numeric: Boolean = false, singleLine: Boolean = true, secret: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        S7Text(label, 11, S7.Muted)
        BasicTextField(value, onChange, Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .border(1.dp, S7.Border).background(S7.Workspace).padding(9.dp)
            .semantics { contentDescription = label },
            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
            cursorBrush = SolidColor(Color.White), singleLine = singleLine,
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = when {
                secret -> KeyboardType.Password
                numeric -> KeyboardType.Number
                else -> KeyboardType.Text
            }))
    }
}

@Composable internal fun ArchiveRow(title: String, summary: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).border(.5.dp, S7.Border)
        .clickable(role = Role.Button, onClick = onClick).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Description, null, Modifier.size(15.dp), S7.Link)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            S7Text(title, 11)
            Text(summary, color = S7.Muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
