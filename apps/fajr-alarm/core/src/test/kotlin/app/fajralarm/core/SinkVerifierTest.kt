package app.fajralarm.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SinkVerifierTest {

    private val sinkBox = Box(0.3f, 0.4f, 0.7f, 0.8f)

    private fun sink(dx: Float = 0f, score: Float = 0.7f) =
        Detection("sink", score, sinkBox.copy(left = sinkBox.left + dx, right = sinkBox.right + dx))

    private fun frame(t: Long, vararg d: Detection, brightness: Float = 120f) = FrameResult(t, brightness, d.toList())

    /** Frames every 333 ms (the camera's analysis rate) for [ms], the box drifting like a hand-held phone. */
    private fun feed(v: SinkVerifier, ms: Long, start: Long = 0, drift: Float = 0.01f, extra: List<Detection> = emptyList()): SinkStatus {
        var last: SinkStatus = SinkStatus.Searching
        var t = start
        var i = 0
        while (t <= start + ms) {
            val dx = if (i % 2 == 0) 0f else drift
            last = v.offer(FrameResult(t, 120f, listOf(sink(dx)) + extra))
            t += 333
            i++
        }
        return last
    }

    @Test
    fun confirmsAfterHoldingASinkInAMovingHand() {
        assertEquals(SinkStatus.Confirmed, feed(SinkVerifier(), 2_400, drift = 0.06f))
    }

    @Test
    fun notConfirmedBeforeTheHoldTime() {
        val s = feed(SinkVerifier(), 1_500, drift = 0.06f)
        assertIs<SinkStatus.Holding>(s)
        assertTrue(s.progress in 0.5f..0.95f)
    }

    @Test
    fun aPerfectlyStillCameraIsNotEnough() {
        assertIs<SinkStatus.Holding>(feed(SinkVerifier(), 4_000, drift = 0f))
    }

    @Test
    fun detectorJitterOnAStillPhoneIsNotEnough() {
        assertIs<SinkStatus.Holding>(feed(SinkVerifier(), 4_000, drift = 0.03f))
    }

    @Test
    fun aSinkInsideAPhoneScreenDoesNotCount() {
        val phone = Detection("cell phone", 0.6f, Box(0.2f, 0.3f, 0.85f, 0.9f))
        assertEquals(SinkStatus.OnScreen, feed(SinkVerifier(), 3_000, drift = 0.06f, extra = listOf(phone)))
    }

    @Test
    fun aScreenElsewhereInTheRoomIsFine() {
        val tv = Detection("tv", 0.8f, Box(0.0f, 0.0f, 0.2f, 0.2f))
        assertEquals(SinkStatus.Confirmed, feed(SinkVerifier(), 2_400, drift = 0.06f, extra = listOf(tv)))
    }

    @Test
    fun darknessResetsTheCheck() {
        val v = SinkVerifier()
        feed(v, 1_500, drift = 0.06f)
        assertEquals(SinkStatus.Dark, v.offer(frame(1_900, sink(), brightness = 10f)))
        assertIs<SinkStatus.Holding>(feed(v, 1_500, start = 2_000, drift = 0.06f))
    }

    @Test
    fun aShortMissIsForgivenButALongOneStartsOver() {
        val v = SinkVerifier()
        feed(v, 1_000, drift = 0.06f)
        assertIs<SinkStatus.Holding>(v.offer(frame(1_300)))
        assertEquals(SinkStatus.Searching, v.offer(frame(2_500)))
    }

    @Test
    fun lowConfidenceSinksAreIgnored() {
        val v = SinkVerifier()
        assertEquals(SinkStatus.Searching, v.offer(frame(0, sink(score = 0.2f))))
    }
}
