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
import androidx.activity.result.PickVisualMediaRequest
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
import app.cutekind.data.Days
import app.cutekind.data.Feature
import app.cutekind.sync.LiveService
import app.cutekind.sync.Sync
import app.cutekind.sync.Wire
import app.cutekind.ui.AppScaffold
import app.cutekind.ui.CuteKindTheme
import app.cutekind.ui.DrawScreen
import app.cutekind.ui.FullScreenDoodle
import app.cutekind.ui.HomeScreen
import app.cutekind.ui.JoinDialog
import app.cutekind.ui.MemoriesScreen
import app.cutekind.ui.OnboardingScreen
import app.cutekind.ui.SettingsScreen
import app.cutekind.ui.Tab
import app.cutekind.widget.PartnerWidget
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    /** Id of the doodle shown full screen, or null. */
    private var viewing by mutableStateOf<String?>(null)

    private fun openFromIntent(intent: Intent?) {
        intent?.dataString?.let(vm::onLink)
        if (intent?.getBooleanExtra(EXTRA_VIEW_LATEST, false) == true) viewing = vm.state.value.received.firstOrNull()?.id
    }

    companion object { const val EXTRA_VIEW_LATEST = "view_latest" }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Sync.schedule(this)
        if (savedInstanceState == null) openFromIntent(intent)
        setContent {
            CuteKindTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
                val snackbar = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::addPhotoSticker) }
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

                // Battery savers cut the live connection when the phone sleeps; ask once to be left running.
                LaunchedEffect(state.paired, state.on(Feature.INSTANT)) {
                    if (state.paired && state.on(Feature.INSTANT) && !state.askedBattery && !LiveService.unrestricted(this@MainActivity)) {
                        vm.markBatteryAsked()
                        allowBackground()
                    }
                }

                if (!state.paired) {
                    OnboardingScreen(onCreate = vm::createCouple, onJoin = vm::join, initialName = state.myName, initialCode = vm.joinPrompt.orEmpty())
                } else {
                    if (tab == Tab.MEMORIES && !state.on(Feature.MEMORIES)) tab = Tab.HOME
                    AppScaffold(state, tab, { tab = it }, snackbar) { m ->
                        when (tab) {
                            Tab.HOME -> HomeScreen(
                                state, now, onDraw = { tab = Tab.DRAW },
                                onNudge = { vm.nudge(); scope.launch { snackbar.showSnackbar("Heart sent to ${state.partnerLabel}") } },
                                onAnswer = vm::answer, onShareCode = ::shareCode, onJoinCode = { vm.joinPrompt = "" }, onAddWidget = ::addWidget, modifier = m, onRetry = { vm.refresh() }, onOpen = { viewing = it.id },
                            )
                            Tab.DRAW -> DrawScreen(
                                vm.strokes, vm.paper, vm.note, state.partnerLabel,
                                onStroke = { vm.strokes.add(it) }, onUndo = { vm.strokes.removeLastOrNull() },
                                onClear = { vm.strokes.clear(); vm.stickers = emptyList() },
                                stickers = vm.stickers, onStickers = { vm.stickers = it }, stickersEnabled = state.on(Feature.STICKERS),
                                onPickPhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, onBg = { vm.paper = it }, onNote = { vm.note = it },
                                onSend = {
                                    vm.sendDrawing(); tab = Tab.HOME
                                    scope.launch { snackbar.showSnackbar("Sent. It will show on ${state.partnerLabel}'s home screen") }
                                },
                                modifier = m,
                            )
                            Tab.MEMORIES -> MemoriesScreen(state, now, m, onOpen = { viewing = it.id })
                            Tab.SETTINGS -> SettingsScreen(
                                state, onFeature = vm::setFeature, onName = vm::setName, onAnniversary = vm::setAnniversary,
                                onRelay = vm::setRelay, onShareCode = ::shareCode, onJoinCode = { vm.joinPrompt = "" }, onAddWidget = ::addWidget, onUnpair = vm::unpair, modifier = m, now = now, onSync = { now = System.currentTimeMillis(); vm.refresh() },
                                backgroundAllowed = LiveService.unrestricted(this@MainActivity), onAllowBackground = ::allowBackground,
                            )
                        }
                    }
                    (state.received + state.sent).firstOrNull { it.id == viewing }?.let { d ->
                        val mine = d.from == state.deviceId
                        FullScreenDoodle(
                            d, title = "${if (mine) "You" else d.name.ifBlank { state.partnerLabel }} · ${Days.ago(d.ts, now)}",
                            replay = state.on(Feature.REPLAY), onClose = { viewing = null },
                        )
                    }
                    vm.joinPrompt?.let { prefill ->
                        JoinDialog(
                            initialCode = prefill, currentCode = state.code,
                            losesHistory = state.received.isNotEmpty() || state.sent.isNotEmpty(),
                            onJoin = { code -> vm.join(state.myName, code); tab = Tab.HOME },
                            onDismiss = { vm.joinPrompt = null },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openFromIntent(intent)
    }

    override fun onStart() { super.onStart(); Sync.appInForeground = true; Sync.schedule(this) }
    override fun onStop() { super.onStop(); Sync.appInForeground = false }

    private fun shareCode() {
        val code = vm.state.value.code ?: return
        val text = "Join me on CuteKind so we can draw on each other's home screens.\n\n" +
            "Tap to pair with me: ${Wire.link(code)}\n\n" +
            "Or open the app and enter our couple code: ${Wire.pretty(code)}"
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share invite"))
    }

    @android.annotation.SuppressLint("BatteryLife")
    private fun allowBackground() {
        runCatching {
            startActivity(Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:$packageName")))
        }
    }

    private fun addWidget() {
        val manager = AppWidgetManager.getInstance(this)
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(ComponentName(this, PartnerWidget::class.java), null, null)
        }
    }
}
