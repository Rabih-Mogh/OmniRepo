package app.recitation.data

import android.content.Context
import org.json.JSONObject

/** Where a recitation was left: which recording, of which surah, how far in, and the verse that was being recited. */
data class Place(val trackId: String, val surah: Int, val ms: Int, val verse: Int)

/** The user's choices, and the place they reached, kept from one opening of the app to the next. */
object Settings {
    private fun store(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Whether the model may be downloaded even though Accent Coach is installed and could, once put right, lend it. */
    fun downloadModel(context: Context): Boolean = store(context).getBoolean("download_model", false)

    fun setDownloadModel(context: Context, allowed: Boolean) = store(context).edit().putBoolean("download_model", allowed).apply()

    /** Whether each word's meaning shows under it; [byDefault] until the user has chosen. */
    fun wordMeanings(context: Context, byDefault: Boolean): Boolean = store(context).getBoolean("word_meanings", byDefault)

    fun setWordMeanings(context: Context, shown: Boolean) = store(context).edit().putBoolean("word_meanings", shown).apply()

    /** How large the verses are drawn: 1 is the usual size. */
    fun textScale(context: Context): Float = store(context).getFloat("text_scale", 1f)

    fun setTextScale(context: Context, scale: Float) = store(context).edit().putFloat("text_scale", scale).apply()

    /** The script the verses are written in: the Madinah mushaf's until the user chooses another. */
    fun script(context: Context): Script = Script.of(store(context).getString("script", null))

    fun setScript(context: Context, script: Script) = store(context).edit().putString("script", script.key).apply()

    /** Whether the user paused the downloads. Paused work isn't picked up again until they say so. */
    fun paused(context: Context): Boolean = store(context).getBoolean("paused", false)

    fun setPaused(context: Context, paused: Boolean) = store(context).edit().putBoolean("paused", paused).apply()

    /** Remembers how far a recording has been listened to, and that it was the last one listened to. */
    fun savePlace(context: Context, place: Place) {
        val places = places(context)
        places.put(place.trackId, JSONObject().put("surah", place.surah).put("ms", place.ms).put("verse", place.verse))
        store(context).edit().putString("places", places.toString()).putString("last", place.trackId).apply()
    }

    /** Forgets a recording's place, once it has been heard to the end. */
    fun clearPlace(context: Context, trackId: String) {
        val places = places(context)
        places.remove(trackId)
        val edit = store(context).edit().putString("places", places.toString())
        if (store(context).getString("last", null) == trackId) edit.remove("last")
        edit.apply()
    }

    /** Where [trackId] was left, if it was. */
    fun place(context: Context, trackId: String): Place? {
        val saved = places(context).optJSONObject(trackId) ?: return null
        return Place(trackId, saved.getInt("surah"), saved.getInt("ms"), saved.getInt("verse"))
    }

    /** The place in the recording listened to most recently. */
    fun lastRead(context: Context): Place? = store(context).getString("last", null)?.let { place(context, it) }

    private fun places(context: Context): JSONObject =
        runCatching { JSONObject(store(context).getString("places", "{}")!!) }.getOrDefault(JSONObject())
}
