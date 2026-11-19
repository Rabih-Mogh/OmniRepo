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
        // Two ways to tell the caller is signed with this app's key; either is enough. The
        // permission is the direct one, but an app installed before this one defined it may
        // not hold it yet. Comparing signatures works then, as long as Android lets this app
        // see the caller, which it does for an app that is reading from this provider.
        val caller = Binder.getCallingUid()
        val sameKey = context.checkCallingPermission(READ_MODEL) == PackageManager.PERMISSION_GRANTED ||
            context.packageManager.checkSignatures(Process.myUid(), caller) == PackageManager.SIGNATURE_MATCH
        if (!sameKey) throw SecurityException("The model is only shared with apps signed with the same key")
        val store = ModelStore(context)
        if (!store.isReady) throw FileNotFoundException("The model hasn't been downloaded yet")
        return ParcelFileDescriptor.open(store.modelFile, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "application/octet-stream"

    private companion object {
        const val READ_MODEL = "app.accentcoach.permission.READ_MODEL"
    }

    // Nothing to list or change: the provider only hands out the one file.
    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
}
