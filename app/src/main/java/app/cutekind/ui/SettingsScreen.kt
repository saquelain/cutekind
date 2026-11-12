package app.cutekind.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.cutekind.data.AppState
import app.cutekind.data.Feature
import app.cutekind.sync.Wire
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: AppState,
    onFeature: (Feature, Boolean) -> Unit,
    onName: (String) -> Unit,
    onAnniversary: (Long?) -> Unit,
    onRelay: (String) -> Unit,
    onShareCode: () -> Unit,
    onJoinCode: () -> Unit,
    onAddWidget: () -> Unit,
    onUnpair: () -> Unit,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
    onSync: () -> Unit = {},
    backgroundAllowed: Boolean = true,
    onAllowBackground: () -> Unit = {},
) {
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var confirmUnpair by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        SectionCard {
            Text("The two of you", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(state.myName, { onName(it.take(20)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Your name") })
            LabelValue("Partner", state.partnerName ?: "Not joined yet")
            Row(verticalAlignment = Alignment.CenterVertically) {
                LabelValue("Couple code", Wire.pretty(state.code.orEmpty()), Modifier.weight(1f))
                TextButton(onShareCode) { Text("Share") }
            }
            OutlinedButton(onJoinCode, Modifier.fillMaxWidth()) { Text("Join with my partner's code") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                LabelValue(
                    "Together since",
                    state.anniversary?.let { LocalDate.ofEpochDay(it).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) } ?: "Not set",
                    Modifier.weight(1f),
                )
                TextButton({ pickDate = true }) { Text(if (state.anniversary == null) "Set date" else "Change") }
            }
        }

        SectionCard {
            Text("Connection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val mins = (now - state.lastSyncTs) / 60_000
            LabelValue("Last checked for drawings", when {
                state.lastSyncTs == 0L -> "Never"
                mins < 1 -> "Just now"
                mins < 60 -> "$mins min ago"
                else -> "${mins / 60} h ago"
            })
            LabelValue("Instant delivery", when {
                !state.on(Feature.INSTANT) -> "Off"
                state.live -> "Connected"
                else -> "Reconnecting…"
            })
            LabelValue("Waiting to be sent", if (state.outbox.isEmpty()) "Nothing" else "${state.outbox.size}")
            state.lastError?.let { Text(it.replaceFirstChar(Char::uppercase), color = MaterialTheme.colorScheme.error) }
            OutlinedButton(onSync, Modifier.fillMaxWidth()) { Text("Sync now") }
            if (state.on(Feature.INSTANT) && !backgroundAllowed) {
                Text("Your phone's battery saver can pause instant delivery while the screen is off.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onAllowBackground, Modifier.fillMaxWidth()) { Text("Let CuteKind run in the background") }
            }
        }

        SectionCard {
            Text("Features", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Feature.entries.forEachIndexed { i, f ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().toggleable(state.on(f), role = Role.Switch) { onFeature(f, it) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(f.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(f.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(state.on(f), null)
                }
            }
        }

        SectionCard {
            Text("Widget", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Your partner's latest drawing lives on your home screen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onAddWidget, Modifier.fillMaxWidth()) { Text("Add the widget to my home screen") }
        }

        SectionCard {
            Text("Advanced", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                state.relay, { onRelay(it.trim()) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Relay server") },
                supportingText = { Text("Drawings are encrypted with your couple code before they leave the phone. Both of you must use the same server.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
            )
            TextButton({ confirmUnpair = true }) { Text("Unpair and erase everything", color = MaterialTheme.colorScheme.error) }
        }
    }

    if (pickDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = state.anniversary?.let { it * 86_400_000L })
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = { TextButton({ onAnniversary(picker.selectedDateMillis?.let { it / 86_400_000L }); pickDate = false }) { Text("Save") } },
            dismissButton = { TextButton({ pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(picker) }
    }
    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text("Unpair?") },
            text = { Text("This removes the couple code, all drawings and all answers from this phone. It cannot be undone.") },
            confirmButton = { TextButton({ confirmUnpair = false; onUnpair() }) { Text("Unpair", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ confirmUnpair = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun LabelValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
