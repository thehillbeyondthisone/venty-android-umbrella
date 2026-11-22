package io.github.orth4x0r.venty.ui.subventy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

internal object Deck {
    val Graphite = Color(0xFF242522)
    val Ink = Color(0xFF29231B)
    val Ivory = Color(0xFFE5D6BB)
    val Panel = Color(0xFFEDE1CB)
    val Muted = Color(0xFF5E5141)
    val Border = Color(0xFF92836D)
    val Amber = Color(0xFFFFB638)
    val Rust = Color(0xFF9D341C)
    val Teal = Color(0xFF2B625D)
    val Shape = RoundedCornerShape(6.dp)
}

internal enum class KeyFinish { Ivory, Rust, Teal, Graphite }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DeckKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    finish: KeyFinish = KeyFinish.Ivory,
    active: Boolean = false,
    description: String? = null,
    onLongClick: (() -> Unit)? = null,
    repeatWhileHeld: Boolean = false,
    swatch: Color? = null,
) {
    val interaction = if (repeatWhileHeld) remember { MutableInteractionSource() } else null
    var repeating by remember { mutableStateOf(false) }
    val action by rememberUpdatedState(onClick)
    val holdAction: (() -> Unit)? = if (repeatWhileHeld) ({ repeating = true }) else onLongClick
    if (interaction != null) {
        val pressed by interaction.collectIsPressedAsState()
        LaunchedEffect(pressed, repeating, enabled) {
            if (!pressed || !enabled) repeating = false
            else if (repeating) while (true) { action(); delay(135) }
        }
    }
    val (top, bottom, ink) = when {
        active -> Triple(Color(0xFFFFC85F), Color(0xFFE69B2B), Deck.Ink)
        finish == KeyFinish.Rust -> Triple(Color(0xFFAC421F), Color(0xFF802812), Color.White)
        finish == KeyFinish.Teal -> Triple(Color(0xFF32716B), Color(0xFF214D47), Color.White)
        finish == KeyFinish.Graphite -> Triple(Color(0xFF3B3B35), Deck.Graphite, Deck.Ivory)
        else -> Triple(Color(0xFFF3E7D1), Color(0xFFD4C19D), Deck.Ink)
    }
    Box(
        modifier.heightIn(min = 48.dp)
            .shadow(if (enabled) 2.dp else 0.dp, Deck.Shape)
            .clip(Deck.Shape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .border(1.dp, if (active) Color(0xFF965509) else Deck.Border, Deck.Shape)
            .semantics {
                if (active) selected = true
                if (description != null) contentDescription = description
            }
            .combinedClickable(enabled = enabled, role = Role.Button, interactionSource = interaction,
                onClick = onClick, onLongClick = holdAction)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (swatch != null) Box(Modifier.size(9.dp).background(swatch, CircleShape).border(.5.dp, Deck.Ink, CircleShape))
            Text(
                label,
                color = if (enabled) ink else ink.copy(alpha = .60f),
                fontSize = if (label.length == 1) 22.sp else 13.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun DeckPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Deck.Panel, Deck.Shape).border(1.dp, Deck.Border, Deck.Shape).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
internal fun DeckLCD(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().background(Deck.Graphite, Deck.Shape).border(1.dp, Color.Black, Deck.Shape).padding(5.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFFFFC34D), Color(0xFFF2A525))), Deck.Shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        content = content,
    )
}

@Composable
internal fun DeckLabel(text: String, size: Int = 13, color: Color = Deck.Ink, modifier: Modifier = Modifier) {
    Text(text, modifier, color = color, fontSize = size.sp, lineHeight = (size + 4).sp,
        fontWeight = if (size >= 15) FontWeight.Bold else FontWeight.Medium)
}

@Composable
internal fun DeckStripe() {
    Column(Modifier.fillMaxWidth()) {
        listOf(Deck.Rust, Color(0xFFE09E35), Deck.Teal).forEach { color ->
            Box(Modifier.fillMaxWidth().height(2.dp).background(color))
        }
    }
}

/** LCD numerals are drawn at the available width. Large system text uses scalable text instead. */
@Composable
internal fun LcdNumber(number: Int?, unit: String, modifier: Modifier = Modifier) {
    val value = number?.toString() ?: "—"
    val reading = if (number == null) value else "$value°$unit"
    if (LocalDensity.current.fontScale > 1.3f || number == null || number < 0) {
        Text(reading, modifier, color = Deck.Ink, fontFamily = FontFamily.Monospace,
            fontSize = 34.sp, fontWeight = FontWeight.Bold)
        return
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width((value.length * 37).dp).height(58.dp).semantics { contentDescription = reading }) {
            val glyph = size.width / value.length
            val stroke = size.height * .105f
            val half = size.height / 2
            val maxX = glyph - 5.dp.toPx()
            val horizontal = listOf(0f, half - stroke / 2, size.height - stroke)
            val vertical = listOf(
                Offset(maxX - stroke, stroke), Offset(maxX - stroke, half + stroke / 2),
                Offset(0f, half + stroke / 2), Offset(0f, stroke),
            )
            value.forEachIndexed { index, ch ->
                val mask = DigitSegments[ch.digitToInt()]
                translate(index * glyph) {
                    horizontal.forEachIndexed { row, y ->
                        val bit = listOf(0, 6, 3)[row]
                        val path = Path().apply {
                            moveTo(stroke * .6f, y + stroke / 2)
                            lineTo(stroke * 1.1f, y); lineTo(maxX - stroke * 1.1f, y)
                            lineTo(maxX - stroke * .6f, y + stroke / 2)
                            lineTo(maxX - stroke * 1.1f, y + stroke); lineTo(stroke * 1.1f, y + stroke)
                            close()
                        }
                        drawPath(path, Deck.Ink.copy(alpha = if (mask and (1 shl bit) != 0) 1f else .09f))
                    }
                    vertical.forEachIndexed { pos, offset ->
                        val length = half - stroke * 1.25f
                        val x = offset.x
                        val y = offset.y
                        val path = Path().apply {
                            moveTo(x + stroke / 2, y)
                            lineTo(x + stroke, y + stroke / 2); lineTo(x + stroke, y + length - stroke / 2)
                            lineTo(x + stroke / 2, y + length)
                            lineTo(x, y + length - stroke / 2); lineTo(x, y + stroke / 2)
                            close()
                        }
                        drawPath(path, Deck.Ink.copy(alpha = if (mask and (1 shl listOf(1, 2, 4, 5)[pos]) != 0) 1f else .09f))
                    }
                }
            }
        }
        Text("°$unit", Modifier.padding(start = 3.dp), color = Deck.Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}

private val DigitSegments = intArrayOf(63, 6, 91, 79, 102, 109, 125, 7, 127, 111)
