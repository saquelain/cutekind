package app.cutekind.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import app.cutekind.data.AppState
import app.cutekind.data.Feature

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    DRAW("Draw", Icons.Default.Create),
    MEMORIES("Memories", Icons.Default.Star),
    SETTINGS("Settings", Icons.Default.Settings),
}

@Composable
fun AppScaffold(
    state: AppState,
    tab: Tab,
    onTab: (Tab) -> Unit,
    snackbar: SnackbarHostState? = null,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { snackbar?.let { SnackbarHost(it) } },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.filter { it != Tab.MEMORIES || state.on(Feature.MEMORIES) }.forEach { t ->
                    NavigationBarItem(t == tab, { onTab(t) }, icon = { Icon(t.icon, null) }, label = {
                        // one line always; very large system text is capped here so four tabs still fit
                        val size = with(LocalDensity.current) { (12.dp * fontScale.coerceAtMost(1.15f)).toSp() }
                        Text(t.label, fontSize = size, maxLines = 1, softWrap = false)
                    })
                }
            }
        },
    ) { padding ->
        // imePadding shrinks the content when the keyboard opens, so the focused field scrolls into view;
        // the width cap keeps tablets and landscape from stretching everything edge to edge.
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            content(Modifier.fillMaxSize().widthIn(max = 720.dp))
        }
    }
}
