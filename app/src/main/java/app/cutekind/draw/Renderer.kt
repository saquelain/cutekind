package app.cutekind.draw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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

    fun bitmap(strokes: List<Stroke>, bg: Int, px: Int, cornerFraction: Float = 0f): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (cornerFraction > 0f) {
            val clip = Path().apply { addRoundRect(RectF(0f, 0f, px.toFloat(), px.toFloat()), px * cornerFraction, px * cornerFraction, Path.Direction.CW) }
            canvas.clipPath(clip)
        }
        canvas.drawColor(bg)
        draw(canvas, px.toFloat(), strokes, bg)
        return bmp
    }

    /** Readable ink colour for text placed on top of [bg]. */
    fun inkOn(bg: Int): Int {
        val lum = (0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg)) / 255
        return if (lum > 0.55) 0xFF3B2A33.toInt() else 0xFFFFF4F7.toInt()
    }
}
