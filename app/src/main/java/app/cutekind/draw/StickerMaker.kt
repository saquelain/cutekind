package app.cutekind.draw

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import app.cutekind.data.Sticker
import app.cutekind.data.UNIT
import java.io.ByteArrayOutputStream
import java.util.Base64

object StickerMaker {
    const val MAX_STICKERS = 30
    const val MAX_PHOTOS = 3
    /** Photo stickers travel through the relay with the drawing, so they are kept small. */
    const val MAX_BYTES = 14_000

    val EMOJI = listOf(
        "❤️", "💕", "💖", "💘", "💋", "😘", "🥰", "😍", "😊", "🤗", "😂", "🥺", "😴", "😎", "🙈", "🤭",
        "🌹", "🌸", "🌻", "🌙", "⭐", "✨", "☀️", "🌈", "☁️", "🔥", "🎈", "🎁", "🎂", "🍕", "🍫", "☕",
        "🐻", "🐱", "🐶", "🐰", "🦋", "🐧", "✈️", "🏠", "💌", "💍", "👑", "🎵", "🤍", "💙", "💜", "🖤",
    )

    fun emoji(e: String) = Sticker("e", e, UNIT / 2, UNIT / 2, 0.2f)
    fun text(t: String, color: Int) = Sticker("t", t.trim().take(30), UNIT / 2, UNIT / 2, 0.11f, c = color)

    fun fromPhoto(context: Context, uri: Uri): Sticker? = runCatching {
        val source = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                d.setTargetSampleSize(maxOf(1, maxOf(info.size.width, info.size.height) / 512))
            }
        } else {
            context.contentResolver.openInputStream(uri)!!.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 4 })!!
            }
        }
        Sticker("i", encode(source), UNIT / 2, UNIT / 2, 0.45f)
    }.getOrNull()

    /** Shrinks and compresses until the picture fits the relay budget. */
    fun encode(source: Bitmap): String {
        @Suppress("DEPRECATION")
        val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        var side = 256
        while (true) {
            val scale = side.toFloat() / maxOf(source.width, source.height)
            val bmp = if (scale < 1f) Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), true) else source
            for (quality in intArrayOf(70, 50, 30)) {
                val out = ByteArrayOutputStream()
                bmp.compress(format, quality, out)
                if (out.size() <= MAX_BYTES || side <= 96) return Base64.getEncoder().encodeToString(out.toByteArray())
            }
            side = side * 3 / 4
        }
    }
}
