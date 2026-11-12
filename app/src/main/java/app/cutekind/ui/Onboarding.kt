package app.cutekind.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.cutekind.sync.Wire

@Composable
fun OnboardingScreen(
    onCreate: (name: String) -> Unit,
    onJoin: (name: String, code: String) -> Unit,
    initialName: String = "",
    initialCode: String = "",
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var code by rememberSaveable(initialCode) { mutableStateOf(initialCode) }
    val nameOk = name.isNotBlank()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Icon(Icons.Default.Favorite, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Draw something.\nIt lands on their home screen.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "CuteKind is a widget for two. Whatever one of you draws shows up on the other's phone, with a question for you both every day.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                name, { name = it.take(20) }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Your name") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            Button({ onCreate(name.trim()) }, Modifier.fillMaxWidth().height(52.dp), enabled = nameOk) { Text("Start a new couple") }
            Text(
                "You get a couple code to send to your partner.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(if (initialCode.isNotEmpty()) "Your partner invited you" else "Already have a code from your partner?", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                code, { code = it.uppercase().take(14) }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Couple code") }, placeholder = { Text("XXXX-XXXX-XXXX") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
            )
            OutlinedButton(
                { onJoin(name.trim(), code) }, Modifier.fillMaxWidth().height(52.dp),
                enabled = nameOk && Wire.isValid(code),
            ) { Text("Join my partner") }
        }
    }
}
