package app.recitation

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import app.recitation.data.Library
import app.recitation.data.Playlist
import app.recitation.data.Track
import app.recitation.prepare.PrepareWorker
import app.recitation.prepare.Sounds
import app.recitation.speech.EngineStore
import app.recitation.speech.ModelStore
import app.recitation.yt.YouTube
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.io.File
import java.io.RandomAccessFile
import java.util.Collections

/**
 * The sounds are fetched beside the listening, not in its way: a recording whose sound is
 * on the phone is taken up while another's is still being downloaded, and the recording
 * the reader opens has its sound fetched first. YouTube is stood in for; the sound, the
 * model and the engine are stand-ins too, so the listening itself fails. That each
 * recording got as far as it could is the point.
 */
@RunWith(AndroidJUnit4::class)
class PrepareLanesTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val real = PrepareWorker.sounds

    /** An empty file of the right length: enough to count as being on the phone. */
    private fun standIn(file: File, bytes: Long) {
        file.parentFile!!.mkdirs()
        RandomAccessFile(file, "rw").use { it.setLength(bytes) }
    }

    /** YouTube, stood in for: [onDownload] is what happens while a recording's sound is being downloaded. */
    private fun sounds(asked: MutableList<String>, onDownload: (id: String, stillWanted: () -> Boolean) -> Unit) = object : Sounds {
        override val gapMs = 0L
        override fun audio(id: String) = YouTube.Audio("https://example.org/$id", "m4a", 128)
        override fun download(audio: YouTube.Audio, part: File, sizeNote: File, stillWanted: () -> Boolean, onProgress: (Float) -> Unit) {
            val id = audio.url.substringAfterLast('/')
            asked += id
            onProgress(0.5f)
            onDownload(id, stillWanted)
            if (!stillWanted()) throw InterruptedException()
            standIn(part, 1_000)
            onProgress(1f)
        }
    }

    /** The recordings of the test under way: the library is one for all the tests, and may hold what another left waiting. */
    private var mine = emptySet<String>()

    private fun library(vararg tracks: Track): Library {
        mine = tracks.map { it.id }.toSet()
        val library = Library.get(context)
        library.add(Playlist("https://example.org/lanes-${tracks.first().id}", "Lanes", "Test", tracks.toList()))
        File(context.filesDir, "quran").mkdirs()
        File(context.filesDir, "quran/1.v2.json").writeText(List(7) { """{"w":[["قُلْ","Say"]],"t":"Say"}""" }.joinToString(",", "[", "]"))
        standIn(File(context.filesDir, "model/phonemes_int8.onnx"), ModelStore.MODEL_BYTES)
        for (part in EngineStore.PARTS) standIn(File(context.filesDir, "engine/${EngineStore.VERSION}/${part.name}"), part.bytes)
        return library
    }

    @Before
    fun online() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(connectivity).setNetworkCapabilities(connectivity.activeNetwork, capabilities)
    }

    @After
    fun restore() {
        PrepareWorker.sounds = real
        PrepareWorker.preferred = null
    }

    /** Runs the work to its end; a run that doesn't end is a failure, not a test that hangs. */
    private fun run() = runBlocking { withTimeout(120_000) { TestListenableWorkerBuilder<PrepareWorker>(context).build().doWork() } }

    private fun wait(what: () -> Boolean): Boolean {
        val until = System.currentTimeMillis() + 30_000
        while (!what()) {
            if (System.currentTimeMillis() > until) return false
            Thread.sleep(20)
        }
        return true
    }

    @Test
    fun aRecordingIsListenedToWhileAnothersSoundIsStillBeingDownloaded() {
        val library = library(
            Track("lanes-far", "Far", 60, 1, wanted = true),
            Track("lanes-here", "Here", 60, 1, wanted = true),
            Track("lanes-farther", "Farther", 60, 1, wanted = true),
        )
        standIn(File(library.audioPart("lanes-here", "m4a").path.removeSuffix(".part")), 1_000)

        val asked = Collections.synchronizedList(ArrayList<String>())
        var takenUpMeanwhile = false
        var shownMeanwhile = false
        // The first download doesn't end until the recording that has its sound has been taken up and dealt with.
        PrepareWorker.sounds = sounds(asked) { id, _ ->
            if (id == "lanes-far") {
                shownMeanwhile = PrepareWorker.fetching.value?.let { it.trackId == id && it.fraction == 0.5f } == true
                takenUpMeanwhile = wait { library.track("lanes-here")?.error != null }
            }
        }
        run()

        assertTrue("the one with its sound was listened to while the other's was being downloaded", takenUpMeanwhile)
        assertTrue("and the download showed as its own line of work meanwhile", shownMeanwhile)
        assertEquals("the sounds were fetched one after another, in order", listOf("lanes-far", "lanes-farther"), asked.filter { it in mine })
        for (id in listOf("lanes-far", "lanes-farther")) {
            assertNotNull("$id has its sound on the phone", library.audio(id))
            assertTrue("$id can be played before it has been listened to", library.track(id)!!.playable)
            assertTrue("and has the timings to play by", library.timings(id, 1).exists())
            assertNotNull("$id was then taken up by the listening", library.track(id)!!.error)
        }
        assertNull("nothing is left showing as being fetched", PrepareWorker.fetching.value)
        assertNull(PrepareWorker.progress.value)
    }

    @Test
    fun theSoundOfTheRecordingTheReaderOpensIsFetchedFirst() {
        val library = library(Track("lanes-first", "First", 60, 1, wanted = true), Track("lanes-opened", "Opened", 60, 1, wanted = true))

        val asked = Collections.synchronizedList(ArrayList<String>())
        var steppedAside = false
        var opened = false
        // The reader opens the second recording while the first one's sound is being downloaded.
        PrepareWorker.sounds = sounds(asked) { id, stillWanted ->
            if (id == "lanes-first" && !opened) {
                opened = true
                PrepareWorker.preferred = "lanes-opened"
                steppedAside = wait { !stillWanted() }
            }
        }
        run()

        assertTrue("the download under way stepped aside", steppedAside)
        assertEquals("the opened one's sound came next, and then the first one's again", listOf("lanes-first", "lanes-opened", "lanes-first"), asked.filter { it in mine })
        assertNotNull(library.audio("lanes-opened"))
        assertNotNull(library.audio("lanes-first"))
    }
}
