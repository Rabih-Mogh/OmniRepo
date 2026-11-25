package app.recitation.speech

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import app.recitation.core.Heard
import app.recitation.core.Hearing
import app.recitation.core.Span
import app.recitation.core.SpeechModel
import java.io.File

/**
 * The speech model listening to a recording, run with ONNX Runtime: the same small model
 * that checks a reader's own recitation (see [TajweedStore]). It writes down the sounds
 * actually made, in the Quran phonetic script, rather than guessing words, which is what
 * lining a known text up with a recording needs. [tuning] says how the engine is run.
 */
class Listener(model: File, tuning: Tuning = Tuning.USUAL) : SpeechModel, AutoCloseable {

    private val env = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(tuning.threads)
        // Threads with nothing to do wait rather than spin, when that was found to be no slower.
        if (!tuning.spin) addConfigEntry("session.intra_op.allow_spinning", "0")
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
    }
    private val session: OrtSession = env.createSession(model.path, options)
    private val hearing = Hearing(this)

    /** The model's scores for every 20 ms of [samples], from their start: a stretch of a recording as it is, with nothing put before or after it. */
    override fun run(samples: FloatArray): FloatArray = session.scores(env, samples)

    /** Listens to a downloaded recording; see [Hearing.listen]. */
    fun listen(
        audio: File,
        notes: File,
        stillWanted: () -> Boolean,
        onPiece: (piece: Int, frames: IntArray, rows: FloatArray) -> Unit,
        onProgress: (Float) -> Unit,
        fromMs: Int = 0,
        untilMs: Int = Int.MAX_VALUE,
        next: (done: Set<Int>) -> Int? = { null },
    ): Heard? = AudioDecoder(audio).use { whole ->
        // Only a stretch of it, when some verses are being listened to again by themselves.
        val source = if (fromMs == 0 && untilMs == Int.MAX_VALUE) whole else Span(whole, fromMs.toLong() * hearing.samplesPerMs, untilMs.toLong() * hearing.samplesPerMs)
        hearing.listen(source, notes, stillWanted, onPiece, onProgress, next)
    }

    /** What an earlier, unfinished listening left in [notes]; see [Hearing.resume]. */
    fun resume(notes: File): Map<Int, Heard> = hearing.resume(notes)

    override fun close() {
        session.close()
        options.close()
    }
}
