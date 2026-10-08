package com.example.padnotes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** One pen stroke. Coordinates and width are fractions of the canvas width. */
data class InkStroke(val color: Int, val width: Float, val points: List<Offset>)

private val inkColors = listOf(Color(0xFF111111), Color(0xFFD32F2F), Color(0xFF1976D2), Color(0xFF388E3C))
private val penWidths = listOf(0.004f, 0.009f, 0.018f)

fun encodeStrokes(list: List<InkStroke>): String = list.joinToString("\n") { s ->
    "${s.color},${(s.width * 10000).roundToInt()}:" +
        s.points.joinToString(" ") { "${(it.x * 10000).roundToInt()},${(it.y * 10000).roundToInt()}" }
}

fun decodeStrokes(text: String): List<InkStroke> = text.lines().mapNotNull { line ->
    runCatching {
        val (head, pts) = line.split(":", limit = 2)
        val (c, w) = head.split(",")
        InkStroke(
            c.toInt(), w.toInt() / 10000f,
            pts.split(" ").filter { it.isNotEmpty() }.map { p ->
                val (x, y) = p.split(",")
                Offset(x.toInt() / 10000f, y.toInt() / 10000f)
            }
        )
    }.getOrNull()
}

/**
 * Freehand drawing area for finger or stylus (Xiaomi Focus Pen). Turn on "Pen only"
 * to ignore palm and finger touches while writing.
 */
@Composable
fun SketchPad(initial: String, ink: Color, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val strokes = remember { mutableStateListOf<InkStroke>().also { it.addAll(decodeStrokes(initial)) } }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var colorIdx by remember { mutableIntStateOf(0) }
    var thickness by remember { mutableIntStateOf(1) }
    var eraser by remember { mutableStateOf(false) }
    var penOnly by remember { mutableStateOf(false) }

    fun erase(p: Offset) {
        strokes.removeAll { s -> s.points.any { (it - p).getDistance() < 0.03f } }
    }

    Column2(modifier) {
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(12.dp)).background(Color.White)
                .border(1.dp, Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .pointerInput(eraser, penOnly, colorIdx, thickness) {
                    val w = size.width.toFloat()
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (penOnly && down.type != PointerType.Stylus) return@awaitEachGesture
                        val pts = mutableListOf(Offset(down.position.x / w, down.position.y / w))
                        if (eraser) erase(pts.first()) else current = pts.toList()
                        down.consume()
                        drag(down.id) { change ->
                            val p = Offset(change.position.x / w, change.position.y / w)
                            if (eraser) erase(p) else { pts.add(p); current = pts.toList() }
                            change.consume()
                        }
                        if (!eraser) {
                            strokes.add(InkStroke(inkColors[colorIdx].toArgb(), penWidths[thickness], pts.toList()))
                            current = emptyList()
                        }
                        onChange(encodeStrokes(strokes))
                    }
                }
        ) {
            val w = size.width
            fun render(color: Color, width: Float, pts: List<Offset>) {
                if (pts.isEmpty()) return
                val px = pts.map { Offset(it.x * w, it.y * w) }
                if (px.size == 1) {
                    drawCircle(color, radius = width * w / 2f, center = px[0])
                    return
                }
                val path = Path().apply {
                    moveTo(px[0].x, px[0].y)
                    for (i in 1 until px.size) {
                        val mid = (px[i - 1] + px[i]) / 2f
                        quadraticBezierTo(px[i - 1].x, px[i - 1].y, mid.x, mid.y)
                    }
                    lineTo(px.last().x, px.last().y)
                }
                drawPath(path, color, style = Stroke(width = width * w, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            strokes.forEach { render(Color(it.color), it.width, it.points) }
            render(inkColors[colorIdx], penWidths[thickness], current)
        }

        val chip = FilterChipDefaults.filterChipColors(
            labelColor = ink, selectedLabelColor = ink, selectedContainerColor = ink.copy(alpha = 0.15f)
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            inkColors.forEachIndexed { i, c ->
                val on = i == colorIdx && !eraser
                Box(
                    Modifier.padding(4.dp).size(28.dp).clip(CircleShape).background(c)
                        .border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
                        .clickable { colorIdx = i; eraser = false }
                )
            }
            Spacer(Modifier.width(8.dp))
            listOf("Thin", "Medium", "Thick").forEachIndexed { i, label ->
                FilterChip(
                    selected = thickness == i, onClick = { thickness = i; eraser = false },
                    label = { Text(label) }, colors = chip, modifier = Modifier.padding(end = 6.dp)
                )
            }
            FilterChip(
                selected = eraser, onClick = { eraser = !eraser },
                label = { Text("Eraser") }, colors = chip, modifier = Modifier.padding(end = 6.dp)
            )
            FilterChip(
                selected = penOnly, onClick = { penOnly = !penOnly },
                label = { Text("Pen only") }, colors = chip, modifier = Modifier.padding(end = 6.dp)
            )
            TextButton(
                onClick = {
                    if (strokes.isNotEmpty()) { strokes.removeAt(strokes.lastIndex); onChange(encodeStrokes(strokes)) }
                },
                colors = ButtonDefaults.textButtonColors(contentColor = ink)
            ) { Text("Undo") }
            TextButton(
                onClick = { strokes.clear(); onChange("") },
                colors = ButtonDefaults.textButtonColors(contentColor = ink)
            ) { Text("Clear") }
        }
    }
}

@Composable
private fun Column2(modifier: Modifier, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Column(modifier.padding(vertical = 8.dp)) { content() }
}
