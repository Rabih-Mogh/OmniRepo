package app.accentcoach.speech

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.ParcelFileDescriptor
import android.os.Process
import java.io.FileNotFoundException

/**
 * Lends the downloaded speech model to the other apps in this repo that use it
 * (Recitation), so they can copy it instead of downloading 318 MB a second time.
 * Read-only, and only for apps signed with the same key as this one.
 */
class ModelProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val context = context ?: throw FileNotFoundException()
        if (mode != "r") throw SecurityException("The model can only be read")
        // Android's own permission checks can't express "same signing key" for a provider
        // without both apps being installed in the right order, so the caller is checked here.
        if (context.packageManager.checkSignatures(Process.myUid(), Binder.getCallingUid()) != PackageManager.SIGNATURE_MATCH) {
            throw SecurityException("The model is only shared with apps signed with the same key")
        }
        val store = ModelStore(context)
        if (!store.isReady) throw FileNotFoundException("The model hasn't been downloaded yet")
        return ParcelFileDescriptor.open(store.modelFile, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "application/octet-stream"

    // Nothing to list or change: the provider only hands out the one file.
    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
}
