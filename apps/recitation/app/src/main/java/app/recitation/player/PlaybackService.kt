package app.recitation.player

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaButtonReceiver
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import app.recitation.MainActivity
import app.recitation.R
import app.recitation.core.SURAHS
import app.recitation.core.Timings
import app.recitation.data.Library
import app.recitation.data.Settings
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Plays the recitation. It is a service so the sound carries on with the screen off, in
 * another app, or after this app is closed, with play and pause in the notification and on
 * the lock screen. The screens talk to it through a media controller.
 */
@OptIn(markerClass = [UnstableApi::class]) // the notification's icon and the stop at a verse's end go through parts of Media3 still marked unstable
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private var stop: StopAt? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            // Pause for calls and other apps' sound, and when headphones are pulled out.
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
            .setHandleAudioBecomingNoisy(true)
            // Keep the phone's processor awake while reciting; otherwise the sound can stall
            // some time after the screen turns off or the app is left.
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        stop = StopAt(player)
        // Tapping the notification opens the surah being recited, not just the app.
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java).setAction(OPEN_RECITING).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Next and previous, on a headset, in a car or in the notification, go by verse.
        session = MediaSession.Builder(this, ByVerse(player, ::versesOf)).setSessionActivity(open).setCallback(Asked()).build()
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_notification) })
    }

    /** What the screens can ask of the player beyond playing and pausing. */
    private inner class Asked : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(STOP_AT).build())
                .build()

        /**
         * Play pressed on a headset or in a car with the app closed: the recitation
         * listened to last is taken up where it was left.
         */
        override fun onPlaybackResumption(session: MediaSession, controller: MediaSession.ControllerInfo): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val (item, ms) = leftOff(this@PlaybackService) ?: return Futures.immediateFailedFuture(UnsupportedOperationException("nothing to take up"))
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(listOf(item), 0, ms))
        }

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, command: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            if (command.customAction != STOP_AT.customAction) return super.onCustomCommand(session, controller, command, args)
            stop?.set(args.getString(TRACK), args.getLong(MS, -1))
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    private var verses: Triple<String, Long, IntArray>? = null // of which recording, as its timings stood when, and where its verses begin

    /**
     * Where the verses of recording [id] begin, in ms, as its timings on the phone have
     * them: the first at the very start, so the opening formulas are heard with it. Read
     * when asked for, so it is right with the screen off and for whichever recording the
     * player has moved on to; empty when nothing is known.
     */
    private fun versesOf(id: String): IntArray {
        val library = Library.get(this)
        val track = library.track(id) ?: return IntArray(0)
        val file = library.timings(id, track.surah)
        verses?.let { (of, asOf, starts) -> if (of == id && asOf == file.lastModified()) return starts }
        val timings = runCatching { Timings.decode(file.readText()) }.getOrNull() ?: return IntArray(0)
        val count = SURAHS.getOrNull(track.surah - 1)?.verses ?: return IntArray(0)
        val starts = (0 until count).mapNotNull { if (it == 0) 0 else timings.startOfVerse(it) }.distinct().sorted().toIntArray()
        verses = Triple(id, file.lastModified(), starts)
        return starts
    }

    /** Swiping the app away stops it, unless it is in the middle of reciting. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    companion object {
        /**
         * Asks for the recitation to pause when recording [TRACK] reaches [MS]: the end of
         * the last verse chosen to be recited. A negative [MS] takes the stop away.
         */
        val STOP_AT = SessionCommand("app.recitation.STOP_AT", Bundle.EMPTY)
        const val TRACK = "track"
        const val MS = "ms"

        fun stopAt(track: String, ms: Long) = Bundle().apply {
            putString(TRACK, track)
            putLong(MS, ms)
        }

        /** Asks the app, from the notification, to show the surah being recited. */
        const val OPEN_RECITING = "app.recitation.OPEN_RECITING"

        /** Whether there is a recitation that was left part of the way through, with its sound still on the phone. */
        fun canTakeUp(context: Context): Boolean = Settings.lastRead(context)?.let { Library.get(context).audio(it.trackId) } != null

        /** The recording listened to last, ready for the player, and how far into it it was left; null if there is none on the phone. */
        fun leftOff(context: Context): Pair<MediaItem, Long>? {
            val place = Settings.lastRead(context) ?: return null
            val file = Library.get(context).audio(place.trackId) ?: return null
            val surah = SURAHS.getOrNull(place.surah - 1) ?: return null
            val metadata = MediaMetadata.Builder().setTitle(surah.arabic)
                .setArtworkData(Artwork.of(context, surah), MediaMetadata.PICTURE_TYPE_FRONT_COVER).build()
            return MediaItem.Builder().setMediaId(place.trackId).setUri(Uri.fromFile(file)).setMediaMetadata(metadata).build() to place.ms.toLong()
        }
    }
}

