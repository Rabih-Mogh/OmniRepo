package app.accentcoach.speech

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import app.accentcoach.core.CtcDecoder
import app.accentcoach.core.HeardPhoneme
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.sqrt

/**
 * Hears phonemes in 16 kHz mono audio with wav2vec2 (CTC over espeak IPA symbols).
 * It writes down the sounds actually made, including ones English doesn't use,
 * rather than guessing words, so an accent shows up as different symbols.
 */
class PhonemeRecognizer(modelFile: File, vocab: List<String>) : AutoCloseable {

    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession = env.createSession(
        modelFile.path,
        OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        },
    )
    private val inputName = session.inputNames.first()
    private val decoder = CtcDecoder(vocab)

    /** Phonemes heard in [audio] (samples in -1..1 at 16 kHz); empty for silence. */
    @Synchronized
    fun recognize(audio: FloatArray): List<HeardPhoneme> {
        if (audio.size < MIN_SAMPLES) return emptyList()
        val input = normalized(audio)
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, input.size.toLong())).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                val out = result[0] as OnnxTensor
                val shape = out.info.shape // [1, frames, vocab]
                val logits = FloatArray(out.floatBuffer.remaining()).also { out.floatBuffer.get(it) }
                return decoder.decode(logits, shape[1].toInt())
            }
        }
    }

    /** Zero mean, unit variance, as the model was trained on. */
    private fun normalized(audio: FloatArray): FloatArray {
        val mean = audio.average().toFloat()
        var v = 0.0
        for (s in audio) v += (s - mean) * (s - mean)
        val std = sqrt(v / audio.size + 1e-7).toFloat()
        return FloatArray(audio.size) { (audio[it] - mean) / std }
    }

    @Synchronized
    override fun close() {
        session.close()
    }

    private companion object {
        /** A quarter of a second: anything shorter can't hold a phoneme. */
        const val MIN_SAMPLES = 4_000
    }
}
