package app.cutekind

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cutekind.data.AppState
import app.cutekind.data.Days
import app.cutekind.data.Feature
import app.cutekind.data.Stroke
import app.cutekind.ui.AppScaffold
import app.cutekind.ui.CuteKindTheme
import app.cutekind.ui.DrawScreen
import app.cutekind.ui.HomeScreen
import app.cutekind.ui.JoinCard
import app.cutekind.ui.MemoriesScreen
import app.cutekind.ui.OnboardingScreen
import app.cutekind.ui.SettingsScreen
import app.cutekind.ui.Tab
import app.cutekind.widget.PartnerWidget
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val now = Samples.NOW

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { CuteKindTheme(dark = dark) { content() } }
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Composable
    private fun Home(state: AppState, onAnswer: (String) -> Unit = {}) =
        AppScaffold(state, Tab.HOME, {}) { m -> HomeScreen(state, now, {}, {}, onAnswer, {}, {}, {}, m) }

    @Test fun onboarding() = shot("01_onboarding") { OnboardingScreen({}, { _, _ -> }, initialName = "Sam") }

    @Test fun homeWaitingForPartner() = shot("02_home_invite") { Home(Samples.fresh) }

    @Test fun homeWithDrawing() = shot("03_home") { Home(Samples.paired) }

    @Test @Config(qualifiers = "w411dp-h1500dp-xhdpi")
    fun homeFullAndAnswerReveal() {
        var state by mutableStateOf(Samples.paired)
        val key = Days.key(now)
        compose.setContent {
            CuteKindTheme { Home(state) { text -> state = state.copy(days = state.days + (key to state.days[key]!!.copy(mine = text))) } }
        }
        compose.onNodeWithText("Maya answered. Answer to reveal it.").assertExists()
        compose.onNodeWithText("Your terrible puns. Never stop.").assertDoesNotExist()
        compose.onRoot().captureRoboImage("screenshots/04_home_question_locked.png")
        compose.onNodeWithText("Your answer").performTextInput("The way you hum when you cook")
        compose.onNodeWithText("Send answer").performClick()
        compose.onNodeWithText("Your terrible puns. Never stop.").assertExists()
        compose.onRoot().captureRoboImage("screenshots/05_home_question_revealed.png")
    }

    @Test fun homeDark() = shot("06_home_dark", dark = true) { Home(Samples.paired.copy(features = mapOf(Feature.SPICY.name to true))) }

    @Test fun drawScreenRecordsStrokes() {
        val strokes = ArrayList<Stroke>()
        compose.setContent {
            CuteKindTheme {
                var list by remember { mutableStateOf(Samples.heartStrokes.take(1)) }
                AppScaffold(Samples.paired, Tab.DRAW, {}) { m ->
                    DrawScreen(list, 0xFFFFE1EA.toInt(), "Miss you", "Maya", { strokes.add(it); list = list + it }, {}, {}, {}, {}, {}, m)
                }
            }
        }
        compose.onNodeWithContentDescription("Drawing canvas").performTouchInput {
            swipe(Offset(width * 0.2f, height * 0.85f), Offset(width * 0.8f, height * 0.85f), 300)
        }
        compose.onNodeWithContentDescription("Drawing canvas").performTouchInput { down(Offset(width * 0.5f, height * 0.1f)); up() }
        assertEquals(2, strokes.size)
        assertTrue("a swipe records many points", strokes[0].p.size > 10)
        assertTrue("stroke is horizontal at 85% height", strokes[0].p.filterIndexed { i, _ -> i % 2 == 1 }.all { it in 1650..1750 })
        assertEquals("a tap is a single dot", 2, strokes[1].p.size)
        compose.onRoot().captureRoboImage("screenshots/07_draw.png")
    }

    @Test fun memories() = shot("08_memories") { AppScaffold(Samples.paired, Tab.MEMORIES, {}) { m -> MemoriesScreen(Samples.paired, now, m) } }

    @Test @Config(qualifiers = "w411dp-h2300dp-xhdpi")
    fun settingsEveryFeatureHasASwitch() {
        var state by mutableStateOf(Samples.paired)
        compose.setContent {
            CuteKindTheme {
                AppScaffold(state, Tab.SETTINGS, {}) { m ->
                    SettingsScreen(state, { f, on -> state = state.copy(features = state.features + (f.name to on)) }, {}, {}, {}, {}, {}, {}, {}, m)
                }
            }
        }
        Feature.entries.forEach { f ->
            val switch = compose.onNode(hasText(f.title) and isToggleable())
            switch.performScrollTo().performClick()
            assertEquals("${f.name} toggles", !f.default, state.on(f))
            switch.performClick()
            assertEquals(f.default, state.on(f))
        }
        compose.onRoot().captureRoboImage("screenshots/09_settings.png")
    }

    @Composable
    private fun Overlay(content: @Composable () -> Unit) =
        Box(Modifier.fillMaxSize().background(Color(0x99000000)).padding(28.dp), contentAlignment = Alignment.Center) { content() }

    @Test fun joinWithCodeFromInsideTheApp() {
        var open by mutableStateOf(false)
        var joined: String? = null
        compose.setContent {
            CuteKindTheme {
                AppScaffold(Samples.fresh, Tab.HOME, {}) { m -> HomeScreen(Samples.fresh, now, {}, {}, {}, {}, { open = true }, {}, m) }
                if (open) Overlay { JoinCard("", Samples.fresh.code, false, { joined = it; open = false }, { open = false }) }
            }
        }
        compose.onNodeWithText("My partner sent me a code instead").performClick()
        compose.onNodeWithText("Join").assertIsNotEnabled()
        compose.onNodeWithText("Couple code").performTextInput("k7qm-2xhd-9trb")
        compose.onNodeWithText("That is your own code. Ask your partner for theirs, or send them yours.").assertExists()
        compose.onNodeWithText("Join").assertIsNotEnabled()
        compose.onNode(hasSetTextAction() and isFocused()).performTextReplacement("abcd-efgh-2345")
        compose.onNodeWithText("Join").assertIsEnabled()
        compose.onRoot().captureRoboImage("screenshots/14_join_dialog.png")
        compose.onNodeWithText("Join").performClick()
        assertEquals("ABCD-EFGH-2345", joined)
    }

    @Test fun inviteLinkPrefillsTheJoinDialog() {
        compose.setContent {
            CuteKindTheme {
                AppScaffold(Samples.paired, Tab.HOME, {}) { m -> HomeScreen(Samples.paired, now, {}, {}, {}, {}, {}, {}, m) }
                Overlay { JoinCard("ABCD-EFGH-2345", Samples.paired.code, true, {}, {}) }
            }
        }
        compose.onNodeWithText("Pair with your partner?").assertExists()
        compose.onNodeWithText("Join").assertIsEnabled()
        compose.onRoot().captureRoboImage("screenshots/15_join_from_link.png")
    }

    @Test fun inviteLinkPrefillsOnboarding() = shot("16_onboarding_from_link") {
        OnboardingScreen({}, { _, _ -> }, initialCode = "ABCD-EFGH-2345")
    }

    @Test fun memoriesTabFollowsItsSwitch() {
        compose.setContent {
            CuteKindTheme { AppScaffold(Samples.paired.copy(features = mapOf(Feature.MEMORIES.name to false)), Tab.HOME, {}) {} }
        }
        compose.onNodeWithText("Memories").assertDoesNotExist()
        compose.onNodeWithText("Settings").assertExists()
    }

    private fun widget(name: String, state: AppState, wDp: Int = 220, hDp: Int = 220) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val d = context.resources.displayMetrics.density
        val view = PartnerWidget.build(context, state, now).apply(context, FrameLayout(context))
        val w = (wDp * d).toInt(); val h = (hDp * d).toInt(); val pad = (24 * d).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w + 2 * pad, h + 2 * pad, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(0xFF5B7C99.toInt()) // stand-in for a wallpaper
        canvas.translate(pad.toFloat(), pad.toFloat())
        view.draw(canvas)
        File("screenshots").mkdirs()
        File("screenshots/$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun widgetStates() {
        widget("10_widget", Samples.paired)
        widget("11_widget_night_wide", Samples.paired.copy(received = Samples.paired.received.drop(1)), wDp = 300, hDp = 220)
        widget("12_widget_empty", Samples.fresh.copy(partnerName = "Maya"))
        widget("13_widget_minimal", Samples.paired.copy(features = listOf(Feature.WIDGET_QUESTION, Feature.WIDGET_CAPTION, Feature.DAYS_COUNTER).associate { it.name to false }), 150, 150)
    }
}
