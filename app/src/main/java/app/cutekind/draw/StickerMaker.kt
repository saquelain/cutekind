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
    /**
     * Photo stickers travel through the relay with the drawing. The relay lets a phone send about
     * 60 messages in a burst, so three photos plus the strokes must stay well under that:
     * 32 KB each is roughly 11 messages per photo.
     */
    const val MAX_BYTES = 32_000
    const val MAX_SIDE = 640

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
                // decode at no more than twice the final size, so the last resize stays sharp
                d.setTargetSampleSize(maxOf(1, maxOf(info.size.width, info.size.height) / MAX_SIDE))
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
            val sample = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / MAX_SIDE)
            context.contentResolver.openInputStream(uri)!!.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })!!
            }
        }
        Sticker("i", encode(source), UNIT / 2, UNIT / 2, 0.5f)
    }.getOrNull()

    /**
     * Keeps as much detail as the budget allows: full size at high quality first, then lower
     * quality, and only then a smaller picture. Most photos fit at 640 px without shrinking.
     */
    fun encode(source: Bitmap): String {
        @Suppress("DEPRECATION")
        val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        var side = MAX_SIDE
        while (true) {
            val scale = side.toFloat() / maxOf(source.width, source.height)
            val bmp = if (scale < 1f) Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), true) else source
            for (quality in intArrayOf(85, 75, 65, 55)) {
                val out = ByteArrayOutputStream()
                bmp.compress(format, quality, out)
                if (out.size() <= MAX_BYTES || side <= 160) return Base64.getEncoder().encodeToString(out.toByteArray())
            }
            side = side * 4 / 5
        }
    }
}
