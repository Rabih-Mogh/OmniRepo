package app.fajralarm.core

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** A box in normalised image coordinates (0..1 on both axes). */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = max(0f, right - left)
    val height: Float get() = max(0f, bottom - top)
    val area: Float get() = width * height
    val centerX: Float get() = (left + right) / 2
    val centerY: Float get() = (top + bottom) / 2

    fun intersectionArea(other: Box): Float =
        max(0f, min(right, other.right) - max(left, other.left)) *
            max(0f, min(bottom, other.bottom) - max(top, other.top))
}

/** One object the detector found, with its COCO label ("sink", "tv", …). */
data class Detection(val label: String, val score: Float, val box: Box)

/** Everything the detector says about one camera frame. */
data class FrameResult(
    val timeMs: Long,
    /** Mean luma of the frame, 0..255. */
    val brightness: Float,
    val detections: List<Detection>,
)

sealed interface SinkStatus {
    /** The phone hasn't been picked up yet, so nothing the camera sees counts. */
    data object Resting : SinkStatus
    data object Dark : SinkStatus
    data object Searching : SinkStatus
    /** The sink is inside a phone, TV or laptop screen, so it doesn't count. */
    data object OnScreen : SinkStatus
    /** A sink is in view but too small in the picture: the camera isn't at it yet. */
    data object TooFar : SinkStatus
    /** A sink is close enough but off to one side: the camera isn't facing it. */
    data object OffCentre : SinkStatus
    /** A sink is in view; [progress] runs 0..1 over the hold time. */
    data class Holding(val progress: Float) : SinkStatus
    data object Confirmed : SinkStatus
}

/**
 * Decides when the camera has really been pointed at a sink, from the detector's
 * per-frame results.
 *
 * A sink must stay in view for [holdMs] (short gaps of up to [maxGapMs] are forgiven,
 * since the detector misses the odd frame) and the camera must move a little meanwhile,
 * as it does in a hand. The sink only counts while the camera is at it and facing it:
 * it must fill [minArea] of the picture and sit near the middle, so one glimpsed across
 * the room or through a doorway resets the check. So does a sink inside a detected
 * phone, TV or laptop, which is a picture on a screen. Not thread-safe: feed it from
 * one thread.
 */
class SinkVerifier(
    private val minScore: Float = 0.45f,
    private val holdMs: Long = 2_000,
    private val maxGapMs: Long = 800,
    private val minFrames: Int = 4,
    /**
     * Smallest movement of the sink box (in frame widths) that counts as a hand-held camera.
     * Well above the detector's own frame-to-frame jitter on a phone lying still.
     */
    private val minMovement: Float = 0.05f,
    /**
     * Smallest share of the picture the sink box must cover. A basin seen from about a
     * metre fills this much; from a doorway two metres off it fills a quarter of it.
     */
    private val minArea: Float = 0.15f,
    /** How far from the middle of the picture the centre of the sink box may be, on either axis. */
    private val maxOffCentre: Float = 0.25f,
) {
    private data class Sighting(val timeMs: Long, val box: Box)

    private val track = ArrayDeque<Sighting>()

    fun reset() = track.clear()

    fun offer(frame: FrameResult): SinkStatus {
        if (frame.brightness < DARK_BRIGHTNESS) {
            reset()
            return SinkStatus.Dark
        }
        val sink = frame.detections
            .filter { it.label == SINK && it.score >= minScore }
            .maxByOrNull { it.score }
        if (sink == null) {
            val last = track.lastOrNull()
            if (last == null || frame.timeMs - last.timeMs > maxGapMs) {
                reset()
                return SinkStatus.Searching
            }
            return SinkStatus.Holding(progress(frame.timeMs))
        }
        if (onScreen(sink, frame.detections)) {
            reset()
            return SinkStatus.OnScreen
        }
        if (sink.box.area < minArea) {
            reset()
            return SinkStatus.TooFar
        }
        if (abs(sink.box.centerX - 0.5f) > maxOffCentre || abs(sink.box.centerY - 0.5f) > maxOffCentre) {
            reset()
            return SinkStatus.OffCentre
        }
        track.lastOrNull()?.let { if (frame.timeMs - it.timeMs > maxGapMs) reset() }
        track.addLast(Sighting(frame.timeMs, sink.box))
        // Keep a sliding window just over the hold time, so a long steady hold can still pass later.
        while (track.size > minFrames && frame.timeMs - track[1].timeMs >= holdMs) track.removeFirst()

        val held = frame.timeMs - track.first().timeMs >= holdMs && track.size >= minFrames
        return if (held && moved()) SinkStatus.Confirmed else SinkStatus.Holding(progress(frame.timeMs))
    }

    private fun progress(nowMs: Long): Float {
        val first = track.firstOrNull() ?: return 0f
        // Capped below 1 until it's confirmed, so a still camera shows "almost" rather than "done".
        return ((nowMs - first.timeMs).toFloat() / holdMs).coerceIn(0f, 0.95f)
    }

    private fun moved(): Boolean {
        val first = track.first().box
        return track.any { s ->
            hypot(s.box.centerX - first.centerX, s.box.centerY - first.centerY) >= minMovement ||
                abs(s.box.width - first.width) >= minMovement
        }
    }

    private fun onScreen(sink: Detection, all: List<Detection>): Boolean {
        if (sink.box.area <= 0f) return false
        return all.any { d ->
            d.label in SCREENS && d.score >= SCREEN_MIN_SCORE &&
                d.box.intersectionArea(sink.box) / sink.box.area >= SCREEN_COVERAGE
        }
    }

    companion object {
        const val SINK = "sink"
        val SCREENS = setOf("tv", "laptop", "cell phone")
        private const val SCREEN_MIN_SCORE = 0.3f
        private const val SCREEN_COVERAGE = 0.6f
        private const val DARK_BRIGHTNESS = 28f
    }
}
