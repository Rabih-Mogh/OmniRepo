package app.recitation.core

/**
 * Places a surah's words while its recording is still being listened to, so the words can
 * light up as far as the listening has got instead of only once it is finished.
 *
 * Rows arrive a stretch at a time from [Hearing]. Each time, the new rows are placed
 * carrying on from where the last stretch left off, and all but the last few seconds of
 * them are settled; those last seconds wait for the sounds after them. This is a rough
 * first placing: on an unclear recording it can fall behind. Once the whole recording has
 * been heard, [Aligner.align] places everything again, properly, and that is what is kept.
 */
class LiveAlignment(private val recital: Recital) {

    private val width = SoundColumns.WIDTH
    private var frames = IntArray(1024)
    private var rows = FloatArray(1024 * width)
    private var count = 0

    private val starts = IntArray(recital.phones.size) { -1 }
    private var settled = 0 // rows whose place is settled
    private var state = 0 // where the path stood at the last settled row

    // Of the sounds settled so far that a reciter must say, how many the model heard clearly where they were put.
    private var required = 0
    private var clear = 0

    /**
     * How well the placing so far fits what was heard, 0..1. On a clear recording it stays
     * near 0.9. When it sinks, the placing has lost its way and isn't worth showing.
     */
    val clarity: Float get() = if (required == 0) 1f else clear.toFloat() / required

    /** More of what the model heard: the frame each row came from, and the rows. */
    fun add(newFrames: IntArray, newRows: FloatArray) {
        val total = count + newFrames.size
        if (total > frames.size) {
            val room = maxOf(total, frames.size * 2)
            frames = frames.copyOf(room)
            rows = rows.copyOf(room * width)
        }
        System.arraycopy(newFrames, 0, frames, count, newFrames.size)
        System.arraycopy(newRows, 0, rows, count * width, newFrames.size * width)
        count = total
    }

    /** Places what has arrived since last time, and returns the timings of the words settled so far. */
    fun advance(): Timings {
        while (count - settled > MARGIN) {
            val until = minOf(count, settled + WINDOW)
            val aligner = Aligner(rows, recital.phones, frames = until)
            val path = aligner.follow(settled, until, state)
            val keep = path.size - MARGIN
            for (t in 0 until keep) {
                val s = path[t]
                if (s % 2 == 1 && starts[s / 2] < 0) {
                    starts[s / 2] = settled + t
                    if (!recital.phones[s / 2].optional) {
                        required++
                        if (aligner.score(s / 2, settled + t) > CLEAR) clear++
                    }
                }
            }
            settled += keep
            state = path[keep - 1]
        }
        // Standing on sound k, or on the pause after it, k + 1 sounds have been placed.
        val sounds = (state + 1) / 2
        val heardMs = if (settled == 0) 0 else frames[settled - 1] * SoundColumns.FRAME_MS
        // A placing that has lost its way would light the wrong words: better to light none
        // until the whole recording has been heard and placed properly.
        if (required >= ENOUGH_TO_JUDGE && clarity < TRUSTED) return Timings(emptyList(), 1f, heardMs = 0)
        return recital.timingsSoFar(starts, frames, sounds, heardMs)
    }

    private companion object {
        /** Rows placed in one go: about a minute of recitation. */
        const val WINDOW = 700

        /** Rows left unsettled at the end of what has been heard: about eight seconds. */
        const val MARGIN = 100

        /** A sound scoring above this (about one chance in ten) counts as clearly heard. */
        const val CLEAR = -2.3f

        /**
         * Below this share of clearly heard sounds, the placing isn't shown. Clear recordings
         * stay above 0.85; a muffled one on which this placing drifted sat at 0.64.
         */
        const val TRUSTED = 0.72f

        /** How many sounds must be settled before the placing is judged. */
        const val ENOUGH_TO_JUDGE = 150
    }
}
