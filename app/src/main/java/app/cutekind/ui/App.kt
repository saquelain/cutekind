package app.cutekind.ui

import androidx.compose.foundation.layout.fillMaxSize
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
                    NavigationBarItem(t == tab, { onTab(t) }, icon = { Icon(t.icon, null) }, label = { Text(t.label) })
                }
            }
        },
    ) { padding -> content(Modifier.fillMaxSize().padding(padding)) }
}
