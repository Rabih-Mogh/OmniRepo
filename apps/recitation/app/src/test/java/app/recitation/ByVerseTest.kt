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
import app.recitation.player.ByVerse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Next and previous, as a headset or a car sends them, go by verse. The player is Media3's
 * own on the test's clock, with three recordings; the middle one has verses beginning at
 * 0, 2, 10 and 20 seconds, and nothing is known of the others.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class ByVerseTest {
    private val inner = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext<Context>())
        .setMediaSourceFactory(FakeMediaSourceFactory())
        .build().apply {
            setMediaItems(listOf("a", "b", "c").map { MediaItem.Builder().setMediaId(it).setUri("https://example.org/$it").build() })
            prepare()
            TestPlayerRunHelper.runUntilPlaybackState(this, Player.STATE_READY)
        }
    private val player = ByVerse(inner) { if (it == "b") intArrayOf(0, 2_000, 10_000, 20_000) else IntArray(0) }

    private fun at() = player.currentMediaItem!!.mediaId to player.currentPosition

    @Test
    fun nextGoesToTheNextVerseAndPastTheLastToTheNextRecording() {
        player.seekTo(1, 0)
        player.seekToNext()
        assertEquals("b" to 2_000L, at())
        player.seekTo(10_500)
        player.seekToNext()
        assertEquals("b" to 20_000L, at())
        player.seekToNext()
        assertEquals("c", at().first)
    }

    @Test
    fun previousGoesToTheStartOfTheVerseAndFromThereToTheOneBefore() {
        player.seekTo(1, 15_000)
        player.seekToPrevious()
        assertEquals("well into a verse: its start", "b" to 10_000L, at())
        player.seekToPrevious()
        assertEquals("at its start: the verse before", "b" to 2_000L, at())
        player.seekTo(500)
        player.seekToPrevious()
        assertEquals("from the first verse: the recording before", "a", at().first)
    }

    @Test
    fun whereNoVersesAreKnownTheyGoByRecording() {
        player.seekToNext()
        assertEquals("b", at().first)
        player.seekTo(2, 1_000)
        player.seekToPrevious()
        assertEquals("b", at().first)
    }

    @Test
    fun theButtonsAreOnOfferInTheLastRecordingToo() {
        player.seekTo(2, 0)
        assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
        assertTrue(player.availableCommands.contains(Player.COMMAND_SEEK_TO_PREVIOUS))
        // A controller that only knows of going by recording gets verses all the same.
        player.seekTo(1, 0)
        player.seekToNextMediaItem()
        assertEquals("b" to 2_000L, at())
    }
}
