package app.accentcoach

import android.app.Application
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
import app.accentcoach.core.DrillItem
import app.accentcoach.core.DrillResult
import app.accentcoach.core.Drills
import app.accentcoach.core.Lesson
import app.accentcoach.core.MyWord
import app.accentcoach.core.MyWords
import app.accentcoach.core.Phonemes
import app.accentcoach.core.Practice
import app.accentcoach.core.Said
import app.accentcoach.core.Sentence
import app.accentcoach.core.Streak
import app.accentcoach.data.Dictionary
import app.accentcoach.data.Progress
import app.accentcoach.data.References
import app.accentcoach.data.SentenceBank
import app.accentcoach.data.Wordbook
import app.accentcoach.speech.ModelDownloadWorker
import app.accentcoach.speech.ModelStore
import app.accentcoach.speech.PhonemeRecognizer
import app.accentcoach.speech.Playback
import app.accentcoach.speech.Recorder
import app.accentcoach.speech.Wav
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

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

/** The parts of the app: reading sentences, the lessons on single sounds, and the learner's own words. */
enum class Page { SENTENCES, SOUNDS, WORDS }

/**
 * A lesson's drill under way: which of its items is [at] hand, and how each one went the
 * last time it was said. It is finished once [at] is past the last item.
 */
data class DrillRun(val lesson: Lesson, val at: Int = 0, val results: Map<Int, DrillResult> = emptyMap()) {
    val item: DrillItem? get() = lesson.items.getOrNull(at)
    val result: DrillResult? get() = results[at]
    val finished: Boolean get() = at >= lesson.items.size
    val native: Int get() = results.values.count { it.said == Said.NATIVE }
}

class PracticeViewModel(app: Application) : AndroidViewModel(app) {

    private val store = ModelStore(app)
    private val vocab = SentenceBank.vocab(app)
    val sentences: List<Sentence> = SentenceBank.sentences(app)
    val focuses: List<String> = sentences.map { it.focus }.distinct()
    val lessons: List<Lesson> = SentenceBank.lessons(app)
    private val progress = Progress(File(app.filesDir, "progress.json"))
    private val wordbook = Wordbook(File(app.filesDir, "words.json"))
    private val references = References(Dictionary { app.assets.open("dictionary.txt") }, ::hearFromVoice)

    var model by mutableStateOf<ModelState>(if (store.isReady) ModelState.Loading else ModelState.Missing)
        private set
    var focus by mutableStateOf<String?>(null)
        private set
    var index by mutableStateOf(0)
        private set
    var page by mutableStateOf(Page.SENTENCES)
        private set
    /** The lesson that is open on the Sounds page, if one is. */
    var lesson by mutableStateOf<Lesson?>(null)
        private set
    /** The open lesson's drill, once it has been started. */
    var drill by mutableStateOf<DrillRun?>(null)
        private set
    /** The text of the learner's own word that is open on the My words page, if one is. */
    private var opened by mutableStateOf<String?>(null)
    /** True while the native sounds of a word being added are worked out. */
    var adding by mutableStateOf(false)
        private set
    /** Why the last word couldn't be added. */
    var addError by mutableStateOf<String?>(null)
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
    private var analysis: Job? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    /** Words the voice is saying into a file, by utterance, each waiting to hear how it went. */
    private val voicing = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private var voiced = 0

    val visible: List<Sentence> get() = focus?.let { f -> sentences.filter { it.focus == f } } ?: sentences
    val sentence: Sentence get() = visible[index.coerceIn(0, visible.lastIndex)]
    val hasRecording: Boolean get() = lastRecording.isNotEmpty()
    val practice: Practice get() = progress.practice
    val streak: Streak get() = progress.streak
    val words: List<MyWord> get() = wordbook.words
    val word: MyWord? get() = opened?.let { text -> wordbook.words.firstOrNull { it.text == text } }

    /** What is to be said next: the drill's item when a drill is under way, the open word of the learner's own, or the sentence. */
    private val saying: Sentence
        get() {
            val run = drill
            val item = run?.item
            return if (run != null && item != null) Drills.sentence(run.lesson, item) else word?.sentence ?: sentence
        }

