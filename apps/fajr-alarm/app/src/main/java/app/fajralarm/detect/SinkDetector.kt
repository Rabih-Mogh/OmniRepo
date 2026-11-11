package app.fajralarm.detect

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import app.fajralarm.core.Box
import app.fajralarm.core.Detection
import app.fajralarm.core.FrameResult
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Runs the bundled EfficientDet-Lite0 model (trained on the 80 everyday COCO objects,
 * "sink" among them) over camera frames, entirely on the phone. The model must be the
 * TensorFlow Lite build with box decoding and non-max suppression built in (outputs:
 * boxes, classes, scores, count); MediaPipe's build leaves that out and won't load.
 *
 * Not safe to share between threads except for [close], which waits for a running
 * [detect] to finish.
 */
class SinkDetector private constructor(
    private val interpreter: Interpreter,
    private val labels: List<String>,
) : Closeable {

    private val inputH: Int
    private val inputW: Int
    private val input: ByteBuffer
    private val pixels: IntArray
    private val maxDetections: Int
    private var closed = false

    init {
        // Output shapes are only known once tensors are allocated.
        interpreter.allocateTensors()
        require(interpreter.outputTensorCount == 4) {
            "Expected a detector with built-in box decoding (4 outputs), got ${interpreter.outputTensorCount}"
        }
        val shape = interpreter.getInputTensor(0).shape() // [1, h, w, 3], uint8
        inputH = shape[1]
        inputW = shape[2]
        input = ByteBuffer.allocateDirect(inputH * inputW * 3).order(ByteOrder.nativeOrder())
        pixels = IntArray(inputH * inputW)
        maxDetections = interpreter.getOutputTensor(0).shape()[1]
    }

    /** Detects objects in [frame]. Returns null once the detector is closed. */
    @Synchronized
    fun detect(frame: Bitmap): FrameResult? {
        if (closed) return null
        val brightness = fillInput(frame)

        // Output order from the model's metadata: boxes, classes, scores, count.
        val boxes = Array(1) { Array(maxDetections) { FloatArray(4) } }
        val classes = Array(1) { FloatArray(maxDetections) }
        val scores = Array(1) { FloatArray(maxDetections) }
        val count = FloatArray(1)
        interpreter.runForMultipleInputsOutputs(arrayOf(input), mapOf(0 to boxes, 1 to classes, 2 to scores, 3 to count))

        val n = count[0].toInt().coerceIn(0, maxDetections)
        val detections = (0 until n).mapNotNull { i ->
            val label = labels.getOrNull(classes[0][i].toInt()) ?: return@mapNotNull null
            val (top, left, bottom, right) = boxes[0][i].toList()
            Detection(label, scores[0][i], Box(left, top, right, bottom))
        }
        return FrameResult(SystemClock.elapsedRealtime(), brightness, detections)
    }

    /** Scales [frame] into the model's input buffer and returns its mean luma. */
    private fun fillInput(frame: Bitmap): Float {
        val scaled = Bitmap.createScaledBitmap(frame, inputW, inputH, true)
        scaled.getPixels(pixels, 0, inputW, 0, 0, inputW, inputH)
        input.rewind()
        var luma = 0L
        for (c in pixels) {
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            input.put(r.toByte()).put(g.toByte()).put(b.toByte())
            luma += (r * 299 + g * 587 + b * 114) / 1000
        }
        input.rewind()
        return luma.toFloat() / pixels.size
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        interpreter.close()
    }

    companion object {
        private const val MODEL = "sink_detector.tflite"
        private const val LABELS = "sink_detector_labels.txt"

        /** Loads the model from the APK's assets. Throws if the model can't be loaded. */
        fun create(context: Context): SinkDetector {
            val model = context.assets.openFd(MODEL).use { fd ->
                FileInputStream(fd.fileDescriptor).channel.use {
                    it.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }
            val labels = context.assets.open(LABELS).bufferedReader().use { it.readLines() }
            return SinkDetector(Interpreter(model, Interpreter.Options().setNumThreads(2)), labels)
        }
    }
}
