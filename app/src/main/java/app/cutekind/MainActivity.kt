package app.cutekind

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.cutekind.data.Feature
import app.cutekind.sync.Sync
import app.cutekind.sync.Wire
import app.cutekind.ui.AppScaffold
import app.cutekind.ui.CuteKindTheme
import app.cutekind.ui.DrawScreen
import app.cutekind.ui.HomeScreen
import app.cutekind.ui.MemoriesScreen
import app.cutekind.ui.OnboardingScreen
import app.cutekind.ui.SettingsScreen
import app.cutekind.ui.Tab
import app.cutekind.widget.PartnerWidget
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Sync.schedule(this)
        setContent {
            CuteKindTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
                val snackbar = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

                // while the app is open, check for new drawings every 30 seconds
                val owner = LocalLifecycleOwner.current
                LaunchedEffect(state.paired) {
                    if (!state.paired) return@LaunchedEffect
                    owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        while (true) { now = System.currentTimeMillis(); vm.refresh(); delay(30_000) }
                    }
                }
                LaunchedEffect(state.paired, state.on(Feature.NOTIFICATIONS)) {
                    if (state.paired && state.on(Feature.NOTIFICATIONS) && Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }

                if (!state.paired) {
                    OnboardingScreen(onCreate = vm::createCouple, onJoin = vm::join, initialName = state.myName)
                } else {
                    if (tab == Tab.MEMORIES && !state.on(Feature.MEMORIES)) tab = Tab.HOME
                    AppScaffold(state, tab, { tab = it }, snackbar) { m ->
                        when (tab) {
                            Tab.HOME -> HomeScreen(
                                state, now, onDraw = { tab = Tab.DRAW },
                                onNudge = { vm.nudge(); scope.launch { snackbar.showSnackbar("Heart sent to ${state.partnerLabel}") } },
                                onAnswer = vm::answer, onShareCode = ::shareCode, onAddWidget = ::addWidget, modifier = m,
                            )
                            Tab.DRAW -> DrawScreen(
                                vm.strokes, vm.paper, vm.note, state.partnerLabel,
                                onStroke = { vm.strokes.add(it) }, onUndo = { vm.strokes.removeLastOrNull() },
                                onClear = { vm.strokes.clear() }, onBg = { vm.paper = it }, onNote = { vm.note = it },
                                onSend = {
                                    vm.sendDrawing(); tab = Tab.HOME
                                    scope.launch { snackbar.showSnackbar("Sent. It will show on ${state.partnerLabel}'s home screen") }
                                },
                                modifier = m,
                            )
                            Tab.MEMORIES -> MemoriesScreen(state, now, m)
                            Tab.SETTINGS -> SettingsScreen(
                                state, onFeature = vm::setFeature, onName = vm::setName, onAnniversary = vm::setAnniversary,
                                onRelay = vm::setRelay, onShareCode = ::shareCode, onAddWidget = ::addWidget, onUnpair = vm::unpair, modifier = m,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() { super.onStart(); Sync.appInForeground = true }
    override fun onStop() { super.onStop(); Sync.appInForeground = false }

    private fun shareCode() {
        val code = Wire.pretty(vm.state.value.code ?: return)
        val text = "Join me on CuteKind so we can draw on each other's home screens. Our couple code: $code"
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share couple code"))
    }

    private fun addWidget() {
        val manager = AppWidgetManager.getInstance(this)
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(ComponentName(this, PartnerWidget::class.java), null, null)
        }
    }
}