/**
 * [player] with next and previous going by verse rather than by recording: what a button
 * on a headset or a car's wheel is for, when a recording is a surah an hour long.
 * [versesOf] says where the verses of a recording begin, in ms; where it knows nothing,
 * and past the last verse, next and previous are the player's own.
 */
@OptIn(markerClass = [UnstableApi::class])
class ByVerse(player: Player, private val versesOf: (String) -> IntArray) : ForwardingPlayer(player) {

    private fun verses(): IntArray = currentMediaItem?.mediaId?.let(versesOf) ?: IntArray(0)

    override fun seekToNext() {
        val at = currentPosition
        val next = verses().firstOrNull { it > at + NEARLY_THERE_MS }
        if (next != null) seekTo(next.toLong()) else super.seekToNext()
    }

    override fun seekToPrevious() {
        val at = currentPosition
        val verses = verses()
        val here = verses.lastOrNull { it <= at }
        // Well into a verse: back to its start. At its start: to the verse before.
        val to = if (here == null || at - here > BACK_TO_START_MS) here else verses.lastOrNull { it < here }
        if (to != null) seekTo(to.toLong()) else super.seekToPrevious()
    }

    override fun seekToNextMediaItem() = seekToNext()

    override fun seekToPreviousMediaItem() = seekToPrevious()

    // On offer whatever the player has queued: within a recording there are still verses to go to.
    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon().addAll(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM).build()

    override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)

    private companion object {
        /** A verse this close ahead is the one being gone to already. */
        const val NEARLY_THERE_MS = 300

        /** Further into a verse than this, previous goes to its start, as a music player's does. */
        const val BACK_TO_START_MS = 3_000
    }
}

/**
 * A play button pressed while the app is closed. When there is a recitation to take up,
 * Media3's own receiver starts the playback service for it; when there is none, nothing
 * is started, since a service started for a button has to begin playing something.
 */
@OptIn(markerClass = [UnstableApi::class])
class ResumeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (PlaybackService.canTakeUp(context)) MediaButtonReceiver().onReceive(context, intent)
    }
}

/**
 * Pauses [player] when a recording reaches a given place. The player itself keeps the place,
 * so it is acted on with the screen off or the app closed, when no screen is there to watch
 * how far the recitation has got.
 */
@OptIn(markerClass = [UnstableApi::class])
class StopAt(private val player: ExoPlayer) {
    private var waiting: PlayerMessage? = null

    /** Pause when recording [track] gets to [ms]; with a negative [ms], or a recording the player doesn't have, don't. */
    fun set(track: String?, ms: Long) {
        waiting?.cancel()
        waiting = null
        if (ms < 0) return
        val item = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == track } ?: return
        // Kept after it is acted on: the reader may go back and recite the same verses again.
        waiting = player.createMessage { _, _ -> player.pause() }
            .setLooper(Looper.getMainLooper())
            .setPosition(item, ms)
            .setDeleteAfterDelivery(false)
            .send()
    }
}
