package app.recitation

import android.content.Context
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import app.recitation.data.Library
import app.recitation.data.Playlist
import app.recitation.data.Track
import app.recitation.prepare.PrepareWorker
import app.recitation.speech.EngineStore
import app.recitation.speech.ModelStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.io.RandomAccessFile

/**
 * With no connection the work still runs: a recording with everything it needs on the phone
 * is taken up, and one that needs something fetched is left waiting, unharmed, for the
 * connection to come back.
 */
@RunWith(AndroidJUnit4::class)
class PrepareOfflineTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** An empty file of the right length: enough to count as being on the phone. */
    private fun standIn(file: File, bytes: Long) {
        file.parentFile!!.mkdirs()
        RandomAccessFile(file, "rw").use { it.setLength(bytes) }
    }

    @Test
    fun withNoConnectionOnlyWhatIsOnThePhoneIsWorkedOn() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        shadowOf(connectivity).setActiveNetworkInfo(null)
        shadowOf(connectivity).setDefaultNetworkActive(false)
        assertNull("the test phone is offline", connectivity.activeNetwork)

        val library = Library.get(context)
        library.add(
            Playlist(
                "https://example.org/offline", "Offline", "Test",
                listOf(Track("offline-needs", "Needs fetching", 60, 1, wanted = true), Track("offline-here", "All here", 60, 1, wanted = true)),
            ),
        )
        // The second recording has its sound, its surah's words, the model and the engine on the
        // phone. All but the words are stand-ins, so working on it fails; that it was tried is the point.
        standIn(File(library.audioPart("offline-here", "m4a").path.removeSuffix(".part")), 1_000)
        File(context.filesDir, "quran").mkdirs()
        File(context.filesDir, "quran/1.v2.json").writeText(List(7) { """{"w":[["قُلْ","Say"]],"t":"Say"}""" }.joinToString(",", "[", "]"))
        // Its timings were cut off half written, as by an app closed in the middle of keeping them.
        library.timings("offline-here", 1).writeText("0.9 12000\n0,3,0 1000,3,")
        standIn(File(context.filesDir, "model/phonemes_int8.onnx"), ModelStore.MODEL_BYTES)
        for (part in EngineStore.PARTS) standIn(File(context.filesDir, "engine/${EngineStore.VERSION}/${part.name}"), part.bytes)

        val worker = TestListenableWorkerBuilder<PrepareWorker>(context).build()
        runBlocking { worker.doWork() }

        val needs = library.track("offline-needs")!!
        assertTrue("it stays in the queue", needs.wanted)
        assertNull("and isn't marked as failed", needs.error)
        val here = library.track("offline-here")!!
        assertNotNull("the one that was all here was taken up", here.error)
        assertTrue("and got past timings that couldn't be read, as far as being playable", here.playable)

        val waiting = WorkManager.getInstance(context).getWorkInfosForUniqueWork(PrepareWorker.WAITING_NAME).get()
        assertEquals("a run is left waiting for the connection", listOf(WorkInfo.State.ENQUEUED), waiting.map { it.state })
    }
}
