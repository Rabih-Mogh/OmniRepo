package app.spark.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Asks one of Google's Gemini models a question, with the user's own key. The key goes in
 * a header, never in an address. Nothing is kept by this class.
 */
object Gemini {

    /** Why Google wouldn't answer. */
    enum class Why { KEY, QUOTA, MISSING, OTHER }

    /** Google answered, but not with what was asked for. [said] is its own explanation, when it gave one. */
    class Refused(val why: Why, val said: String) : IOException(said)

    /** Where the service is. Tests put a server of their own here. */
    internal var base = "https://generativelanguage.googleapis.com/v1beta/"

    /** The models with a free allowance, the ones that write best first. One that is gone or used up is passed over. */
    val PREFERRED = listOf("gemini-3.8-flash", "gemini-4-hydrogen", "gemini-3.6-flash", "gemini-3.5-flash-lite", "gemini-3.1-flash-lite", "gemini-2.5-flash-lite")

    // The version of the service these requests are written for.
    private const val REVISION = "2026-05-20"
    private const val LIMIT = 4_000_000

    // A model that thinks before it writes can take minutes over a long answer.
    private const val PATIENCE_MS = 240_000

    /**
     * The models to try with [key], best first: the preferred ones Google lists for it,
     * then, should none of those be listed, every one of them. Refuses a key Google doesn't accept.
     */
    fun models(key: String): List<String> {
        val reply = Net.fetch(base + "models?pageSize=1000", headers(key), LIMIT)
        if (reply.status !in 200..299) throw refusal(reply)
        val listed = try {
            JSONObject(reply.body).optJSONArray("models")?.let { all -> (0 until all.length()).mapNotNull { all.optJSONObject(it)?.optString("name")?.removePrefix("models/") } }
        } catch (_: Exception) {
            null
        }.orEmpty()
        return PREFERRED.filter(listed::contains).ifEmpty { PREFERRED }
    }

    /**
     * What [model] writes when asked [text], keeping to [system]. Asked the way Google now
     * recommends, and, should that way be turned down as a request it doesn't understand,
     * the older way, which it still serves.
     */
    fun ask(key: String, model: String, system: String, text: String): String {
        val now = Net.post(
            base + "interactions",
            JSONObject().put("model", model).put("system_instruction", system).put("input", text).put("store", false).toString(),
            headers(key) + ("Api-Revision" to REVISION),
            LIMIT,
            PATIENCE_MS,
        )
        if (now.status in 200..299) written(now.body)?.let { return it }
        if (now.status in 200..299 || now.status == 400 || now.status == 404) {
            val part = JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text)))
            val before = Net.post(
                base + "models/$model:generateContent",
                JSONObject()
                    .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                    .put("contents", JSONArray().put(part.put("role", "user")))
                    .toString(),
                headers(key),
                LIMIT,
                PATIENCE_MS,
            )
            if (before.status in 200..299) written(before.body)?.let { return it }
            // A key refused or an allowance used up is the same either way; say so.
            if (before.status in listOf(401, 403, 429)) throw refusal(before)
        }
        throw refusal(now)
    }

    private fun headers(key: String) = mapOf("x-goog-api-key" to key)

    /** The words of an answer, in either of the two shapes the service gives one. Null when there are none. */
    internal fun written(body: String): String? {
        val json = try {
            JSONObject(body)
        } catch (_: Exception) {
            return null
        }
        val out = StringBuilder()
        fun texts(items: JSONArray?) {
            for (i in 0 until (items?.length() ?: 0)) {
                val item = items?.optJSONObject(i) ?: continue
                // What a model thought on the way is not what it wrote.
                if (item.optBoolean("thought") || item.optString("type", "text") != "text") continue
                out.append(item.optString("text"))
            }
        }
        val steps = json.optJSONArray("steps")
        for (i in 0 until (steps?.length() ?: 0)) {
            val step = steps?.optJSONObject(i) ?: continue
            if (step.optString("type") == "model_output") texts(step.optJSONArray("content"))
        }
        if (out.isEmpty()) texts(json.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts"))
        return out.toString().takeIf { it.isNotBlank() }
    }

    private fun refusal(reply: Net.Reply): Refused {
        val error = try {
            JSONObject(reply.body).optJSONObject("error")
        } catch (_: Exception) {
            null
        }
        val said = error?.optString("message").orEmpty().ifEmpty { "HTTP ${reply.status}" }.take(300)
        val code = (error?.optString("code").orEmpty() + " " + error?.optString("status").orEmpty()).lowercase()
        val why = when {
            reply.status == 401 || "authentication" in code || "api key" in said.lowercase() || "api_key" in said.lowercase() -> Why.KEY
            reply.status == 429 || "exhausted" in code || "quota" in code || "rate_limit" in code -> Why.QUOTA
            reply.status == 404 -> Why.MISSING
            else -> Why.OTHER
        }
        return Refused(why, said)
    }
}
