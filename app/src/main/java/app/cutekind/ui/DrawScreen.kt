package app.cutekind.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.cutekind.data.Stroke
import app.cutekind.data.UNIT
import app.cutekind.draw.Renderer
import kotlin.math.abs

@Composable
fun DrawScreen(
    strokes: List<Stroke>,
    bg: Int,
    note: String,
    partnerLabel: String,
    onStroke: (Stroke) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onBg: (Int) -> Unit,
    onNote: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var color by rememberSaveable { mutableIntStateOf(PENS[1]) }
    var brush by rememberSaveable { mutableIntStateOf(1) }
    var eraser by rememberSaveable { mutableStateOf(false) }
    // the stroke under the finger; `tick` invalidates the canvas as points are added
    val live = remember { ArrayList<Int>() }
    var tick by remember { mutableIntStateOf(0) }
    var drawing by remember { mutableStateOf(false) }
    val commit by rememberUpdatedState(onStroke)

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Draw for $partnerLabel", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        val shape = RoundedCornerShape(24.dp)
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(shape)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shape)
                .semantics { contentDescription = "Drawing canvas" }
                .pointerInput(color, brush, eraser) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun add(o: Offset): Boolean {
                            val x = (o.x / size.width * UNIT).toInt().coerceIn(0, UNIT)
                            val y = (o.y / size.height * UNIT).toInt().coerceIn(0, UNIT)
                            val n = live.size
                            if (n >= 2 && abs(live[n - 2] - x) + abs(live[n - 1] - y) < 6) return false
                            live.add(x); live.add(y); return true
                        }
                        live.clear(); add(down.position); drawing = true; tick++
                        down.consume()
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (add(change.position)) tick++
                            change.consume()
                        }
                        commit(Stroke(if (eraser) 0 else color, BRUSHES[brush] * if (eraser) 2.5f else 1f, live.toList(), eraser))
                        live.clear(); drawing = false; tick++
                    }
                },
        ) {
            drawRect(Color(bg))
            tick // read so the live stroke redraws
            drawIntoCanvas {
                val all = if (drawing && live.isNotEmpty())
                    strokes + Stroke(color, BRUSHES[brush] * if (eraser) 2.5f else 1f, live.toList(), eraser) else strokes
                Renderer.draw(it.nativeCanvas, size.width, all, bg)
            }
        }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PENS.forEach { c -> Swatch(c, selected = !eraser && c == color, label = "Pen colour") { color = c; eraser = false } }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BRUSHES.forEachIndexed { i, _ ->
                val sel = brush == i
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .background(if (sel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable(role = Role.RadioButton, onClickLabel = "Brush size ${i + 1}") { brush = i },
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size((8 + i * 8).dp).background(MaterialTheme.colorScheme.onSurface, CircleShape)) }
            }
            FilterChip(eraser, { eraser = !eraser }, label = { Text("Eraser") })
            Spacer(Modifier.weight(1f))
            TextButton(onUndo, enabled = strokes.isNotEmpty()) { Text("Undo") }
            TextButton(onClear, enabled = strokes.isNotEmpty()) { Text("Clear") }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Paper", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 6.dp))
            PAPERS.forEach { c -> Swatch(c, selected = c == bg, label = "Paper colour") { onBg(c) } }
        }

        OutlinedTextField(
            note, { onNote(it.take(60)) }, Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("Add a little note (optional)") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        Button(onSend, Modifier.fillMaxWidth().height(52.dp), enabled = strokes.isNotEmpty()) {
            Text("Send to $partnerLabel's home screen")
        }
    }
}

@Composable
private fun Swatch(color: Int, selected: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.RadioButton, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(if (selected) 40.dp else 32.dp)
                .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                .padding(if (selected) 5.dp else 0.dp)
                .background(Color(color), CircleShape),
        )
    }
}
