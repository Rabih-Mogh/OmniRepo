package app.fajralarm.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A plain ARGB pixel buffer, so matching can run (and be tested) without Android. */
class RgbImage(val width: Int, val height: Int, val argb: IntArray) {
    init {
        require(width > 0 && height > 0 && argb.size == width * height) { "bad image buffer" }
    }
}

enum class MatchStrictness(val displayName: String, val structural: Double, val color: Double) {
    RELAXED("Relaxed", 0.66, 0.35),
    NORMAL("Normal", 0.78, 0.45),
    STRICT("Strict", 0.85, 0.55),
}

data class MatchResult(
    /** Best alignment of the two photos' edges, -1..1 (1 = identical). */
    val structural: Double,
    /** Colour-histogram overlap, 0..1. */
    val color: Double,
    val matched: Boolean,
    /** The photo was too dark or featureless to judge (e.g. lens covered, lights off). */
    val unusable: Boolean,
) {
    /** A single 0–100 figure to show the user. */
    val percent: Int get() = (structural.coerceIn(0.0, 1.0) * 100).roundToInt()
}

/**
 * Decides whether a photo shows the same place as a reference photo.
 *
 * Matching is lighting-tolerant and allows the phone to be held a little
 * differently: the photos' edge (gradient) fields are compared over small shifts
 * and zooms, which ignores flat areas and overall brightness/contrast, and a
 * hue/saturation histogram rejects scenes with the right shapes but wrong colours.
 * The alarm screen shows the reference faintly over the camera preview so the
 * user can line the shot up.
 */
object ImageMatcher {

    private const val SIZE = 48
    private const val MAX_SHIFT = 4
    private val CROPS = floatArrayOf(1.0f, 0.88f, 0.78f)

    class Features internal constructor(
        /** Gradient field (gx, gy interleaved) of each centre crop. */
        internal val crops: List<FloatArray>,
        internal val histogram: DoubleArray,
        internal val unusable: Boolean,
    )

    fun extract(img: RgbImage): Features {
        val lumas = CROPS.map { blur(centerCropLuma(img, it)) }
        val crops = lumas.map(::gradients)
        val full = lumas[0]
        val mean = full.average()
        var v = 0.0
        for (p in full) v += (p - mean) * (p - mean)
        val std = sqrt(v / full.size)
        return Features(crops, colorHistogram(img), unusable = mean < 0.07 || std < 0.035)
    }

    fun compare(reference: Features, candidate: Features, strictness: MatchStrictness): MatchResult {
        if (candidate.unusable) return MatchResult(0.0, 0.0, matched = false, unusable = true)
        // Same framing, candidate zoomed out (reference crop) or zoomed in (candidate crop).
        val pairs = listOf(
            reference.crops[0] to candidate.crops[0],
            reference.crops[0] to candidate.crops[1],
            reference.crops[0] to candidate.crops[2],
            reference.crops[1] to candidate.crops[0],
            reference.crops[2] to candidate.crops[0],
        )
        val structural = pairs.maxOf { (a, b) -> bestShiftedGradientCorrelation(a, b) }
        val color = histogramIntersection(reference.histogram, candidate.histogram)
        val matched = structural >= strictness.structural && color >= strictness.color
        return MatchResult(structural, color, matched, unusable = false)
    }

    /** Compares against every reference photo and returns the best result. */
    fun bestMatch(references: List<Features>, candidate: Features, strictness: MatchStrictness): MatchResult {
        require(references.isNotEmpty()) { "no reference photos" }
        return references
            .map { compare(it, candidate, strictness) }
            .maxWith(compareBy<MatchResult> { it.matched }.thenBy { it.structural })
    }

    // --- internals -----------------------------------------------------------------

    private fun luma(c: Int): Float {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    }

    /** Centre square crop covering [fraction] of the short side, area-averaged to SIZE×SIZE. */
    private fun centerCropLuma(img: RgbImage, fraction: Float): FloatArray {
        val side = (min(img.width, img.height) * fraction).coerceAtLeast(1f)
        val x0 = (img.width - side) / 2f
        val y0 = (img.height - side) / 2f
        val cell = side / SIZE
        val out = FloatArray(SIZE * SIZE)
        for (gy in 0 until SIZE) {
            val sy0 = (y0 + gy * cell).toInt()
            val sy1 = max(sy0 + 1, (y0 + (gy + 1) * cell).toInt()).coerceAtMost(img.height)
            for (gx in 0 until SIZE) {
                val sx0 = (x0 + gx * cell).toInt()
                val sx1 = max(sx0 + 1, (x0 + (gx + 1) * cell).toInt()).coerceAtMost(img.width)
                var sum = 0f
                var n = 0
                for (y in sy0 until sy1) {
                    val row = y * img.width
                    for (x in sx0 until sx1) {
                        sum += luma(img.argb[row + x]); n++
                    }
                }
                out[gy * SIZE + gx] = if (n == 0) 0f else sum / n
            }
        }
        return out
    }

