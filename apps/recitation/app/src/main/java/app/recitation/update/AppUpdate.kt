package app.recitation.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.SystemClock
import androidx.core.content.IntentCompat
import androidx.core.content.pm.PackageInfoCompat
import app.recitation.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * Updates the app from its GitHub repo. Every published build leaves a small
 * `version.json` beside its APK (see `tools/publish-apk.sh`). If that names a higher
 * version than the one installed, the APK is downloaded, checked against its SHA-256
 * and handed to Android to install. Android only accepts it if it is signed with the
 * same key as the installed app.
 *
 * The download runs under [UpdateService], so it carries on with the screen off or the
 * app left, and shows its progress in a notification.
 *
 * The download stays in the cache folder until it is installed. An attempt that stops
 * halfway (no signal, the app closed, the install prompt dismissed) carries on from the
 * bytes already on the phone. Leftovers are deleted as soon as the update is in, and
 * each time the app looks for one.
 *
 * Android says nothing when its install prompt is left without an answer. So when the
 * app's screen is back in front and nothing has been heard, the download goes back on
 * offer instead of the app waiting for good.
 */
object AppUpdate {

    private const val BASE = "https://raw.githubusercontent.com/Rabih-Mogh/OmniRepo/main/apps/recitation/apk/"

    // How long Android gets to act on a yes before its silence counts as no answer.
    private const val UNANSWERED_MS = 4_000L

    // Coming back to the app looks for a newer build again, but not more often than this.
    private const val RECHECK_MS = 10 * 60_000L

