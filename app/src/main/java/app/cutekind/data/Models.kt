package app.cutekind.data

import kotlinx.serialization.Serializable

/** Coordinates are stored as integers in a square 0..UNIT space so drawings scale to any size. */
const val UNIT = 2000

@Serializable
data class Stroke(
    val c: Int,                 // ARGB colour
    val w: Float,               // width as a fraction of the canvas side
    val p: List<Int>,           // x0,y0,x1,y1,... in 0..UNIT
    val e: Boolean = false,     // eraser stroke (painted in the background colour)
)

/** Something placed on top of the strokes: an emoji, a line of text, or a small picture. */
@Serializable
data class Sticker(
    val k: String,              // e = emoji, t = text, i = image
    val t: String,              // the emoji or text; for an image, base64 WebP
    val x: Int,                 // centre, in 0..UNIT
    val y: Int,
    val s: Float,               // size as a fraction of the canvas side (text height, or image width)
    val r: Float = 0f,          // rotation in degrees
    val c: Int = 0,             // text colour
)

@Serializable
data class Drawing(
    val id: String,
    val from: String,
    val name: String,
    val ts: Long,
    val bg: Int,
    val strokes: List<Stroke>,
    val note: String = "",
    val stickers: List<Sticker> = emptyList(),
)

@Serializable
data class DayAnswers(
    val qid: Int,
    val mine: String? = null,
    val partner: String? = null,
    val partnerQid: Int? = null,
)

/** Message exchanged between the two phones (always end-to-end encrypted on the wire). */
@Serializable
data class Envelope(
    val v: Int = 1,
    val id: String,
    val from: String,
    val name: String,
    val ts: Long,
    val type: String,           // draw | answer | nudge | hello
    val drawing: Drawing? = null,
    val day: String? = null,
    val qid: Int? = null,
    val text: String? = null,
    val anniv: Long? = null,
)

/** Every user-facing feature has its own switch in Settings. */
enum class Feature(val title: String, val desc: String, val default: Boolean = true) {
    INSTANT("Instant delivery", "Keep a live connection so drawings arrive within seconds, even when the app is closed. Shows a small permanent notification"),
    DAILY_QUESTION("Daily question", "A new question for the two of you every day"),
    SPICY("Spicy questions", "Mix flirty questions into the daily question. Turn it on for both of you", false),
    REVEAL_LOCK("Answer to reveal", "Hide your partner's answer until you have answered too"),
    WIDGET_QUESTION("Question on widget", "Show today's question under the drawing on the home screen"),
    WIDGET_CAPTION("Caption on widget", "Show who drew it and when on the widget"),
    DAYS_COUNTER("Days together", "Count the days since your anniversary, in the app and on the widget"),
    STREAK("Doodle streak", "Count the days in a row you both sent a drawing"),
    NUDGES("Thinking of you", "Send a quick heart when there is no time to draw"),
    STICKERS("Stickers", "Add emoji, text and photo stickers to your drawings"),
    GALLERY("Save to gallery", "Keep a copy of every drawing you send or receive in a CuteKind album in your phone's gallery"),
    REPLAY("Stroke replay", "Watch a drawing appear stroke by stroke, the way it was drawn"),
    MEMORIES("Memories", "Keep a gallery of the drawings you have sent each other"),
    NOTIFICATIONS("Notifications", "Tell me when my partner sends something"),
    BACKGROUND_SYNC("Background sync", "Also check for new drawings about every 15 minutes, as a backup"),
}

const val DEFAULT_RELAY = "https://ntfy.sh"

@Serializable
data class AppState(
    val deviceId: String = "",
    val myName: String = "",
    val code: String? = null,
    val partnerName: String? = null,
    val anniversary: Long? = null,              // epoch day
    val relay: String = DEFAULT_RELAY,
    val features: Map<String, Boolean> = emptyMap(),
    val received: List<Drawing> = emptyList(),  // newest first
    val sent: List<Drawing> = emptyList(),      // newest first
    val days: Map<String, DayAnswers> = emptyMap(),
    val lastNudgeTs: Long = 0,
    val nudgesReceived: Int = 0,
    val cursor: String? = null,
    val seen: List<String> = emptyList(),
    val partials: Map<String, Map<String, String>> = emptyMap(),
    val outbox: List<Envelope> = emptyList(),
    val lastSyncTs: Long = 0,
    val lastError: String? = null,
    val live: Boolean = false,                  // a live connection to the relay is open right now
    val askedBattery: Boolean = false,
) {
    val paired get() = code != null
    fun on(f: Feature) = features[f.name] ?: f.default
    val partnerLabel get() = partnerName?.takeIf { it.isNotBlank() } ?: "your partner"
}
