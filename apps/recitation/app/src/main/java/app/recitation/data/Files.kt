package app.recitation.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Replaces this file's contents with [text] in one step. Whatever happens meanwhile (the app
 * is closed, the phone loses power) the file holds the old text or the new, never half of
 * one: the new text is written beside it, pushed out to the storage, and then given its name.
 */
fun File.writeWhole(text: String) {
    parentFile?.mkdirs()
    val part = File("$path.part")
    FileOutputStream(part).use { out ->
        out.write(text.toByteArray())
        out.fd.sync()
    }
    if (!part.renameTo(this)) throw IOException("couldn't keep $name")
}

/** Puts a file that can't be read to one side, so that the next save doesn't write over what might yet be recovered from it. */
fun File.setAside() {
    if (exists()) renameTo(File("$path.unreadable"))
}

/** The phone's storage is full, or too nearly so for what is about to be kept on it. */
class NoRoom : Exception("no room left on the phone")

/** Whether [e] is the storage saying it is full. */
fun isNoRoom(e: IOException): Boolean = generateSequence<Throwable>(e) { it.cause }.any { "ENOSPC" in it.message.orEmpty() || "No space left" in it.message.orEmpty() }
