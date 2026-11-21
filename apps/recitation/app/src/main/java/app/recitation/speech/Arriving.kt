package app.recitation.speech

import android.media.MediaDataSource
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * A recording that is still being downloaded, read as far as it has arrived: reading past
 * that waits for more, until [over] says the download has ended. The download writes to
 * [part] and renames it to [whole] when it is all there.
 *
 * [stillWanted] is asked while waiting; when it says no, the read fails, so nothing is left
 * waiting for a download that was given up.
 */
class Arriving(private val part: File, private val whole: File, private val stillWanted: () -> Boolean) : MediaDataSource() {

    /** The download has ended, whether or not it got everything. */
    @Volatile var over = false

    private var input: RandomAccessFile? = null

    @Synchronized
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (size <= 0) return 0
        while (true) {
            // Asked before the length is looked at, so the last bytes to arrive aren't missed.
            val ended = over
            // Once open, the file is the same one whatever it is renamed to.
            val file = input ?: (part.takeIf { it.exists() } ?: whole.takeIf { it.exists() })?.let { RandomAccessFile(it, "r") }?.also { input = it }
            val have = file?.length() ?: 0
            if (file != null && position < have) {
                file.seek(position)
                return file.read(buffer, offset, minOf(size.toLong(), have - position).toInt())
            }
            if (ended) return -1
            if (!stillWanted()) throw IOException("the download was stopped")
            Thread.sleep(50)
        }
    }

    /** Not known until all of it has arrived. */
    override fun getSize(): Long = -1

    @Synchronized
    override fun close() {
        input?.close()
        input = null
    }
}
