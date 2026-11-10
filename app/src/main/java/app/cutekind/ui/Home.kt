package app.cutekind.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cutekind.data.AppState
import app.cutekind.data.Days
import app.cutekind.data.Feature
import app.cutekind.data.Questions
import app.cutekind.sync.Wire

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(1.dp),
    ) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() } }
}

@Composable
fun HomeScreen(
    state: AppState,
    now: Long,
    onDraw: () -> Unit,
    onNudge: () -> Unit,
    onAnswer: (String) -> Unit,
    onShareCode: () -> Unit,
    onAddWidget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latest = state.received.firstOrNull()
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            if (state.partnerName != null) "${state.myName} & ${state.partnerName}" else "Hi ${state.myName}",
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
        )
        val days = Days.daysTogether(state.anniversary, now)?.takeIf { state.on(Feature.DAYS_COUNTER) }
        val streak = Days.streak(state, now).takeIf { state.on(Feature.STREAK) && it > 0 }
        if (days != null || streak != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                days?.let { Chip("Day $it together") }
                streak?.let { Chip(if (it == 1) "1 day streak" else "$it day streak") }
            }
        }

        if (state.partnerName == null) {
            SectionCard {
                Text("Invite your partner", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Send them this code. They install CuteKind, tap Join my partner and type it in.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    Wire.pretty(state.code.orEmpty()), Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    textAlign = TextAlign.Center, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Button(onShareCode, Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Share code")
                }
            }
        }

        SectionCard {
            if (latest != null) {
                DrawingView(
                    latest.strokes, latest.bg, Modifier.fillMaxWidth(),
                    replay = state.on(Feature.REPLAY), replayKey = latest.id,
                    description = "Drawing from ${latest.name}",
                )
                Text(
                    "${latest.name.ifBlank { state.partnerLabel }} drew this ${Days.ago(latest.ts, now)}",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                )
                if (latest.note.isNotBlank()) Text("“${latest.note}”", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Nothing from ${state.partnerLabel} yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Their drawing will appear here and on your home screen widget. Go first?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onDraw, Modifier.weight(1f).height(48.dp)) {
                    Icon(Icons.Default.Create, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(if (latest != null) "Draw back" else "Draw", maxLines = 1)
                }
                if (state.on(Feature.NUDGES)) {
                    FilledTonalButton(onNudge, Modifier.height(48.dp)) {
                        Icon(Icons.Default.Favorite, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Send a heart", maxLines = 1)
                    }
                }
            }
            if (state.on(Feature.NUDGES) && state.lastNudgeTs > 0 && now - state.lastNudgeTs < 86_400_000L) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Favorite, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("${state.partnerLabel} is thinking of you", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (state.on(Feature.DAILY_QUESTION)) QuestionCard(state, now, onAnswer)

        OutlinedButton(onAddWidget, Modifier.fillMaxWidth()) { Text("Add the widget to my home screen") }

        val status = state.lastError ?: if (state.outbox.isNotEmpty()) "Sending…" else null
        if (status != null) {
            Text(status, Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QuestionCard(state: AppState, now: Long, onAnswer: (String) -> Unit) {
    val dayKey = Days.key(now)
    val question = Questions.forDay(Days.epochDay(now), state.on(Feature.SPICY))
    val answers = state.days[dayKey]
    val mine = answers?.mine
    val theirs = answers?.partner
    var draft by rememberSaveable(dayKey) { mutableStateOf("") }
    SectionCard {
        Text(if (question.spicy) "TODAY'S QUESTION · SPICY" else "TODAY'S QUESTION",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(question.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

        if (mine == null) {
            OutlinedTextField(
                draft, { draft = it.take(280) }, Modifier.fillMaxWidth(),
                placeholder = { Text("Your answer") }, maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton({ onAnswer(draft.trim()); draft = "" }, enabled = draft.isNotBlank()) { Text("Send answer") }
            }
        } else {
            AnswerLine("You", mine)
        }

        when {
            theirs == null -> Text("Waiting for ${state.partnerLabel}'s answer",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            mine == null && state.on(Feature.REVEAL_LOCK) -> Row(
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Default.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Text("${state.partnerLabel} answered. Answer to reveal it.",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            else -> {
                val other = answers.partnerQid?.takeIf { it != question.id }?.let(Questions::byId)
                if (other != null) Text("They were asked: ${other.text}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AnswerLine(state.partnerLabel, theirs)
            }
        }
    }
}

@Composable
private fun AnswerLine(who: String, text: String) {
    Column {
        Text(who, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