    private fun blur(src: FloatArray): FloatArray {
        val out = FloatArray(src.size)
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            var s = 0f
            var n = 0
            for (dy in -1..1) for (dx in -1..1) {
                val yy = y + dy
                val xx = x + dx
                if (yy in 0 until SIZE && xx in 0 until SIZE) { s += src[yy * SIZE + xx]; n++ }
            }
            out[y * SIZE + x] = s / n
        }
        return out
    }

    /** Sobel gradients of a SIZE×SIZE image, stored as interleaved (gx, gy). */
    private fun gradients(src: FloatArray): FloatArray {
        val out = FloatArray(SIZE * SIZE * 2)
        fun p(x: Int, y: Int) = src[y.coerceIn(0, SIZE - 1) * SIZE + x.coerceIn(0, SIZE - 1)]
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val gx = (p(x + 1, y - 1) + 2 * p(x + 1, y) + p(x + 1, y + 1)) -
                (p(x - 1, y - 1) + 2 * p(x - 1, y) + p(x - 1, y + 1))
            val gy = (p(x - 1, y + 1) + 2 * p(x, y + 1) + p(x + 1, y + 1)) -
                (p(x - 1, y - 1) + 2 * p(x, y - 1) + p(x + 1, y - 1))
            val i = (y * SIZE + x) * 2
            out[i] = gx
            out[i + 1] = gy
        }
        return out
    }

    /**
     * How well the edges of [a]'s central window line up with [b] shifted by up to
     * ±MAX_SHIFT pixels: the normalised dot product of the two gradient fields.
     * Flat areas contribute nothing, and a uniform change in brightness or contrast
     * scales both sides equally, so it cancels out.
     */
    private fun bestShiftedGradientCorrelation(a: FloatArray, b: FloatArray): Double {
        var best = -1.0
        val lo = MAX_SHIFT + 1
        val hi = SIZE - MAX_SHIFT - 1
        for (dy in -MAX_SHIFT..MAX_SHIFT) for (dx in -MAX_SHIFT..MAX_SHIFT) {
            var dot = 0.0; var ea = 0.0; var eb = 0.0
            for (y in lo until hi) {
                for (x in lo until hi) {
                    val ia = (y * SIZE + x) * 2
                    val ib = ((y + dy) * SIZE + x + dx) * 2
                    val ax = a[ia].toDouble(); val ay = a[ia + 1].toDouble()
                    val bx = b[ib].toDouble(); val by = b[ib + 1].toDouble()
                    dot += ax * bx + ay * by
                    ea += ax * ax + ay * ay
                    eb += bx * bx + by * by
                }
            }
            if (ea <= 1e-9 || eb <= 1e-9) continue
            val c = dot / sqrt(ea * eb)
            if (c > best) best = c
        }
        return best
    }

    /**
     * 12 hues × 2 saturations for coloured pixels + 3 brightness bins for greys/whites.
     * Brightness is first normalised to the photo's own bright end (95th percentile), so
     * a dimmer or brighter shot of the same sink lands in the same bins.
     */
    private fun colorHistogram(img: RgbImage): DoubleArray {
        val side = min(img.width, img.height)
        val x0 = (img.width - side) / 2
        val y0 = (img.height - side) / 2
        val step = max(1, side / 64)
        val samples = ArrayList<Int>(64 * 64 + 128)
        var y = y0
        while (y < y0 + side) {
            var x = x0
            while (x < x0 + side) { samples.add(img.argb[y * img.width + x]); x += step }
            y += step
        }
        fun maxChannel(c: Int) = max((c shr 16) and 0xFF, max((c shr 8) and 0xFF, c and 0xFF))
        val sorted = samples.map(::maxChannel).sorted()
        val p95 = sorted[(sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)].coerceAtLeast(1) / 255f

        val bins = DoubleArray(12 * 2 + 3)
        for (c in samples) {
            val r = ((c shr 16) and 0xFF) / 255f
            val g = ((c shr 8) and 0xFF) / 255f
            val b = (c and 0xFF) / 255f
            val mx = max(r, max(g, b))
            val mn = min(r, min(g, b))
            val rel = (mx / p95).coerceAtMost(1f)
            val sat = if (mx <= 0f) 0f else (mx - mn) / mx
            val bin = if (sat < 0.2f || rel < 0.15f) {
                24 + (rel * 3).toInt().coerceAtMost(2)
            } else {
                val d = mx - mn
                var hue = when (mx) {
                    r -> ((g - b) / d) % 6f
                    g -> (b - r) / d + 2f
                    else -> (r - g) / d + 4f
                } * 60f
                if (hue < 0) hue += 360f
                (hue / 30f).toInt().coerceIn(0, 11) * 2 + if (sat < 0.5f) 0 else 1
            }
            bins[bin] += 1.0
        }
        for (i in bins.indices) bins[i] /= samples.size.toDouble()
        return bins
    }

    private fun histogramIntersection(a: DoubleArray, b: DoubleArray): Double {
        var s = 0.0
        for (i in a.indices) s += min(a[i], b[i])
        return s
    }
}
