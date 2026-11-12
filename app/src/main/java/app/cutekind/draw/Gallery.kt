package app.cutekind.draw

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import app.cutekind.data.Drawing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Saves drawings as photos in a "CuteKind" album, so they show up in the phone's gallery. */
object Gallery {
    const val ALBUM = "CuteKind"
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.systemDefault())

    fun fileName(d: Drawing): String =
        "CuteKind_${stamp.format(Instant.ofEpochMilli(d.ts))}_${d.name.filter(Char::isLetterOrDigit).take(12).ifEmpty { "doodle" }}.png"

    /**
     * Returns true if the picture was written. Needs Android 10 or newer, where an app may add
     * to its own album without asking for storage permission.
     */
    fun save(context: Context, d: Drawing): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val resolver = context.contentResolver
        return runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName(d))
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + ALBUM)
                put(MediaStore.Images.Media.DATE_TAKEN, d.ts)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            try {
                resolver.openOutputStream(uri)!!.use { Renderer.bitmap(d, 1080).compress(Bitmap.CompressFormat.PNG, 100, it) }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                true
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                false
            }
        }.getOrDefault(false)
    }
}
