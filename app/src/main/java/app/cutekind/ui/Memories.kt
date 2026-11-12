package app.cutekind.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cutekind.data.AppState
import app.cutekind.data.Days

@Composable
fun MemoriesScreen(state: AppState, now: Long, modifier: Modifier = Modifier) {
    var mine by rememberSaveable { mutableStateOf(false) }
    val list = if (mine) state.sent else state.received
    val waiting = state.outbox.mapNotNull { it.drawing?.id }.toSet()
    LazyVerticalGrid(
        GridCells.Adaptive(150.dp), modifier,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Memories", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!mine, { mine = false }, label = { Text("From ${state.partnerLabel}") })
                    FilterChip(mine, { mine = true }, label = { Text("From me") })
                }
            }
        }
        if (list.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    if (mine) "Drawings you send are kept here." else "Drawings from ${state.partnerLabel} are kept here.",
                    Modifier.fillMaxWidth().padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(list, key = { it.id }) { d ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DrawingView(d.strokes, d.bg, Modifier.fillMaxWidth(), stickers = d.stickers, corner = 18.dp)
                Text(
                    if (d.id in waiting) "Not sent yet" else d.note.ifBlank { Days.ago(d.ts, now).replaceFirstChar(Char::uppercase) },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (d.id in waiting) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
