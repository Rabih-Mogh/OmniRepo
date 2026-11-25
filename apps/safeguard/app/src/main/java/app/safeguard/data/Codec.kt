package app.safeguard.data

import app.safeguard.core.Change
import app.safeguard.core.Hours
import app.safeguard.core.Limit
import app.safeguard.core.Listed
import app.safeguard.core.Pending
import app.safeguard.core.Rules
import app.safeguard.core.Stamp
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek

/** The rules and the waiting changes as JSON, which is how they are kept on the phone. */
internal object Codec {

    fun write(rules: Rules): JSONObject = JSONObject()
        .put("limits", JSONArray(rules.limits.map(::write)))
        .put("sites", JSONArray(rules.sites))
        .put("keywords", JSONArray(rules.keywords))
        .put("apps", JSONArray(rules.apps))
        .put("wait", rules.waitHours)
        .put("locked", rules.locked)

    fun rules(json: JSONObject): Rules = Rules(
        limits = json.getJSONArray("limits").objects().map(::limit),
        sites = json.getJSONArray("sites").strings(),
        keywords = json.getJSONArray("keywords").strings(),
        apps = json.getJSONArray("apps").strings(),
        waitHours = json.getInt("wait"),
        locked = json.getBoolean("locked"),
    )

    private fun write(limit: Limit): JSONObject = JSONObject()
        .put("id", limit.id)
        .put("name", limit.name)
        .put("apps", JSONArray(limit.apps))
        .put("sites", JSONArray(limit.sites))
        .putOpt("minutes", limit.minutesPerDay)
        .putOpt(
            "hours",
            limit.hours?.let { JSONObject().put("from", it.from).put("to", it.to).put("days", JSONArray(it.days.map(DayOfWeek::getValue))) },
        )

    private fun limit(json: JSONObject): Limit = Limit(
        id = json.getString("id"),
        name = json.getString("name"),
        apps = json.getJSONArray("apps").strings(),
        sites = json.getJSONArray("sites").strings(),
        minutesPerDay = if (json.has("minutes")) json.getInt("minutes") else null,
        hours = json.optJSONObject("hours")?.let { h ->
            val days = h.getJSONArray("days")
            Hours(h.getInt("from"), h.getInt("to"), (0 until days.length()).map { DayOfWeek.of(days.getInt(it)) }.toSet())
        },
    )

    fun write(pending: List<Pending>): JSONArray = JSONArray(
        pending.map {
            JSONObject()
                .put("change", write(it.change))
                .put("left", it.left)
                .put("wall", it.seen.wall)
                .put("elapsed", it.seen.elapsed)
                .put("boot", it.seen.boot)
        },
    )

    fun pending(json: JSONArray): List<Pending> = json.objects().map {
        Pending(change(it.getJSONObject("change")), it.getLong("left"), Stamp(it.getLong("wall"), it.getLong("elapsed"), it.getInt("boot")))
    }

    private fun write(change: Change): JSONObject = when (change) {
        is Change.Add -> JSONObject().put("do", "add").put("list", change.list.name).put("value", change.value)
        is Change.Drop -> JSONObject().put("do", "drop").put("list", change.list.name).put("value", change.value)
        is Change.PutLimit -> JSONObject().put("do", "limit").put("limit", write(change.limit))
        is Change.DropLimit -> JSONObject().put("do", "unlimit").put("id", change.id)
        is Change.Wait -> JSONObject().put("do", "wait").put("hours", change.hours)
        Change.Lock -> JSONObject().put("do", "lock")
        Change.Unlock -> JSONObject().put("do", "unlock")
    }

    private fun change(json: JSONObject): Change = when (val what = json.getString("do")) {
        "add" -> Change.Add(Listed.valueOf(json.getString("list")), json.getString("value"))
        "drop" -> Change.Drop(Listed.valueOf(json.getString("list")), json.getString("value"))
        "limit" -> Change.PutLimit(limit(json.getJSONObject("limit")))
        "unlimit" -> Change.DropLimit(json.getString("id"))
        "wait" -> Change.Wait(json.getInt("hours"))
        "lock" -> Change.Lock
        "unlock" -> Change.Unlock
        else -> error("unknown change $what")
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)

    private fun JSONArray.strings(): Set<String> = (0 until length()).mapTo(LinkedHashSet(), ::getString)
}
