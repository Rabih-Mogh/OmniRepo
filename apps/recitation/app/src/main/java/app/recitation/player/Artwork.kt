package app.recitation.player

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.LruCache
import app.recitation.core.Surah
import app.recitation.data.SurahNames
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * The picture that goes with a surah in Android's own media player (the notification and
 * the lock screen). Android takes that player's colours from the picture, so this is how
 * it comes to wear the app's theme: the surah's name under the arch, on the colour of the
 * app's own bars, teal by day and a dark teal in dark mode.
 */
object Artwork {

    private const val SIZE = 512
    private val made = LruCache<String, ByteArray>(24)

    /**
     * A PNG for [surah], in the colours of the phone's current light or dark mode. The name
     * is drawn with the font in [calligraphy] when there is one.
     */
    fun of(
        context: Context,
        surah: Surah,
        calligraphy: File? = SurahNames.file(context),
        dark: Boolean = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES,
    ): ByteArray {
        val key = "${surah.number}-$dark-${calligraphy != null}"
        made.get(key)?.let { return it }

        val background = if (dark) 0xFF0F2B31.toInt() else 0xFF0E5E6F.toInt()
        val line = if (dark) 0xFF5EDCCB.toInt() else 0xFFB8F5EC.toInt()
        val ink = if (dark) 0xFFF5F5F5.toInt() else 0xFFFFFFFF.toInt()
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(background)

        // The low pointed arch of the app's icon, as tall as it is wide.
        val s = SIZE.toFloat()
        val arch = Path().apply {
            moveTo(s * 0.14f, s * 0.88f)
            lineTo(s * 0.14f, s * 0.52f)
            quadTo(s * 0.14f, s * 0.3f, s * 0.5f, s * 0.16f)
            quadTo(s * 0.86f, s * 0.3f, s * 0.86f, s * 0.52f)
            lineTo(s * 0.86f, s * 0.88f)
        }
        canvas.drawPath(arch, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = s * 0.02f
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            color = line
        })

        // The surah's name inside it: in the fetched calligraphy when it is on the phone.
        val text = if (calligraphy != null) SurahNames.glyph(surah.number) else surah.arabic
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            typeface = calligraphy?.let { runCatching { Typeface.createFromFile(it) }.getOrNull() } ?: Typeface.DEFAULT_BOLD
            textSize = s * 0.34f
        }
        // As large as fits between the arch's sides, and centred there by what is actually
        // drawn: a calligraphic name's ink can sit well to one side of the space it is given.
        val widest = s * 0.56f
        val tallest = s * 0.3f
        fun drawn() = android.graphics.Rect().also { paint.getTextBounds(text, 0, text.length, it) }
        val atFullSize = drawn()
        paint.textSize *= minOf(widest / atFullSize.width().coerceAtLeast(1), tallest / atFullSize.height().coerceAtLeast(1), 1f)
        val drawn = drawn()
        // The middle of the arch: half way across, and half way between its point and its feet.
        canvas.drawText(text, s * 0.5f - drawn.exactCenterX(), s * 0.52f - drawn.exactCenterY(), paint)

        val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        made.put(key, png)
        return png
    }
}
