package app.accentcoach.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.IntentCompat
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Updates the app from its GitHub repo. Every published build leaves a small
 * `version.json` beside its APK (see `tools/publish-apk.sh`). If that names a higher
 * version than the one installed, the APK is downloaded, checked against its SHA-256
 * and handed to Android to install. Android only accepts it if it is signed with the
 * same key as the installed app.
 */
object AppUpdate {

    private const val BASE = "https://raw.githubusercontent.com/Rabih-Mogh/OmniRepo/main/apps/accent-coach/apk/"

    data class Release(val versionCode: Long, val versionName: String, val apk: String, val size: Long, val sha256: String)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val release: Release) : State
        data class Downloading(val progress: Float) : State
        data object Installing : State

        /** [release] is set when the update itself failed and can be retried. */
        data class Failed(val message: String, val release: Release?) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    // Outlives the screen, so leaving the app doesn't cancel a download.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var installing: Release? = null

    fun installedVersionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"

    private fun installedVersionCode(context: Context): Long =
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))

    /** Looks for a newer build. A [quiet] check says nothing unless it finds one. */
    suspend fun check(context: Context, quiet: Boolean = true) {
        val busy = _state.value
        if (busy is State.Downloading || busy == State.Installing) return
        if (!quiet) _state.value = State.Checking
        _state.value = try {
            val latest = withContext(Dispatchers.IO) { fetchRelease() }
            when {
                latest.versionCode > installedVersionCode(context) -> State.Available(latest)
                quiet -> State.Idle
                else -> State.UpToDate
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (quiet) State.Idle else State.Failed("Couldn't check for updates. Is the phone online?", null)
        }
    }

    fun install(context: Context, release: Release) {
        if (_state.value is State.Downloading || _state.value == State.Installing) return
        val app = context.applicationContext
        installing = release
        _state.value = State.Downloading(0f)
        scope.launch {
            try {
                withContext(Dispatchers.IO) { downloadAndCommit(app, release) }
            } catch (e: Exception) {
                _state.value = State.Failed(e.message ?: "The update failed.", release)
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

    /** Streams the APK into an install session, and only commits it if the checksum matches. */
    private suspend fun downloadAndCommit(context: Context, release: Release) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(release.size)
            // Android 12+: after the first confirmed update, later ones install without asking.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        val session = installer.openSession(id)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val conn = open(BASE + release.apk)
            try {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) error("Download failed (HTTP ${conn.responseCode}).")
                conn.inputStream.use { input ->
                    session.openWrite("app.apk", 0, release.size).use { out ->
                        val buf = ByteArray(1 shl 16)
                        var done = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            done += n
                            _state.value = State.Downloading((done.toFloat() / release.size).coerceIn(0f, 1f))
                        }
                        session.fsync(out)
                    }
                }
            } finally {
                conn.disconnect()
            }
            if (digest.digest().joinToString("") { "%02x".format(it) } != release.sha256) {
                // Usually a newer build was published between the check and the download.
                error("The download didn't match the published version. Try again in a few minutes.")
            }
            _state.value = State.Installing
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

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            useCaches = false
        }

    internal fun installFailed(message: String) {
        _state.value = State.Failed(message, installing)
    }
}

/** Hears back from Android about an update handed to it by [AppUpdate]. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            // Android wants a yes from the user: always for the first update, and every time before Android 12.
            PackageInstaller.STATUS_PENDING_USER_ACTION -> try {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                context.startActivity(confirm!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
                AppUpdate.installFailed("Android couldn't show its install prompt. Keep the app open and try again.")
            }
            // The app has been replaced and closed by now; there's nobody left to tell.
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> AppUpdate.installFailed("The update was cancelled.")
            else -> AppUpdate.installFailed(
                "Android refused the update: " +
                    (intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "no reason given"),
            )
        }
    }
}
