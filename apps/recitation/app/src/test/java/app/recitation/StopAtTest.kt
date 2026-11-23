package app.recitation

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.test.utils.FakeMediaSourceFactory
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recitation.player.StopAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The recitation stops at the end of the last verse chosen, by the player's own doing: with
 * no screen watching how far it has got, as when the phone is locked. The player here is
 * Media3's own, run on the test's clock, with three ten-second recordings.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class StopAtTest {
    private val player = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext<Context>())
        .setMediaSourceFactory(FakeMediaSourceFactory())
        .build().apply {
            setMediaItems(listOf("a", "b", "c").map { MediaItem.Builder().setMediaId(it).setUri("https://example.org/$it").build() })
            prepare()
        }
    private val stop = StopAt(player)

    private fun playUntilItStops() {
        player.play()
        TestPlayerRunHelper.runUntilPlayWhenReady(player, false)
    }

    @Test
    fun itPausesWhereToldInTheRecordingNamed() {
        stop.set("b", 3_000)
        playUntilItStops()
        assertEquals("b", player.currentMediaItem!!.mediaId)
        assertTrue("stopped at ${player.currentPosition}", player.currentPosition in 3_000..3_300)

        // Played again from there it carries on, as the reader pressing play at the end would have it.
        player.play()
        TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED)
        assertEquals("c", player.currentMediaItem!!.mediaId)
    }

    @Test
    fun goingBackAndRecitingTheSameVersesStopsThereAgain() {
        stop.set("a", 4_000)
        playUntilItStops()
        player.seekTo(1_000)
        playUntilItStops()
        assertTrue("stopped at ${player.currentPosition}", player.currentPosition in 4_000..4_300)
    }

    @Test
    fun theStopIsTakenAwayOrMovedWhenAskedAndStaysWhenRecordingsAreQueuedAround() {
        stop.set("a", 4_000)
        stop.set("a", -1)
        stop.set("b", 2_000)
        stop.set("b", 6_000)
        // As when the rest of a playlist is queued around the recording being played.
        player.addMediaItems(0, listOf(MediaItem.Builder().setMediaId("before").setUri("https://example.org/before").build()))
        playUntilItStops()
        assertEquals("b", player.currentMediaItem!!.mediaId)
        assertTrue("stopped at ${player.currentPosition}", player.currentPosition in 6_000..6_300)
    }

    @Test
    fun aRecordingThePlayerDoesNotHaveStopsNothing() {
        stop.set("elsewhere", 1_000)
        player.play()
        TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED)
        assertFalse(player.isPlaying)
        assertEquals("c", player.currentMediaItem!!.mediaId)
    }
}
