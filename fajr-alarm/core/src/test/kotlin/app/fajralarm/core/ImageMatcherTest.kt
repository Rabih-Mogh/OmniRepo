package app.fajralarm.core

import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Synthetic "bathroom" scenes: tiled wall, a basin, a tap and a few objects, drawn
 * from a seed. The same seed photographed differently should match; other seeds
 * (other rooms) should not.
 */
class ImageMatcherTest {

    private val w = 400
    private val h = 300

    private data class Shape(val x: Float, val y: Float, val rw: Float, val rh: Float, val color: Int, val ellipse: Boolean)

    private data class Scene(val wall: Int, val tilesX: Float, val tilesY: Float, val phase: Float, val shapes: List<Shape>)

    private fun scene(seed: Int): Scene {
        val r = Random(seed)
        fun col() = (0xFF shl 24) or (r.nextInt(30, 255) shl 16) or (r.nextInt(30, 255) shl 8) or r.nextInt(30, 255)
        val wall = col()
        val shapes = buildList {
            // basin + tap + a few random objects (soap, cup, mirror edge…)
            add(Shape(r.nextFloat() * 0.3f + 0.35f, r.nextFloat() * 0.2f + 0.55f, 0.28f, 0.16f, 0xFFF2F2F2.toInt(), true))
            add(Shape(r.nextFloat() * 0.3f + 0.35f, r.nextFloat() * 0.1f + 0.3f, 0.03f, 0.12f, 0xFF9A9A9A.toInt(), false))
            repeat(6) {
                add(Shape(r.nextFloat(), r.nextFloat(), r.nextFloat() * 0.15f + 0.03f, r.nextFloat() * 0.15f + 0.03f, col(), r.nextBoolean()))
            }
        }
        // Each room has its own tile size and alignment.
        return Scene(wall, r.nextFloat() * 10f + 4f, r.nextFloat() * 8f + 3f, r.nextFloat(), shapes)
    }

    /**
     * Render scene [seed] as if photographed with a camera offset by ([shiftX], [shiftY])
     * pixels, zoom [zoom], brightness [gain], plus sensor noise.
     */
    private fun render(
        seed: Int,
        shiftX: Float = 0f,
        shiftY: Float = 0f,
        zoom: Float = 1f,
        rotateDeg: Float = 0f,
        gain: Float = 1f,
        noise: Int = 0,
        noiseSeed: Int = 99,
    ): RgbImage {
        val sc = scene(seed)
        val wall = sc.wall
        val nr = Random(noiseSeed)
        val px = IntArray(w * h)
        val cosR = kotlin.math.cos(Math.toRadians(rotateDeg.toDouble())).toFloat()
        val sinR = kotlin.math.sin(Math.toRadians(rotateDeg.toDouble())).toFloat()
        for (y in 0 until h) for (x in 0 until w) {
            // camera → scene coordinates (0..1)
            val cx = (x - w / 2f) / zoom
            val cy = (y - h / 2f) / zoom
            val sx = (cx * cosR - cy * sinR + w / 2f + shiftX) / w
            val sy = (cx * sinR + cy * cosR + h / 2f + shiftY) / h
            var c = wall
            // tile grout lines
            val tx = (sx * sc.tilesX + sc.phase).mod(1f)
            val ty = (sy * sc.tilesY + sc.phase).mod(1f)
            if (tx < 0.05f || ty < 0.05f) c = 0xFF808080.toInt()
            for (s in sc.shapes) {
                val dx = (sx - s.x) / s.rw
                val dy = (sy - s.y) / s.rh
                val inside = if (s.ellipse) dx * dx + dy * dy <= 1f else dx in -1f..1f && dy in -1f..1f
                if (inside) c = s.color
            }
            fun ch(v: Int) = ((v * gain) + if (noise > 0) nr.nextInt(-noise, noise + 1) else 0).roundToInt().coerceIn(0, 255)
            px[y * w + x] = (0xFF shl 24) or (ch((c shr 16) and 0xFF) shl 16) or (ch((c shr 8) and 0xFF) shl 8) or ch(c and 0xFF)
        }
        return RgbImage(w, h, px)
    }

    private fun score(ref: RgbImage, cand: RgbImage, s: MatchStrictness = MatchStrictness.NORMAL): MatchResult =
        ImageMatcher.compare(ImageMatcher.extract(ref), ImageMatcher.extract(cand), s)

    @Test
    fun samePlaceUnderDifferentConditionsMatches() {
        for (seed in 1..8) {
            val ref = render(seed)
            val variants = mapOf(
                "dimmer+noise" to render(seed, gain = 0.65f, noise = 18),
                "shifted" to render(seed, shiftX = 14f, shiftY = -9f, noise = 8),
                "zoomed in" to render(seed, zoom = 1.12f, noise = 8),
                "zoomed out" to render(seed, zoom = 0.9f, gain = 1.2f, noise = 8),
                "tilted 3°" to render(seed, rotateDeg = 3f, shiftX = 5f, noise = 8),
                "everything" to render(seed, shiftX = -8f, shiftY = 6f, zoom = 1.06f, rotateDeg = -2f, gain = 0.75f, noise = 14),
            )
            for ((name, img) in variants) {
                val r = score(ref, img)
                println("seed $seed $name: structural=${"%.2f".format(r.structural)} color=${"%.2f".format(r.color)}")
                assertTrue(r.matched, "seed $seed $name should match: $r")
            }
        }
    }

    @Test
    fun otherSinksDoNotMatch() {
        // Every synthetic scene is a sink with tiles, so this is the hard case of
        // "someone else's bathroom", not just "the ceiling".
        var worst = -1.0
        for (a in 1..12) for (b in 1..12) {
            if (a == b) continue
            val r = score(render(a), render(b, noise = 8), MatchStrictness.NORMAL)
            worst = maxOf(worst, r.structural)
            assertFalse(r.matched, "scene $a vs $b must not match: $r")
        }
        println("highest structural score between different scenes: ${"%.2f".format(worst)}")
    }

    @Test
    fun darkOrBlankPhotosAreRejected() {
        val ref = ImageMatcher.extract(render(1))
        val black = RgbImage(w, h, IntArray(w * h) { 0xFF050505.toInt() })
        val grey = RgbImage(w, h, IntArray(w * h) { 0xFF888888.toInt() })
        for (img in listOf(black, grey)) {
            val r = ImageMatcher.compare(ref, ImageMatcher.extract(img), MatchStrictness.RELAXED)
            assertTrue(r.unusable && !r.matched, "$r")
        }
    }

    @Test
    fun bestMatchUsesAnyReference() {
        val refs = listOf(render(3), render(5)).map(ImageMatcher::extract)
        val r = ImageMatcher.bestMatch(refs, ImageMatcher.extract(render(5, gain = 0.8f, noise = 10)), MatchStrictness.NORMAL)
        assertTrue(r.matched)
    }
}
