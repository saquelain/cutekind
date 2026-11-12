package app.cutekind

import app.cutekind.data.AppState
import app.cutekind.data.DayAnswers
import app.cutekind.data.Days
import app.cutekind.data.Drawing
import app.cutekind.data.Stroke
import app.cutekind.data.UNIT
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

object Samples {
    const val NOW = 1_794_300_000_000L // fixed clock so screenshots are stable
    private const val DAY = 86_400_000L

    fun heart(color: Int, scale: Float = 1f, cx: Int = UNIT / 2, cy: Int = UNIT / 2, width: Float = 0.022f): Stroke {
        val pts = ArrayList<Int>()
        for (i in 0..80) {
            val t = i / 80.0 * 2 * Math.PI
            val x = 16 * sin(t).pow(3)
            val y = 13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t)
            pts.add((cx + x * 42 * scale).toInt()); pts.add((cy - y * 42 * scale - 60 * scale).toInt())
        }
        return Stroke(color, width, pts)
    }

    fun wave(color: Int, y: Int, width: Float = 0.014f): Stroke =
        Stroke(color, width, (0..40).flatMap { i -> listOf(300 + i * 35, (y + 60 * sin(i / 3.0)).toInt()) })

    fun dot(color: Int, x: Int, y: Int) = Stroke(color, 0.04f, listOf(x, y))

    val heartStrokes = listOf(
        heart(0xFFE8507A.toInt()), heart(0xFFFB8C00.toInt(), 0.45f, 1500, 520, 0.012f),
        wave(0xFF3B2A33.toInt(), 1650), dot(0xFF1E88E5.toInt(), 380, 420), dot(0xFFFDD835.toInt(), 520, 300),
    )

    fun drawing(id: String, name: String, ts: Long, bg: Int = 0xFFFFFDF8.toInt(), strokes: List<Stroke> = heartStrokes, note: String = "", stickers: List<app.cutekind.data.Sticker> = emptyList()) =
        Drawing(id, "dev-$name", name, ts, bg, strokes, note, stickers)

    /** A stand-in "photo": a sunset gradient with a sun, for photo-sticker tests. */
    fun photo(): android.graphics.Bitmap {
        val b = android.graphics.Bitmap.createBitmap(400, 300, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.shader = android.graphics.LinearGradient(0f, 0f, 0f, 300f, 0xFF3A3F8F.toInt(), 0xFFFF9A62.toInt(), android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 400f, 300f, p)
        p.shader = null; p.color = 0xFFFFE08A.toInt(); c.drawCircle(280f, 190f, 60f, p)
        p.color = 0xFF22264F.toInt(); c.drawRect(0f, 240f, 400f, 300f, p)
        return b
    }

    val fresh = AppState(deviceId = "me", myName = "Sam", code = "K7QM2XHD9TRB")

    val paired = fresh.copy(
        partnerName = "Maya",
        anniversary = Days.epochDay(NOW) - 213,
        received = listOf(
            drawing("r1", "Maya", NOW - 2 * 3_600_000L, note = "Miss you, sleepyhead"),
            drawing("r2", "Maya", NOW - DAY, 0xFF1F1B2E.toInt(), listOf(heart(0xFFFFFFFF.toInt(), 0.8f), wave(0xFFFDD835.toInt(), 1600))),
            drawing("r3", "Maya", NOW - 2 * DAY, 0xFFDDF1FF.toInt(), listOf(wave(0xFF1E88E5.toInt(), 700, 0.03f), wave(0xFF1E88E5.toInt(), 1000, 0.03f), wave(0xFF8E24AA.toInt(), 1300, 0.03f))),
            drawing("r4", "Maya", NOW - 4 * DAY, 0xFFE0F5E6.toInt(), listOf(heart(0xFF43A047.toInt(), 0.6f))),
        ),
        sent = listOf(
            drawing("s1", "Sam", NOW - 3_600_000L, 0xFFFFE1EA.toInt(), listOf(heart(0xFFE53935.toInt(), 0.9f))),
            drawing("s2", "Sam", NOW - DAY, strokes = listOf(wave(0xFF3B2A33.toInt(), 1000))),
            drawing("s3", "Sam", NOW - 2 * DAY),
        ),
        lastNudgeTs = NOW - 600_000L,
        days = mapOf(Days.key(NOW) to DayAnswers(qid = 0, partner = "Your terrible puns. Never stop.", partnerQid = null)),
    )
}
