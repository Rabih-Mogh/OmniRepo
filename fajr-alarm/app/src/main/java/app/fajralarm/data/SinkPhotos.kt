package app.fajralarm.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.fajralarm.core.ImageMatcher
import app.fajralarm.core.RgbImage
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Reference photos of the user's sink, kept in the app's private storage. */
object SinkPhotos {

    const val MAX = 3

    private fun dir(context: Context) = File(context.filesDir, "sink").apply { mkdirs() }

    fun list(context: Context): List<File> =
        dir(context).listFiles { f -> f.name.endsWith(".jpg") }?.sortedBy { it.name } ?: emptyList()

    fun count(context: Context): Int = list(context).size

    fun add(context: Context, photo: Bitmap): File {
        val scaled = scaleDown(photo, 768)
        val file = File(dir(context), "ref_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        return file
    }

    fun delete(file: File) {
        file.delete()
    }

    fun load(file: File): Bitmap? = BitmapFactory.decodeFile(file.path)

    /** Matching features of every saved reference photo. Do this off the main thread. */
    fun referenceFeatures(context: Context): List<ImageMatcher.Features> =
        list(context).mapNotNull { f -> load(f)?.let { ImageMatcher.extract(toRgbImage(it)) } }

    fun features(photo: Bitmap): ImageMatcher.Features = ImageMatcher.extract(toRgbImage(photo))

    private fun toRgbImage(bitmap: Bitmap): RgbImage {
        val b = scaleDown(bitmap, 320).let {
            if (it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, false)
        }
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        return RgbImage(b.width, b.height, px)
    }

    private fun scaleDown(b: Bitmap, maxSide: Int): Bitmap {
        val longest = max(b.width, b.height)
        if (longest <= maxSide) return b
        val f = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(b, (b.width * f).roundToInt(), (b.height * f).roundToInt(), true)
    }
}
