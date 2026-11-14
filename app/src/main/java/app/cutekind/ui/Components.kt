package app.cutekind.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cutekind.data.Sticker
import app.cutekind.data.Stroke
import app.cutekind.draw.Renderer

/** Square view of a drawing. With [replay] it draws itself stroke by stroke; tap to watch again. */
@Composable
fun DrawingView(
    strokes: List<Stroke>,
    bg: Int,
    modifier: Modifier = Modifier,
    stickers: List<Sticker> = emptyList(),
    replay: Boolean = false,
    replayKey: Any? = null,
    corner: Dp = 24.dp,
    description: String = "Drawing",
    onClick: (() -> Unit)? = null,
) {
    val progress = remember { Animatable(1f) }
    var plays by remember { mutableIntStateOf(0) }
    if (replay) {
        LaunchedEffect(replayKey, plays) {
            val points = strokes.sumOf { it.p.size / 2 }
            progress.snapTo(0f)
            progress.animateTo(1f, tween((points * 6).coerceIn(900, 4500), easing = LinearEasing))
        }
    }
    val shape = RoundedCornerShape(corner)
    Canvas(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape)
            .then(when {
                onClick != null -> Modifier.clickable(onClickLabel = "Open full screen", onClick = onClick)
                replay -> Modifier.clickable(onClickLabel = "Replay drawing") { plays++ }
                else -> Modifier
            })
            .semantics { contentDescription = description },
    ) {
        drawRect(Color(bg))
        val shown = if (replay) progress.value else 1f
        drawIntoCanvas {
            Renderer.draw(it.nativeCanvas, size.width, strokes, bg, shown)
            // stickers pop in once the strokes have finished drawing
            if (shown >= 1f) Renderer.stickers(it.nativeCanvas, size.width, stickers)
        }
    }
}

@Composable
fun Chip(text: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