    init {
        if (store.isReady) loadModel() else watchDownload()
        tts = TextToSpeech(app) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            tts?.language = Locale.US
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                voicing.remove(utteranceId)?.complete(true)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                voicing.remove(utteranceId)?.complete(false)
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                voicing.remove(utteranceId)?.complete(false)
            }
        })
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

    fun show(p: Page) {
        page = p
        lesson = null
        drill = null
        opened = null
        addError = null
        reset()
    }

    /** Opens one of the learner's own words to practise. */
    fun open(w: MyWord) {
        opened = w.text
        reset()
    }

    /**
     * Adds what was [typed] to the learner's own words, with its native sounds: from the
     * dictionary where it has the word, heard from the phone's voice where it hasn't.
     * [onAdded] is called once it is in the list.
     */
    fun addWord(typed: String, onAdded: () -> Unit = {}) {
        if (adding) return
        val text = MyWords.clean(typed)
        if (text == null) {
            addError = "Type a word or a short phrase: letters only, up to ${MyWords.MAX_WORDS} words."
            return
        }
        adding = true
        addError = null
        viewModelScope.launch {
            try {
                wordbook.add(withContext(Dispatchers.IO) { references.of(text) })
                onAdded()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                addError = e.message ?: "\"$text\" couldn't be added."
            } finally {
                adding = false
            }
        }
    }

    fun removeWord(w: MyWord) {
        wordbook.remove(w.text)
    }

    /**
     * The native sounds of a [word] the dictionary doesn't have: the phone's voice says
     * it into a file and the recogniser writes down what it hears there.
     */
    private suspend fun hearFromVoice(word: String): List<String> {
        val rec = recognizer ?: error("\"$word\" isn't in the dictionary, and the speech model that would work it out hasn't started.")
        val t = tts
        if (t == null || !ttsReady) {
            error("\"$word\" isn't in the dictionary. Working it out needs an American English voice: add one in Settings → Text-to-speech.")
        }
        val id = "voiced-${voiced++}"
        val file = File(getApplication<Application>().cacheDir, "$id.wav")
        val done = CompletableDeferred<Boolean>()
        voicing[id] = done
        try {
            t.setSpeechRate(0.95f)
            val said = t.synthesizeToFile(word, null, file, id) == TextToSpeech.SUCCESS &&
                withTimeoutOrNull(VOICE_TIMEOUT_MS) { done.await() } == true
            val audio = if (said && file.exists()) Wav.read(file.readBytes()) else null
            val heard = audio?.let { Phonemes.normalize(rec.recognize(trimSilence(it)).map { p -> p.symbol }) }.orEmpty()
            if (heard.isEmpty()) error("\"$word\" isn't in the dictionary, and the phone's voice couldn't be heard saying it.")
            return heard
        } finally {
            voicing.remove(id)
            file.delete()
        }
    }

    fun open(l: Lesson) {
        lesson = l
        drill = null
        reset()
    }

    /** Starts the open lesson's drill, from its first item. */
    fun startDrill() {
        drill = DrillRun(lesson ?: return)
        reset()
    }

    /** Moves on to the drill's next item; past the last one the drill is over and its result is kept. */
    fun nextItem() {
        val run = drill ?: return
        if (run.finished) return
        val next = run.copy(at = run.at + 1)
        if (next.finished) progress.drilled(run.lesson.id, run.native)
        drill = next
        reset()
    }

    /** Steps back from a drill to its lesson, or from a lesson or a word to its list; false when there is nowhere to step back to. */
    fun back(): Boolean {
        when {
            drill != null -> drill = null
            lesson != null -> lesson = null
            opened != null -> opened = null
            else -> return false
        }
        reset()
        return true
    }

    private fun reset() {
        if (recorder.isRecording) recorder.stop()
        liveJob?.cancel()
        analysis?.cancel()
        Playback.stop()
        stage = Stage.Idle
        live = emptyList()
        lastRecording = FloatArray(0)
        error = null
    }

    /** Says [text], by default what is to be said next, with the phone's American English voice. */
    fun listen(slow: Boolean, text: String = saying.text) {
        val t = tts ?: return
        if (!ttsReady) {
            error = "No American English voice is installed. Add one in Settings → Text-to-speech."
            return
        }
        t.setSpeechRate(if (slow) 0.6f else 0.95f)
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "listen")
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
        val target = saying
        val run = drill
        val item = run?.item
        val mine = word
        // Cancelled when the sentence or the drill's item is left, so a late result never lands on another one.
        analysis = viewModelScope.launch {
            val heard = withContext(Dispatchers.Default) { rec.recognize(trimSilence(audio)) }
            live = heard.map { it.symbol }
            val assessment = when {
                run != null && item != null -> {
                    val result = Drills.judge(run.lesson, item, live)
                    drill = run.copy(results = run.results + (run.at to result))
                    result.assessment
                }
                mine != null -> Assessor.assess(target, live, mirrored = mine.heard).also {
                    if (it.heard.isNotEmpty()) wordbook.scored(mine.text, it.score)
                }
                else -> Assessor.assess(target, live)
            }
            stage = Stage.Done(assessment)
            // A recording with nothing in it isn't practice.
            if (assessment.heard.isNotEmpty()) progress.recorded()
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

        /** How long the phone's voice gets to say a word into a file. */
        const val VOICE_TIMEOUT_MS = 15_000L

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
