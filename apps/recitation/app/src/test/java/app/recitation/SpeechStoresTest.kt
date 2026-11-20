package app.recitation

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recitation.speech.EngineStore
import app.recitation.speech.ModelStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Where the speech engine and the speech model come from: Accent Coach when it is on the
 * phone, a download when it isn't, and in either case only if the checksums match.
 * Small stand-in files take the place of the real 34 MB and 318 MB ones.
 */
@RunWith(AndroidJUnit4::class)
class SpeechStoresTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private val first = ByteArray(70_000) { (it * 7).toByte() }
    private val second = ByteArray(9_000) { (it * 13).toByte() }
    private val parts get() = listOf(EngineStore.Part("libone.so", first.size.toLong(), sha256(first)), EngineStore.Part("libtwo.so", second.size.toLong(), sha256(second)))

    /** A folder standing in for the repo, holding each engine file gzipped. */
    private fun repo(one: ByteArray = first, two: ByteArray = second): String {
        val dir = folder.newFolder()
        for ((name, bytes) in listOf("libone.so" to one, "libtwo.so" to two)) {
            GZIPOutputStream(File(dir, "$name.gz").outputStream()).use { it.write(bytes) }
        }
        return dir.toURI().toURL().toString()
    }

    /** Installs a stand-in Accent Coach whose package holds the given native libraries. */
    private fun installAccentCoach(libraries: Map<String, ByteArray>) {
        val apk = folder.newFile()
        ZipOutputStream(apk.outputStream()).use { zip ->
            for ((name, bytes) in libraries) {
                zip.putNextEntry(ZipEntry("lib/arm64-v8a/$name"))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        val app = ApplicationInfo().apply {
            packageName = ModelStore.ACCENT_COACH
            sourceDir = apk.path
        }
        shadowOf(context.packageManager).installPackage(
            PackageInfo().apply {
                packageName = ModelStore.ACCENT_COACH
                applicationInfo = app
            },
        )
    }

    private fun engineFile(name: String) = File(context.filesDir, "engine/${EngineStore.VERSION}/$name")

    @Test
    fun theEngineIsDownloadedOnceWhenAccentCoachIsNotThere() = runBlocking {
        val store = EngineStore(context, parts, repo())
        assertFalse(store.isReady)
        var last = 0f
        store.obtain { last = it }
        assertTrue(store.isReady)
        assertEquals(1f, last, 0.001f)
        assertArrayEquals(first, engineFile("libone.so").readBytes())
        assertArrayEquals(second, engineFile("libtwo.so").readBytes())
        // It is there now: nothing is fetched again, even with nowhere to fetch from.
        EngineStore(context, parts, "file:///nowhere/").obtain { fail("nothing should be fetched") }
    }

    @Test
    fun theEngineIsCopiedOutOfAccentCoachWithoutADownload() = runBlocking {
        installAccentCoach(mapOf("libone.so" to first, "libtwo.so" to second))
        val store = EngineStore(context, parts, "file:///nowhere/")
        store.obtain { }
        assertTrue(store.isReady)
        assertArrayEquals(first, engineFile("libone.so").readBytes())
    }

    @Test
    fun anAccentCoachWithADifferentEngineIsNotUsed() = runBlocking {
        // Same size, other bytes: an Accent Coach built with another release of the engine.
        installAccentCoach(mapOf("libone.so" to ByteArray(first.size) { 1 }, "libtwo.so" to second))
        val store = EngineStore(context, parts, repo())
        store.obtain { }
        assertArrayEquals(first, engineFile("libone.so").readBytes())
    }

    @Test
    fun aDamagedDownloadIsNotKept() = runBlocking {
        val store = EngineStore(context, parts, repo(one = ByteArray(first.size) { 2 }))
        try {
            store.obtain { }
            fail("a damaged engine was accepted")
        } catch (e: IOException) {
            assertFalse(store.isReady)
            assertFalse(engineFile("libone.so").exists())
        }
    }

    @Test
    fun anEngineOfAnotherVersionIsClearedAway() = runBlocking {
        val old = File(context.filesDir, "engine/0.9.0/libone.so").apply { parentFile!!.mkdirs(); writeBytes(first) }
        EngineStore(context, parts, repo()).obtain { }
        assertFalse(old.exists())
    }

    // ---- the model

    private val model = ByteArray(200_000) { (it * 31).toByte() }
    private val ownCopy get() = File(context.filesDir, "model/phonemes_int8.onnx")

    private fun store() = ModelStore(context, model.size.toLong(), sha256(model))

    /** Makes the stand-in Accent Coach lend [bytes] as its model, the way the real one does: as an open file. */
    private fun accentCoachLends(bytes: ByteArray?) {
        installAccentCoach(emptyMap())
        val file = bytes?.let { folder.newFile().apply { writeBytes(it) } }
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
                ParcelFileDescriptor.open(file ?: throw FileNotFoundException("not downloaded yet"), ParcelFileDescriptor.MODE_READ_ONLY)
            override fun getType(uri: Uri): String = "application/octet-stream"
            override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
        }
        val authority = ModelStore.ACCENT_COACH_MODEL.authority!!
        val info = ProviderInfo().apply {
            this.authority = authority
            name = "app.accentcoach.speech.ModelProvider"
            packageName = ModelStore.ACCENT_COACH
            applicationInfo = context.packageManager.getApplicationInfo(ModelStore.ACCENT_COACH, 0)
            exported = true
        }
        shadowOf(context.packageManager).addOrUpdateProvider(info)
        provider.attachInfo(context, info)
        ShadowContentResolver.registerProviderInternal(authority, provider)
    }

    private fun keepOwnCopy() {
        ownCopy.parentFile!!.mkdirs()
        ownCopy.writeBytes(model)
    }

    @Test
    fun accentCoachsModelIsReadInPlaceAndTheAppsOwnCopyIsDeleted() = runBlocking {
        keepOwnCopy()
        accentCoachLends(model)
        val store = store()
        assertEquals(ModelStore.Lender.READY to null, store.lender())
        store.obtain(download = false) { _, _ -> }
        assertFalse("the copy this app made earlier is gone", ownCopy.exists())
        val opened = store.open()
        assertNull(opened.file)
        val read = ByteArray(model.size).also { opened.bytes!!.get(it) }
        assertArrayEquals(model, read)
    }

    @Test
    fun tidyingAtStartFreesTheOwnCopyOnlyWhenAccentCoachsIsGood() = runBlocking {
        keepOwnCopy()
        // No Accent Coach: the app's own copy is all there is, and stays.
        assertFalse(store().tidy())
        assertTrue(ownCopy.exists())
        // Accent Coach arrives with a damaged file: still stays.
        accentCoachLends(ByteArray(model.size) { 3 })
        assertFalse(store().tidy())
        assertTrue(ownCopy.exists())
        // And with the real one: freed.
        accentCoachLends(model)
        assertTrue(store().tidy())
        assertFalse(ownCopy.exists())
    }

    @Test
    fun withoutAccentCoachTheAppsOwnCopyIsUsed() = runBlocking {
        keepOwnCopy()
        val store = store()
        store.obtain(download = false) { _, _ -> }
        assertEquals(ownCopy, store.open().file)
        assertTrue(ownCopy.exists())
    }

    @Test
    fun aDamagedAccentCoachModelIsRefusedRatherThanUsed() = runBlocking {
        accentCoachLends(ByteArray(model.size) { 3 })
        try {
            store().obtain(download = false) { _, _ -> }
            fail("a damaged model was accepted")
        } catch (e: ModelStore.Unavailable) {
            assertEquals(ModelStore.Lender.REFUSED, e.reason)
        }
    }

    @Test
    fun anAccentCoachThatHasNotDownloadedItsModelSaysSo() = runBlocking {
        accentCoachLends(null)
        try {
            store().obtain(download = false) { _, _ -> }
            fail("there was no model to use")
        } catch (e: ModelStore.Unavailable) {
            assertEquals(ModelStore.Lender.NO_MODEL, e.reason)
        }
    }
}
