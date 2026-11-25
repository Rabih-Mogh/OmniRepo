package app.safeguard.data

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
import app.safeguard.core.Book
import app.safeguard.core.Change
import app.safeguard.core.Presets
import app.safeguard.core.Rules
import app.safeguard.core.Stamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.abs

/**
 * What the app remembers: the rules, the changes waiting to loosen them, and how long each
 * limit has been used today. Kept in the app's own preferences file; the screens and the
 * watching service both read it from here. Used from the main thread only.
 */
object Store {

    /** Today's use of each limit, in milliseconds, by the limit's id. */
    data class Used(val day: String, val millis: Map<String, Long>)

    private val _book = MutableStateFlow(Book())
    val book: StateFlow<Book> = _book.asStateFlow()

    private val _used = MutableStateFlow(Used("", emptyMap()))
    val used: StateFlow<Used> = _used.asStateFlow()

    private var prefs: SharedPreferences? = null

    // The moment last looked at, and the running time at which the waits were last written down.
    private var last: Stamp? = null
    private var written = 0L

    /** Reads what was kept, the first time it is called. */
    fun open(context: Context) {
        if (prefs == null) load(context)
    }

    internal fun load(context: Context) {
        val p = context.applicationContext.getSharedPreferences("safeguard", Context.MODE_PRIVATE)
        prefs = p
        // A first run starts with the three limits most people come for.
        val rules = p.getString("rules", null)?.let { Codec.rules(JSONObject(it)) } ?: Rules(limits = Presets.limits)
        val pending = p.getString("pending", null)?.let { Codec.pending(JSONArray(it)) }.orEmpty()
        _book.value = Book(rules, pending)
        _used.value = p.getString("used", null)?.let { text ->
            val json = JSONObject(text)
            val millis = json.getJSONObject("millis")
            Used(json.getString("day"), millis.keys().asSequence().associateWith(millis::getLong))
        } ?: Used("", emptyMap())
        last = null
        written = 0
    }

    /** Asks for [change]. Locked, one that loosens the rules joins the waiting changes instead of happening. */
    fun ask(context: Context, change: Change) {
        open(context)
        _book.value = _book.value.at(now(context)).ask(change, now(context))
        write()
    }

    /** Withdraws a waiting [change]. */
    fun cancel(context: Context, change: Change) {
        open(context)
        _book.value = _book.value.cancel(change)
        write()
    }

    /** Moves the waiting changes on to this moment, and applies the ones whose wait is over. */
    fun tick(context: Context) {
        open(context)
        val before = _book.value
        if (before.pending.isEmpty()) return
        val now = now(context)
        val after = before.at(now)
        _book.value = after
        // The clock was set by hand if it moved differently from the phone's running time.
        val jumped = last?.let { it.boot == now.boot && abs((now.wall - it.wall) - (now.elapsed - it.elapsed)) > 5_000 } ?: false
        last = now
        // Written down when something changed, right after the clock is set (or a restart
        // would count the jump as time waited), and otherwise twice a minute.
        val changed = after.rules != before.rules || after.pending.size != before.pending.size
        if (changed || jumped || now.elapsed - written > 30_000 || now.elapsed < written) write()
    }

    fun usedSeconds(limit: String): Long = (today().millis[limit] ?: 0) / 1000

    /** Adds [millis] of use to [limit]'s day. */
    fun spend(context: Context, limit: String, millis: Long) {
        open(context)
        if (millis <= 0) return
        val used = today()
        _used.value = used.copy(millis = used.millis + (limit to (used.millis[limit] ?: 0) + millis))
        prefs?.edit()?.putString("used", JSONObject().put("day", _used.value.day).put("millis", JSONObject(_used.value.millis)).toString())?.apply()
    }

    // A new day starts from nothing.
    private fun today(): Used {
        val day = LocalDate.now().toString()
        return _used.value.takeIf { it.day == day } ?: Used(day, emptyMap()).also { _used.value = it }
    }

    private fun write() {
        val book = _book.value
        prefs?.edit()
            ?.putString("rules", Codec.write(book.rules).toString())
            ?.putString("pending", Codec.write(book.pending).toString())
            ?.apply()
        written = SystemClock.elapsedRealtime()
    }

    private fun now(context: Context) = Stamp(
        wall = System.currentTimeMillis(),
        elapsed = SystemClock.elapsedRealtime(),
        boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0),
    )
}
