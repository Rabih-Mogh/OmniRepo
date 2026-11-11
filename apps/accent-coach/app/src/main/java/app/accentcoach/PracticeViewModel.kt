package app.accentcoach

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.accentcoach.core.Assessment
import app.accentcoach.core.Assessor
import app.accentcoach.core.Sentence
import app.accentcoach.data.SentenceBank
import app.accentcoach.speech.ModelDownloadWorker
import app.accentcoach.speech.ModelStore
import app.accentcoach.speech.PhonemeRecognizer
import app.accentcoach.speech.Playback
import app.accentcoach.speech.Recorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

sealed interface ModelState {
    data object Missing : ModelState
    /** [waiting] is true while the download waits for a connection to come back. */
    data class Downloading(val progress: Float, val waiting: Boolean = false) : ModelState
    data class Failed(val message: String) : ModelState
    data object Loading : ModelState
    data object Ready : ModelState
}

sealed interface Stage {
    data object Idle : Stage
    data object Recording : Stage
    data object Analysing : Stage
    data class Done(val assessment: Assessment) : Stage
}

class PracticeViewModel(app: Application) : AndroidViewModel(app) {

    private val store = ModelStore(app)
    private val vocab = SentenceBank.vocab(app)
    val sentences: List<Sentence> = SentenceBank.sentences(app)
    val focuses: List<String> = sentences.map { it.focus }.distinct()

    var model by mutableStateOf<ModelState>(if (store.isReady) ModelState.Loading else ModelState.Missing)
        private set
    var focus by mutableStateOf<String?>(null)
        private set
    var index by mutableStateOf(0)
        private set
    var stage by mutableStateOf<Stage>(Stage.Idle)
        private set
    /** Phonemes heard so far while recording, refreshed as you speak. */
    var live by mutableStateOf<List<String>>(emptyList())
        private set
    var level by mutableFloatStateOf(0f)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var recognizer: PhonemeRecognizer? = null
    private val recorder = Recorder()
    private var lastRecording = FloatArray(0)
    private var liveJob: Job? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    val visible: List<Sentence> get() = focus?.let { f -> sentences.filter { it.focus == f } } ?: sentences
    val sentence: Sentence get() = visible[index.coerceIn(0, visible.lastIndex)]
    val hasRecording: Boolean get() = lastRecording.isNotEmpty()

    init {
        if (store.isReady) loadModel() else watchDownload()
        tts = TextToSpeech(app) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            tts?.language = Locale.US
        }
    }

    /** Starts the background download; [watchDownload] follows it from here. */
    fun downloadModel() {
        model = ModelState.Downloading(store.partialProgress)
        ModelDownloadWorker.start(getApplication())
    }

    /** Mirrors the background download, which keeps going while the app is closed. */
    private fun watchDownload() {
        viewModelScope.launch {
            WorkManager.getInstance(getApplication())
                .getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.WORK_NAME)
                .collect { infos ->
                    val info = infos.lastOrNull() ?: return@collect
                    if (model == ModelState.Loading || model == ModelState.Ready) return@collect
                    model = when (info.state) {
                        WorkInfo.State.RUNNING -> ModelState.Downloading(
                            info.progress.getFloat(ModelDownloadWorker.PROGRESS, store.partialProgress),
                        )
                        // Queued again after a dropped connection, or waiting for one.
                        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
                            ModelState.Downloading(store.partialProgress, waiting = info.runAttemptCount > 0)
                        WorkInfo.State.SUCCEEDED -> {
                            if (store.isReady) loadModel()
                            return@collect
                        }
                        WorkInfo.State.FAILED -> ModelState.Failed(
                            info.outputData.getString(ModelDownloadWorker.ERROR) ?: "Download failed",
                        )
                        WorkInfo.State.CANCELLED -> ModelState.Missing
                    }
                }
        }
    }

    private fun loadModel() {
        model = ModelState.Loading
        viewModelScope.launch {
            try {
                recognizer = withContext(Dispatchers.Default) { PhonemeRecognizer(store.modelFile, vocab) }
                model = ModelState.Ready
            } catch (e: Exception) {
                model = ModelState.Failed("The speech model couldn't start: ${e.message}")
            }
        }
    }

    fun chooseFocus(f: String?) {
        focus = f
        index = 0
        reset()
    }

    fun next() {
        index = (index + 1) % visible.size
        reset()
    }

    fun previous() {
        index = (index - 1 + visible.size) % visible.size
        reset()
    }

    private fun reset() {
        if (recorder.isRecording) recorder.stop()
        liveJob?.cancel()
        Playback.stop()
        stage = Stage.Idle
        live = emptyList()
        lastRecording = FloatArray(0)
        error = null
    }

    /** Says the sentence with the phone's American English voice. */
    fun listen(slow: Boolean) {
        val t = tts ?: return
        if (!ttsReady) {
            error = "No American English voice is installed. Add one in Settings → Text-to-speech."
            return
        }
        t.setSpeechRate(if (slow) 0.6f else 0.95f)
        t.speak(sentence.text, TextToSpeech.QUEUE_FLUSH, null, sentence.id)
    }

    fun startRecording() {
        val rec = recognizer ?: return
        Playback.stop()
        tts?.stop()
        error = null
        live = emptyList()
        try {
            recorder.start(onLimit = { viewModelScope.launch { stopRecording() } })
        } catch (e: Exception) {
            error = e.message
            return
        }
        stage = Stage.Recording
        // Re-hear everything said so far whenever the last pass has finished, so the
        // symbols appear while you speak. Each pass covers the whole recording, so the
        // final result never depends on where a pass happened to cut.
        liveJob = viewModelScope.launch {
            launch {
                while (isActive && recorder.isRecording) {
                    level = recorder.level
                    delay(50)
                }
            }
            while (isActive && recorder.isRecording) {
                val audio = recorder.snapshot()
                if (audio.size > SAMPLE_STEP) {
                    val heard = withContext(Dispatchers.Default) { rec.recognize(audio) }
                    if (isActive && recorder.isRecording) live = heard.map { it.symbol }
                }
                delay(150)
            }
        }
    }

    fun stopRecording() {
        if (stage != Stage.Recording) return
        val audio = recorder.stop()
        liveJob?.cancel()
        level = 0f
        lastRecording = audio
        val rec = recognizer ?: return
        stage = Stage.Analysing
        val target = sentence
        viewModelScope.launch {
            val heard = withContext(Dispatchers.Default) { rec.recognize(trimSilence(audio)) }
            live = heard.map { it.symbol }
            stage = Stage.Done(Assessor.assess(target, live))
        }
    }

    fun playMine() = Playback.play(lastRecording)

    fun tryAgain() = reset()

    override fun onCleared() {
        if (recorder.isRecording) recorder.stop()
        Playback.stop()
        tts?.shutdown()
        recognizer?.close()
    }

    private companion object {
        const val SAMPLE_STEP = 8_000

        /** Cuts quiet lead-in and tail (below 2% of the peak), so silence isn't heard as sounds. */
        fun trimSilence(audio: FloatArray): FloatArray {
            if (audio.isEmpty()) return audio
            val peak = audio.maxOf { kotlin.math.abs(it) }
            val threshold = peak * 0.02f
            val first = audio.indexOfFirst { kotlin.math.abs(it) > threshold }.coerceAtLeast(0)
            val last = audio.indexOfLast { kotlin.math.abs(it) > threshold }.coerceAtLeast(first)
            val pad = 3_200 // 0.2 s either side keeps soft first and last sounds
            return audio.copyOfRange((first - pad).coerceAtLeast(0), (last + pad).coerceAtMost(audio.size))
        }
    }
}
