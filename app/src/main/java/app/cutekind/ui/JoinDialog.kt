package app.cutekind.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.cutekind.sync.Wire

/** Join a partner's couple from inside the app: typed by hand, or pre-filled from a tapped invite link. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinDialog(
    initialCode: String,
    currentCode: String?,
    losesHistory: Boolean,
    onJoin: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) { JoinCard(initialCode, currentCode, losesHistory, onJoin, onDismiss) }
}

@Composable
fun JoinCard(
    initialCode: String,
    currentCode: String?,
    losesHistory: Boolean,
    onJoin: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var code by rememberSaveable(initialCode) { mutableStateOf(initialCode) }
    val same = Wire.isValid(code) && Wire.normalize(code) == currentCode
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 6.dp) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (initialCode.isNotEmpty()) "Pair with your partner?" else "Join your partner",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                if (initialCode.isNotEmpty()) "You opened an invite link. Joining pairs this phone with the person who sent it."
                else "Enter the couple code your partner sent you.",
            )
            OutlinedTextField(
                code, { code = it.uppercase().take(14) }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Couple code") }, placeholder = { Text("XXXX-XXXX-XXXX") },
                isError = same,
                supportingText = if (same) ({ Text("That is your own code. Ask your partner for theirs, or send them yours.") }) else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
            )
            Text(
                if (losesHistory) "Your current code stops working on this phone, and the drawings and answers saved under it are removed."
                else "Your own code is replaced by theirs. You only need one code between you.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onDismiss) { Text("Cancel") }
                TextButton({ onJoin(code) }, enabled = Wire.isValid(code) && !same) { Text("Join") }
            }
        }
    }
}
