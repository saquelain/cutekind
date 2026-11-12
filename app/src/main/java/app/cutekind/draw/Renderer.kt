package app.cutekind.draw

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.LruCache
import app.cutekind.data.Drawing
import app.cutekind.data.Sticker
import app.cutekind.data.Stroke
import app.cutekind.data.UNIT

/** One renderer for the in-app canvas, the widget bitmap, thumbnails and notifications. */
object Renderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    /** @param progress 0..1, how much of the drawing (by points, in drawing order) to show. */
    @Synchronized
    fun draw(canvas: Canvas, size: Float, strokes: List<Stroke>, bg: Int, progress: Float = 1f) {
        val k = size / UNIT
        var budget = if (progress >= 1f) Int.MAX_VALUE else (strokes.sumOf { it.p.size / 2 } * progress).toInt()
        for (s in strokes) {
            if (budget <= 0) break
            val n = minOf(s.p.size / 2, budget)
            budget -= n
            paint.color = if (s.e) bg else s.c
            val width = s.w * size
            if (n == 1) {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(s.p[0] * k, s.p[1] * k, width / 2, paint)
                continue
            }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = width
            path.rewind()
            path.moveTo(s.p[0] * k, s.p[1] * k)
            for (i in 1 until n) {
                val px = s.p[2 * i - 2] * k; val py = s.p[2 * i - 1] * k
                val x = s.p[2 * i] * k; val y = s.p[2 * i + 1] * k
                path.quadTo(px, py, (px + x) / 2, (py + y) / 2)
            }
            path.lineTo(s.p[2 * n - 2] * k, s.p[2 * n - 1] * k)
            canvas.drawPath(path, paint)
        }
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val images = LruCache<Int, Bitmap>(12)
    private val box = RectF()

    private fun image(data: String): Bitmap? = images.get(data.hashCode()) ?: runCatching {
        val bytes = java.util.Base64.getDecoder().decode(data)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()?.also { images.put(data.hashCode(), it) }

    /** Width/height ratio of an image sticker, used for hit-testing and outlines. */
    fun aspect(st: Sticker): Float = if (st.k == "i") image(st.t)?.let { it.width.toFloat() / it.height } ?: 1f else 1f

    /** Stickers sit on top of the strokes, in the order they were added. */
    @Synchronized
    fun stickers(canvas: Canvas, size: Float, list: List<Sticker>) {
        val k = size / UNIT
        for (st in list) {
            canvas.save()
            canvas.translate(st.x * k, st.y * k)
            canvas.rotate(st.r)
            if (st.k == "i") {
                image(st.t)?.let { bmp ->
                    val w = st.s * size; val h = w * bmp.height / bmp.width
                    box.set(-w / 2, -h / 2, w / 2, h / 2)
                    canvas.drawBitmap(bmp, null, box, imagePaint)
                }
            } else {
                textPaint.textSize = st.s * size
                textPaint.color = if (st.k == "t") st.c else Color.BLACK
                textPaint.isFakeBoldText = st.k == "t"
                val fm = textPaint.fontMetrics
                canvas.drawText(st.t, 0f, -(fm.ascent + fm.descent) / 2, textPaint)
            }
            canvas.restore()
        }
    }

    fun bitmap(d: Drawing, px: Int, cornerFraction: Float = 0f): Bitmap = bitmap(d.strokes, d.bg, px, cornerFraction, d.stickers)

    fun bitmap(strokes: List<Stroke>, bg: Int, px: Int, cornerFraction: Float = 0f, stickers: List<Sticker> = emptyList()): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (cornerFraction > 0f) {
            val clip = Path().apply { addRoundRect(RectF(0f, 0f, px.toFloat(), px.toFloat()), px * cornerFraction, px * cornerFraction, Path.Direction.CW) }
            canvas.clipPath(clip)
        }
        canvas.drawColor(bg)
        draw(canvas, px.toFloat(), strokes, bg)
        stickers(canvas, px.toFloat(), stickers)
        return bmp
    }

    /** Readable ink colour for text placed on top of [bg]. */
    fun inkOn(bg: Int): Int {
        val lum = (0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg)) / 255
        return if (lum > 0.55) 0xFF3B2A33.toInt() else 0xFFFFF4F7.toInt()
    }
}
