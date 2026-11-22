package io.github.orth4x0r.venty.ui.subventy

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.random.Random

private val Dust = Random(713).run {
    List(184) { Offset(nextFloat(), nextFloat()) }
}

@Composable
internal fun SculptureBackdrop(view: DrawView, modifier: Modifier) {
    val mobile = view == DrawView.Mobile
    Box(modifier.drawWithCache {
        val w = size.width
        val h = size.height
        val center = Offset(w * .5f, h * .49f)
        val base = if (mobile) Color(0xFF081418) else Color(0xFF150F19)
        val pool = if (mobile) Color(0xFF195557) else Color(0xFF612F30)
        val rim = if (mobile) Color(0xFF1A3345) else Color(0xFF362033)
        val spark = if (mobile) Color(0xFFA6E5D4) else Color(0xFFFFC6A7)
        val light = Brush.radialGradient(
            0f to pool, .46f to rim, 1f to base, center = center, radius = maxOf(w * .9f, h * .68f, 1f),
        )
        val edge = Brush.verticalGradient(
            0f to Color(0xEA0A0D12), .14f to Color.Transparent, .79f to Color.Transparent,
            1f to base, endY = h.coerceAtLeast(1f),
        )
        val sheen = Brush.linearGradient(
            0f to Color.Transparent, .29f to spark.copy(alpha = .075f), .57f to Color.Transparent,
            .78f to spark.copy(alpha = .08f), 1f to Color.Transparent,
            start = Offset(w, h * .25f), end = Offset(0f, h),
        )
        val waveLight = Brush.horizontalGradient(
            listOf(Color.Transparent, Color(0x28FBA888), Color(0x255F578E), Color.Transparent),
            endX = w.coerceAtLeast(1f),
        )
        val swirls = if (mobile) emptyList() else List(8) { band ->
            val offset = h * band * .015f
            Path().apply {
                moveTo(-w * .07f, h * .86f - offset)
                cubicTo(w * .29f, h * .82f - offset, w * .18f, h * .35f - offset, w * .50f, h * .34f - offset)
                cubicTo(w * .81f, h * .31f - offset, w * .86f, h * .60f - offset, w * 1.07f, h * .42f - offset)
            }
        }
        onDrawBehind {
            drawRect(light)
            drawRect(sheen)
            if (mobile) engraveMobileField(center, 34.dp.toPx())
            else swirls.forEachIndexed { index, film ->
                drawPath(film, waveLight, style = Stroke(if (index % 3 == 0) 1.dp.toPx() else .5.dp.toPx()))
            }
            Dust.forEachIndexed { index, dot ->
                drawCircle(
                    spark.copy(alpha = if (index % 9 == 0) .32f else .105f),
                    if (index % 9 == 0) .8.dp.toPx() else .42.dp.toPx(),
                    Offset(dot.x * w, dot.y * h),
                )
            }
            drawRect(edge)
            val trim = spark.copy(alpha = .30f)
            val inset = 16.dp.toPx()
            val upper = 53.dp.toPx()
            val cap = 18.dp.toPx()
            drawLine(trim, Offset(inset, upper), Offset(inset + cap, upper), .7.dp.toPx())
            drawLine(trim, Offset(w - inset - cap, upper), Offset(w - inset, upper), .7.dp.toPx())
        }
    })
}

private fun DrawScope.engraveMobileField(center: Offset, pitch: Float) {
    val ink = Color(0x1862B3AC)
    val h = size.height
    val w = size.width
    if (pitch <= 0f || h <= 0f || w <= 0f) return
    var x = (w % pitch) / 2f
    while (x < w) {
        drawLine(ink, Offset(x, 0f), Offset(x, h), .55f)
        x += pitch
    }
    var y = (h % pitch) / 2f
    while (y < h) {
        drawLine(ink, Offset(0f, y), Offset(w, y), .55f)
        y += pitch
    }
    for (index in 0..4) {
        val span = w * (.27f + index * .10f)
        val oval = Size(span * 2f, span * 1.64f)
        val pivot = center - Offset(span, span * .69f)
        drawArc(Color(0x3289BDAD), 21f, 138f, false, pivot, oval, style = Stroke(.6f))
    }
}
