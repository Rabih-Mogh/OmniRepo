package app.recitation.speech

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import app.recitation.core.Heard
import app.recitation.core.Hearing
import app.recitation.core.Span
import app.recitation.core.SpeechModel
import java.io.File
import java.nio.FloatBuffer

/**
 * The speech model on the phone: wav2vec2 (CTC over IPA symbols), run with ONNX Runtime.
 * It writes down the sounds actually made rather than guessing words, which is what
 * lining a known text up with a recording needs. [tuning] says how the engine is run.
 */
class Listener(model: ModelStore.Opened, vocab: List<String>, tuning: Tuning = Tuning.USUAL) : SpeechModel, AutoCloseable {

    private val env = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(tuning.threads)
        // Threads with nothing to do wait rather than spin, when that was found to be no slower.
        if (!tuning.spin) addConfigEntry("session.intra_op.allow_spinning", "0")
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
    }

    // Accent Coach's copy of the model is read straight out of its file, mapped into memory;
    // this app's own copy is opened by its path. The mapping is held for as long as the session is.
    private val borrowed = model.bytes
    private val session: OrtSession = if (borrowed != null) env.createSession(borrowed, options) else env.createSession(model.file!!.path, options)
    private val inputName = session.inputNames.first()
    private val hearing = Hearing(this, vocab)

    override fun run(samples: FloatArray): FloatArray =
        OnnxTensor.createTensor(env, FloatBuffer.wrap(samples), longArrayOf(1, samples.size.toLong())).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                val scores = (result[0] as OnnxTensor).floatBuffer // [1, frames, symbols]; a copy, so asked for once
                FloatArray(scores.remaining()).also { scores.get(it) }
            }
        }

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
