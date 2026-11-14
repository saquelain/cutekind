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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
import app.cutekind.data.Sticker
import app.cutekind.data.Stroke
import app.cutekind.draw.Gallery
import app.cutekind.draw.StickerMaker
import app.cutekind.ui.AppScaffold
import app.cutekind.ui.CuteKindTheme
import app.cutekind.ui.DrawScreen
import app.cutekind.ui.FullScreenDoodle
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

    @Composable
    private fun Draw(onStroke: (Stroke) -> Unit = {}) {
        var list by remember { mutableStateOf(Samples.heartStrokes.take(1)) }
        AppScaffold(Samples.paired, Tab.DRAW, {}) { m ->
            DrawScreen(list, 0xFFFFE1EA.toInt(), "Miss you", "Maya", { onStroke(it); list = list + it }, {}, {}, {}, {}, {}, m)
        }
    }

    @Test fun drawScreenRecordsStrokes() {
        val strokes = ArrayList<Stroke>()
        compose.setContent { CuteKindTheme { Draw(strokes::add) } }
        compose.onNodeWithContentDescription("Drawing canvas").performTouchInput {
            swipe(Offset(width * 0.2f, height * 0.85f), Offset(width * 0.8f, height * 0.85f), 300)
        }
        compose.onNodeWithContentDescription("Drawing canvas").performTouchInput { down(Offset(width * 0.5f, height * 0.1f)); up() }
        assertEquals(2, strokes.size)
        assertEquals("a straight swipe is stored as just its two ends", 4, strokes[0].p.size)
        assertTrue("stroke runs left to right", strokes[0].p[0] in 350..450 && strokes[0].p[2] in 1550..1650)
        assertTrue("stroke is horizontal at 85% height", strokes[0].p.filterIndexed { i, _ -> i % 2 == 1 }.all { it in 1650..1750 })
        assertEquals("a tap is a single dot", 2, strokes[1].p.size)
        compose.onRoot().captureRoboImage("screenshots/07_draw.png")
    }

    @Test fun stickersCanBeAddedMovedResizedAndRemoved() {
        var stickers by mutableStateOf(listOf<Sticker>())
        val strokes = ArrayList<Stroke>()
        compose.setContent {
            CuteKindTheme {
                AppScaffold(Samples.paired, Tab.DRAW, {}) { m ->
                    DrawScreen(Samples.heartStrokes.take(1), 0xFFFFFDF8.toInt(), "", "Maya", { strokes.add(it) }, {}, {}, {}, {}, {}, m,
                        stickers = stickers, onStickers = { stickers = it })
                }
            }
        }
        val canvas = compose.onNodeWithContentDescription("Drawing canvas")
        compose.onNodeWithContentDescription("Sticker 🌙").performScrollTo().performClick()
        assertEquals(listOf("🌙"), stickers.map { it.t })
        assertEquals(1000, stickers[0].x)

        // dragging on the sticker moves it and draws nothing
        canvas.performTouchInput { swipe(center, Offset(width * 0.8f, height * 0.25f), 300) }
        assertTrue("moved right and up: ${stickers[0]}", stickers[0].x in 1550..1650 && stickers[0].y in 450..550)
        assertTrue("no stroke drawn while moving a sticker", strokes.isEmpty())

        val before = stickers[0].s
        compose.onNodeWithText("Bigger").performClick()
        assertTrue(stickers[0].s > before)
        compose.onNodeWithText("Turn").performClick()
        assertEquals(15f, stickers[0].r)

        // words become a sticker in the current pen colour
        compose.onNodeWithText("Text").performScrollTo().performClick()
        compose.onNodeWithText("Words to put on the drawing").performTextInput("miss u")
        compose.onNodeWithText("Add").performClick()
        assertEquals(listOf("e", "t"), stickers.map { it.k })
        assertEquals("miss u", stickers[1].t)
        compose.onNodeWithText("Smaller").performScrollTo()
        compose.onRoot().captureRoboImage("screenshots/21_draw_stickers.png")

        compose.onNodeWithText("Remove").performClick()
        assertEquals(listOf("🌙"), stickers.map { it.t })
        compose.onNodeWithText("Remove").assertDoesNotExist()

        // drawing away from any sticker still draws
        canvas.performTouchInput { swipe(Offset(width * 0.1f, height * 0.9f), Offset(width * 0.5f, height * 0.9f), 200) }
        assertEquals(1, strokes.size)
    }

    @Test fun photoStickersAreShrunkToFitTheRelay() {
        val rnd = java.util.Random(1)
        val photo = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888).apply {
            val px = IntArray(1200 * 900) { 0xFF000000.toInt() or rnd.nextInt(0xFFFFFF) } // noise: the worst case for compression
            setPixels(px, 0, 1200, 0, 0, 1200, 900)
        }
        val data = StickerMaker.encode(photo)
        val bytes = java.util.Base64.getDecoder().decode(data)
        assertTrue("fits the budget: ${bytes.size}", bytes.size <= StickerMaker.MAX_BYTES)
        val back = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertEquals("keeps its 4:3 shape", 4f / 3f, back.width.toFloat() / back.height, 0.05f)

        // an ordinary photo is not shrunk below 640 px: that is what made stickers look blurry before
        val ordinary = Bitmap.createScaledBitmap(Samples.photo(), 1600, 1200, true)
        val kept = java.util.Base64.getDecoder().decode(StickerMaker.encode(ordinary)).let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
        assertEquals(640, kept.width); assertEquals(480, kept.height)

        // and a drawing carrying three of them still fits in the relay's message limit
        val d = Samples.drawing("p", "Sam", now).copy(stickers = List(3) { Sticker("i", data, 1000, 1000, 0.4f) })
        val env = app.cutekind.data.Envelope(id = "x", from = "a", name = "Sam", ts = now, type = "draw", drawing = d)
        val keys = app.cutekind.sync.Wire.keys("K7QM2XHD9TRB")
        val lines = app.cutekind.sync.Wire.encode(env, keys)
        assertTrue("three worst-case photos stay inside one burst of relay messages: ${lines.size}", lines.size <= 45)
        assertEquals(env, app.cutekind.sync.Wire.decode(lines.joinToString("") { it.substringAfterLast('|') }, keys))
        widgetShot("22_widget_stickers", Samples.paired.copy(received = listOf(Samples.drawing("s", "Maya", now, stickers = listOf(
            Sticker("i", StickerMaker.encode(Samples.photo()), 560, 620, 0.42f, -8f),
            Sticker("t", "miss u", 1350, 1500, 0.13f, 6f, 0xFFE8507A.toInt()),
            Sticker("e", "🌙", 1500, 520, 0.22f),
        )))))
    }

    @Test fun tappingADoodleOpensItFullScreen() {
        var open by mutableStateOf<app.cutekind.data.Drawing?>(null)
        val state = Samples.paired.copy(received = listOf(Samples.drawing("s", "Maya", now - 3_600_000L, note = "Miss you, sleepyhead", stickers = listOf(
            Sticker("i", StickerMaker.encode(Samples.photo()), 560, 620, 0.42f, -8f),
            Sticker("t", "miss u", 1350, 1500, 0.13f, 6f, 0xFFE8507A.toInt()),
        ))) + Samples.paired.received.drop(1))
        compose.setContent {
            CuteKindTheme {
                AppScaffold(state, Tab.HOME, {}) { m -> HomeScreen(state, now, {}, {}, {}, {}, {}, {}, m, onOpen = { open = it }) }
                open?.let { FullScreenDoodle(it, "Maya · today", replay = true, onClose = { open = null }) }
            }
        }
        compose.onNodeWithContentDescription("Drawing from Maya").performClick()
        assertEquals("s", open?.id)
        compose.onNodeWithContentDescription("Doodle full screen").assertIsDisplayed()
        compose.onNodeWithText("Replay").assertIsDisplayed()
        compose.onRoot().captureRoboImage("screenshots/23_full_screen.png")
        compose.onNodeWithContentDescription("Doodle full screen").performTouchInput { doubleClick() }
        compose.onRoot().captureRoboImage("screenshots/24_full_screen_zoomed.png")
        compose.onNodeWithContentDescription("Close").performClick()
        assertEquals(null, open)
        compose.onNodeWithContentDescription("Doodle full screen").assertDoesNotExist()
    }

    @Test fun memoriesOpenFullScreenToo() {
        var opened: String? = null
        compose.setContent { CuteKindTheme { AppScaffold(Samples.paired, Tab.MEMORIES, {}) { m -> MemoriesScreen(Samples.paired, now, m, onOpen = { opened = it.id }) } } }
        compose.onAllNodesWithContentDescription("Drawing from Maya")[1].performClick()
        assertEquals("r2", opened)
    }

    @Test fun drawingsAreSavedToTheCuteKindAlbum() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val d = Samples.paired.received.first()
        val saved = java.io.ByteArrayOutputStream()
        val resolver = org.robolectric.Shadows.shadowOf(context.contentResolver)
        // Robolectric hands back the next row of the images table; give that row somewhere to write
        resolver.registerOutputStream(android.net.Uri.parse(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString() + "/1"), saved)
        assertTrue(Gallery.save(context, d))
        val row = resolver.insertStatements.single().contentValues
        assertEquals("Pictures/CuteKind", row.getAsString(android.provider.MediaStore.Images.Media.RELATIVE_PATH))
        assertEquals("image/png", row.getAsString(android.provider.MediaStore.Images.Media.MIME_TYPE))
        assertTrue(row.getAsString(android.provider.MediaStore.Images.Media.DISPLAY_NAME).matches(Regex("CuteKind_\\d{8}_\\d{6}_Maya\\.png")))
        val png = android.graphics.BitmapFactory.decodeByteArray(saved.toByteArray(), 0, saved.size())
        assertEquals(1080, png.width); assertEquals(1080, png.height)
        assertEquals("published, not left pending", 0, resolver.updateStatements.single().contentValues.getAsInteger(android.provider.MediaStore.Images.Media.IS_PENDING))
    }

    // Narrow phone with large text: every tool stays reachable and no label is cut mid-word
    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi", fontScale = 1.3f)
    fun drawOnSmallPhoneWithLargeText() {
        compose.setContent { CuteKindTheme { Draw() } }
        listOf("Eraser", "Undo", "Clear").forEach { compose.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
        compose.onNodeWithText("Send to Maya's home screen").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Clear").performScrollTo()
        compose.onRoot().captureRoboImage("screenshots/17_draw_small_phone.png")
    }

    @Test @Config(qualifiers = "w780dp-h360dp-xhdpi")
    fun drawLandscape() = shot("18_draw_landscape") { Draw() }

    @Test @Config(qualifiers = "w840dp-h1100dp-xhdpi")
    fun homeTablet() = shot("19_home_tablet") { Home(Samples.paired) }

    @Test fun homeShowsWhyADrawingHasNotGoneOut() {
        val stuck = Samples.paired.copy(
            lastError = "Not sent yet: the relay server is limiting this network (limit reached: daily message quota reached). Will keep retrying.",
        )
        var retried = 0
        compose.setContent {
            CuteKindTheme { AppScaffold(stuck, Tab.HOME, {}) { m -> HomeScreen(stuck, now, {}, {}, {}, {}, {}, {}, m, onRetry = { retried++ }) } }
        }
        compose.onNodeWithText("Try again").performClick()
        assertEquals(1, retried)
        compose.onRoot().captureRoboImage("screenshots/20_home_not_sent.png")
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

    private fun widgetShot(name: String, state: AppState) = widget(name, state)

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
