package app.recitation.speech

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import app.recitation.core.Heard
import app.recitation.core.Hearing
import app.recitation.core.SpeechModel
import java.io.File
import java.nio.FloatBuffer

/**
 * The speech model on the phone: wav2vec2 (CTC over IPA symbols), run with ONNX Runtime.
 * It writes down the sounds actually made rather than guessing words, which is what
 * lining a known text up with a recording needs.
 */
class Listener(modelFile: File, vocab: List<String>) : SpeechModel, AutoCloseable {

    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession = env.createSession(
        modelFile.path,
        OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        },
    )
    private val inputName = session.inputNames.first()
    private val hearing = Hearing(this, vocab)

    override fun run(samples: FloatArray): FloatArray =
        OnnxTensor.createTensor(env, FloatBuffer.wrap(samples), longArrayOf(1, samples.size.toLong())).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                val output = result[0] as OnnxTensor // [1, frames, symbols]
                FloatArray(output.floatBuffer.remaining()).also { output.floatBuffer.get(it) }
            }
        }

    /** Listens to a downloaded recording; see [Hearing.listen]. */
    fun listen(
        audio: File,
        notes: File,
        stillWanted: () -> Boolean,
        onPiece: (frames: IntArray, rows: FloatArray) -> Unit,
        onProgress: (Float) -> Unit,
    ): Heard? = AudioDecoder(audio).use { hearing.listen(it, notes, stillWanted, onPiece, onProgress) }

    /** What an earlier, unfinished listening left in [notes]; see [Hearing.resume]. */
    fun resume(notes: File): Heard = hearing.resume(notes)

    override fun close() {
        session.close()
    }
}
