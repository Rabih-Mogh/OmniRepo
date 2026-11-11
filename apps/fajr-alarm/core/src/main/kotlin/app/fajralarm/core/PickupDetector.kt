package app.fajralarm.core

import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Tells whether the phone has been picked up, from accelerometer readings.
 *
 * Readings are low-pass filtered to the direction of gravity, so the alarm's own
 * vibration (fast, small shakes) doesn't count. Once that direction has turned
 * [minTiltDegrees] away from where it started, the phone is no longer lying where it
 * was, and it stays "picked up" from then on. Not thread-safe: feed it from one thread.
 */
class PickupDetector(
    private val minTiltDegrees: Float = 25f,
    /** Weight of each new reading in the low-pass filter (0..1); lower is smoother. */
    private val smoothing: Float = 0.1f,
) {
    private var gravity: FloatArray? = null
    private var start: FloatArray? = null

    var pickedUp = false
        private set

    fun offer(x: Float, y: Float, z: Float): Boolean {
        if (pickedUp) return true
        val g = gravity?.also {
            it[0] += smoothing * (x - it[0])
            it[1] += smoothing * (y - it[1])
            it[2] += smoothing * (z - it[2])
        } ?: floatArrayOf(x, y, z).also { gravity = it }
        val s = start ?: g.copyOf().also { start = it }
        if (angleDegrees(s, g) >= minTiltDegrees) pickedUp = true
        return pickedUp
    }

    private fun angleDegrees(a: FloatArray, b: FloatArray): Float {
        val na = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2])
        val nb = sqrt(b[0] * b[0] + b[1] * b[1] + b[2] * b[2])
        if (na == 0f || nb == 0f) return 0f
        val cos = ((a[0] * b[0] + a[1] * b[1] + a[2] * b[2]) / (na * nb)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cos).toDouble()).toFloat()
    }
}