    data class Release(val versionCode: Long, val versionName: String, val apk: String, val size: Long, val sha256: String)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val release: Release) : State
        data class Downloading(val progress: Float) : State
        data object Installing : State

        /** Downloaded while the app was out of sight. Installing waits for the user to come back and say so. */
        data class Ready(val release: Release) : State

        /** [release] is set when the update itself failed and can be retried. */
        data class Failed(val message: String, val release: Release?) : State
    }

    /** The server answered the download with something other than the file. */
    internal class HttpError(val code: Int) : IOException("HTTP $code")

    /** The downloaded file isn't the published one. */
    internal class Mismatch : IOException("checksum mismatch")

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    // False once the user closes the pop-up. Asking for a check, an update or a retry opens it again,
    // and so does a newer build.
    private val _prompt = MutableStateFlow(true)
    val prompt: StateFlow<Boolean> = _prompt.asStateFlow()

    // True from the tap on Update until Android has the file or the attempt has failed.
    // UpdateService keeps the app alive for that long.
    private val _working = MutableStateFlow(false)
    internal val working: StateFlow<Boolean> = _working.asStateFlow()

    // Install sessions the app gave up itself. Android reports each as a cancelled update, which is no news.
    private val abandoned = ConcurrentHashMap.newKeySet<Int>()

    // Outlives the screen, so leaving the app doesn't cancel a download.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // The build being fetched or installed. The tests set it too.
    @Volatile
    internal var installing: Release? = null

    // The install session Android is asking the user about, and the wait for its answer.
    @Volatile
    private var asked: Int? = null
    private var unanswered: Job? = null

    // The build the pop-up was last closed on. A different one opens it again.
    private var declined: Release? = null

    // When a check last got an answer, on the clock that counts from the phone's start.
    private var checkedAt = 0L

    // Held while the downloads folder is written to or tidied.
    private val files = Mutex()

    fun installedVersionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"

    private fun installedVersionCode(context: Context): Long =
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))

    private fun busy(): Boolean = _state.value is State.Downloading || _state.value == State.Installing

    /** Closes the pop-up for the build on offer. A download that is running carries on. */
    fun dismiss() {
        declined = when (val now = _state.value) {
            is State.Available -> now.release
            is State.Ready -> now.release
            is State.Failed -> now.release
            else -> installing
        }
        _prompt.value = false
    }

    /** Looks for a newer build. A [quiet] check says nothing unless it finds one. */
    suspend fun check(context: Context, quiet: Boolean = true) {
        if (busy()) return
        val ready = _state.value as? State.Ready
        if (!quiet) {
            _prompt.value = true
            _state.value = State.Checking
        }
        val app = context.applicationContext
        var latest: Release? = null
        val found = try {
            val release = withContext(Dispatchers.IO) { fetchRelease() }
            latest = release
            checkedAt = SystemClock.elapsedRealtime()
            when {
                release.versionCode > installedVersionCode(app) -> State.Available(release)
                quiet -> State.Idle
                else -> State.UpToDate
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (quiet) State.Idle else State.Failed(app.getString(R.string.update_check_failed), null)
        }
        // An update may have started while this was asking.
        if (busy()) return
        // A download that is waiting to be installed stays on offer, unless a newer build has replaced it.
        offer(if (ready != null && (latest == null || latest == ready.release)) ready else found)
        val keep = latest
        withContext(Dispatchers.IO) { cleanUp(app, keep) }
    }

    /** The check made each time the app's screen comes back: quiet, and skipped if the last one was moments ago. */
    suspend fun checkAgain(context: Context) {
        if (checkedAt != 0L && SystemClock.elapsedRealtime() - checkedAt < RECHECK_MS) return
        check(context)
    }

    /** Puts what a check found on offer. A build other than the one the pop-up was closed on opens it again. */
    internal fun offer(next: State) {
        val release = (next as? State.Available)?.release
        if (release != null && release != declined) _prompt.value = true
        _state.value = next
    }

    fun install(context: Context, release: Release) {
        if (busy()) return
        val app = context.applicationContext
        installing = release
        forget()
        _prompt.value = true
        _state.value = State.Downloading(0f)
        _working.value = true
        UpdateNotice.clear(app)
        // Carries the download through the screen turning off or the app being left.
        UpdateService.start(app)
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    files.withLock {
                        val dir = folder(app)
                        tidy(dir, installedVersionCode(app), keep = release)
                        val apk = download(BASE + release.apk, release, dir) { _state.value = State.Downloading(it) }
                        _state.value = State.Installing
                        commit(app, apk)
                    }
                }
            } catch (e: Exception) {
                val message = when (e) {
                    is HttpError -> app.getString(R.string.update_download_failed, e.code)
                    // Usually a newer build was published between the check and the download.
                    is Mismatch -> app.getString(R.string.update_mismatch)
                    is IOException -> app.getString(R.string.update_interrupted)
                    else -> e.message ?: app.getString(R.string.update_failed)
                }
                _prompt.value = true
                _state.value = State.Failed(message, release)
                // Nobody is looking at the pop-up, so say it where it will be seen.
                if (!UpdateNotice.appOnScreen()) UpdateNotice.result(app, message)
            } finally {
                _working.value = false
            }
        }
    }

    private fun fetchRelease(): Release {
        val conn = open(BASE + "version.json")
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) error("HTTP ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            return Release(
                versionCode = json.getLong("versionCode"),
                versionName = json.getString("versionName"),
                apk = json.getString("apk"),
                size = json.getLong("size"),
                sha256 = json.getString("sha256"),
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun folder(context: Context) = File(context.cacheDir, "updates")

    // Downloads are named after the build they hold: "24-<sha256>.part" while fetching, ".apk" once whole.
    private fun name(release: Release) = "${release.versionCode}-${release.sha256}"

    /**
     * Fetches [release] into [dir] and returns the finished file. Bytes left by an earlier
     * attempt are kept and only the rest is asked for; a finished file isn't fetched again.
     */
    internal suspend fun download(url: String, release: Release, dir: File, onProgress: (Float) -> Unit = {}): File {
        dir.mkdirs()
        val apk = File(dir, name(release) + ".apk")
        if (apk.length() == release.size && sha256(apk) == release.sha256) return apk
        apk.delete()

        val part = File(dir, name(release) + ".part")
        if (part.length() > release.size) part.delete()
        var have = part.length()
        onProgress((have.toFloat() / release.size).coerceIn(0f, 1f))
        if (have < release.size) {
            val conn = open(url)
            try {
                // Unzipped, so byte positions mean the same here and on the server.
                conn.setRequestProperty("Accept-Encoding", "identity")
                if (have > 0) conn.setRequestProperty("Range", "bytes=$have-")
                when (val code = conn.responseCode) {
                    HttpURLConnection.HTTP_PARTIAL -> Unit
                    // The server sent the whole file again; start over.
                    HttpURLConnection.HTTP_OK -> have = 0
                    // The published file is shorter than what's here, so it's a different build.
                    416 -> {
                        part.delete()
                        throw Mismatch()
                    }
                    else -> throw HttpError(code)
                }
                conn.inputStream.use { input ->
                    FileOutputStream(part, have > 0).use { out ->
                        val buf = ByteArray(1 shl 16)
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            have += n
                            onProgress((have.toFloat() / release.size).coerceIn(0f, 1f))
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
        }
        if (part.length() < release.size) throw IOException("The download stopped early.")
        if (part.length() > release.size || sha256(part) != release.sha256) {
            part.delete()
            throw Mismatch()
        }
        if (!part.renameTo(apk)) throw IOException("Couldn't keep the download.")
        return apk
    }

    /** Deletes downloads that will never be installed: builds the app already has, and any build but [keep]. */
    internal fun tidy(dir: File, installed: Long, keep: Release? = null) {
        for (file in dir.listFiles().orEmpty()) {
            val version = file.name.substringBefore('-').toLongOrNull()
            if (version == null || version <= installed || (keep != null && file.nameWithoutExtension != name(keep))) {
                file.delete()
            }
        }
    }

    /** Frees what no update needs any more: old downloads, and install sessions Android still holds for the app. */
    internal fun cleanUp(context: Context, keep: Release? = null) {
        if (!files.tryLock()) return // an update is using them
        try {
            tidy(folder(context), installedVersionCode(context), keep)
            val installer = context.packageManager.packageInstaller
            for (session in installer.mySessions) abandon(installer, session.sessionId)
        } catch (_: Exception) {
            // Nothing depends on it; the next check tries again.
        } finally {
            files.unlock()
        }
    }

    /** Hands the downloaded APK to Android. The file stays until the install has gone through. */
    private fun commit(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        // An earlier request that was never answered gives way to this one.
        for (old in installer.mySessions) abandon(installer, old.sessionId)
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            // Android 12+: after the first confirmed update, later ones install without asking.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        val session = installer.openSession(id)
        try {
            session.openWrite("app.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out, 1 shl 16) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val status = PendingIntent.getBroadcast(context, id, Intent(context, UpdateReceiver::class.java), flags)
            session.commit(status.intentSender)
        } catch (e: Throwable) {
            session.abandon()
            throw e
        } finally {
            session.close()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            useCaches = false
        }

    internal fun installFailed(message: String) {
        forget()
        _prompt.value = true
        _state.value = State.Failed(message, installing)
    }

    /**
     * Android wants the user's yes, but the app isn't on screen to ask for it. The download
     * is kept, a notification says it is ready, and the pop-up offers it when the app is opened.
     */
    internal fun waitForUser(context: Context, sessionId: Int) {
        forget()
        abandon(context.packageManager.packageInstaller, sessionId)
        _prompt.value = true
        _state.value = installing?.let { State.Ready(it) } ?: State.Idle
        UpdateNotice.result(context, context.getString(R.string.update_notice_ready))
    }

    /** Android's question about [sessionId] is about to cover the app's screen. */
    internal fun asking(context: Context, sessionId: Int) {
        asked = sessionId
        _state.value = State.Installing
        resumed(context)
    }

    /**
     * The app's screen is in front. If Android's question about an update should be on top
     * of it and nothing is heard, the question was left without an answer, and Android never
     * says so. The download is kept and goes back on offer.
     */
    fun resumed(context: Context) {
        val session = asked ?: return
        val installer = context.applicationContext.packageManager.packageInstaller
        unanswered?.cancel()
        unanswered = scope.launch {
            var done = progress(installer, session)
            while (true) {
                // A yes takes Android a moment to act on, and then it closes the app to replace it.
                delay(UNANSWERED_MS)
                val now = progress(installer, session)
                if (now == null || done == null || now <= done) break
                // Android is working on it, so the answer was yes.
                done = now
            }
            if (asked != session) return@launch
            asked = null
            _prompt.value = true
            _state.value = installing?.let { State.Ready(it) } ?: State.Idle
        }
    }

    /** The app's screen is covered or left, so its silence about an install says nothing. */
    fun paused() {
        unanswered?.cancel()
    }

    // Android has answered, or is no longer being asked.
    private fun forget() {
        asked = null
        unanswered?.cancel()
    }

    private fun progress(installer: PackageInstaller, sessionId: Int): Float? = try {
        installer.getSessionInfo(sessionId)?.progress
    } catch (_: Exception) {
        null
    }

    private fun abandon(installer: PackageInstaller, sessionId: Int) {
        abandoned += sessionId
        try {
            installer.abandonSession(sessionId)
        } catch (_: Exception) {
            // Already gone.
        }
    }

    /** True, once, for an install session the app ended itself. */
    internal fun abandonedHere(sessionId: Int): Boolean = abandoned.remove(sessionId)
}

/** Hears back from Android about an update handed to it by [AppUpdate]. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // The new version has just replaced the old one, so its download has done its job.
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            AppUpdate.cleanUp(context)
            UpdateNotice.clear(context)
            return
        }
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            // Android wants a yes from the user: always for the first update, and every time before Android 12.
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                when {
                    confirm == null -> AppUpdate.installFailed(context.getString(R.string.update_prompt_failed))
                    // Android doesn't let an app open a screen while it is out of sight, so the question waits.
                    !UpdateNotice.appOnScreen() -> AppUpdate.waitForUser(context, sessionId)
                    else -> try {
                        AppUpdate.asking(context, sessionId)
                        context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {
                        AppUpdate.installFailed(context.getString(R.string.update_prompt_failed))
                    }
                }
            }
            // The app has been replaced and closed by now; there's nobody left to tell.
            PackageInstaller.STATUS_SUCCESS -> Unit
            // Not news when the app ended the install itself, to tidy up or to wait for the user.
            PackageInstaller.STATUS_FAILURE_ABORTED ->
                if (!AppUpdate.abandonedHere(sessionId)) AppUpdate.installFailed(context.getString(R.string.update_cancelled))
            else -> AppUpdate.installFailed(
                context.getString(
                    R.string.update_refused,
                    intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                        ?: context.getString(R.string.update_no_reason),
                ),
            )
        }
    }
}
