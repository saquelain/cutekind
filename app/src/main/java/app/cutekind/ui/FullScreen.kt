package app.cutekind.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cutekind.data.Drawing
import app.cutekind.draw.Renderer
import kotlin.math.min

/**
 * A doodle on the whole screen. Pinch or double-tap to zoom, drag to look around. The strokes are
 * redrawn at the zoomed size rather than stretched, so lines stay sharp however far you zoom in.
 */
@Composable
fun FullScreenDoodle(d: Drawing, title: String, replay: Boolean, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var zoom by remember(d.id) { mutableFloatStateOf(1f) }
    var pan by remember(d.id) { mutableStateOf(Offset.Zero) }
    val progress = remember(d.id) { Animatable(if (replay) 0f else 1f) }
    var plays by remember(d.id) { mutableIntStateOf(0) }
    if (replay) {
        LaunchedEffect(d.id, plays) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween((d.strokes.sumOf { it.p.size / 2 } * 6).coerceIn(900, 4500), easing = LinearEasing))
        }
    }

    // the outer tap handler swallows touches so nothing underneath can be pressed by accident
    Box(Modifier.fillMaxSize().background(Color(0xFF120D10)).pointerInput(Unit) { detectTapGestures { } }) {
        Canvas(
            Modifier.fillMaxSize().safeDrawingPadding().padding(top = 56.dp, bottom = 56.dp).clipToBounds()
                .semantics { contentDescription = "Doodle full screen" }
                .pointerInput(d.id) {
                    detectTransformGestures { _, move, change, _ ->
                        zoom = (zoom * change).coerceIn(1f, 6f)
                        val limit = min(size.width, size.height) * (zoom - 1f) / 2f
                        pan = Offset((pan.x + move.x).coerceIn(-limit, limit), (pan.y + move.y).coerceIn(-limit, limit))
                    }
                }
                .pointerInput(d.id) {
                    detectTapGestures(onDoubleTap = { if (zoom > 1f) { zoom = 1f; pan = Offset.Zero } else zoom = 2.5f })
                },
        ) {
            val side = min(size.width, size.height) * zoom
            val left = (size.width - side) / 2 + pan.x
            val top = (size.height - side) / 2 + pan.y
            val paper = Path().apply { addRoundRect(RoundRect(left, top, left + side, top + side, CornerRadius(24.dp.toPx()))) }
            clipPath(paper) {
                drawRect(Color(d.bg), Offset(left, top), Size(side, side))
                withTransform({ translate(left, top) }) {
                    drawIntoCanvas {
                        Renderer.draw(it.nativeCanvas, side, d.strokes, d.bg, progress.value)
                        if (progress.value >= 1f) Renderer.stickers(it.nativeCanvas, side, d.stickers)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClose) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
            Text(title, Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (replay) TextButton({ plays++ }) { Text("Replay", color = Color(0xFFFF8FAE), maxLines = 1, softWrap = false) }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 14.dp)) {
            if (d.note.isNotBlank()) {
                Text("“${d.note}”", Modifier.fillMaxWidth(), color = Color.White, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
