package app.recitation.data

import android.content.Context
import org.json.JSONObject

/** Where a recitation was left: which recording, of which surah, how far in, and the verse that was being recited. */
data class Place(val trackId: String, val surah: Int, val ms: Int, val verse: Int)

/** Whether the app is light or dark: as the phone is set, or one of them whatever the phone says. */
enum class Appearance(val key: String) {
    AUTO("auto"), LIGHT("light"), DARK("dark");

    companion object {
        fun of(key: String?): Appearance = entries.firstOrNull { it.key == key } ?: AUTO
    }
}

/** The user's choices, and the place they reached, kept from one opening of the app to the next. */
object Settings {
    private fun store(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** How the speech engine was found to run fastest on this phone, as `Tuning` writes it; null until that has been tried. */
    fun tuning(context: Context): String? = store(context).getString("tuning", null)

    fun setTuning(context: Context, tuning: String) = store(context).edit().putString("tuning", tuning).apply()

    /** Whether each word's meaning shows under it; [byDefault] until the user has chosen. */
    fun wordMeanings(context: Context, byDefault: Boolean): Boolean = store(context).getBoolean("word_meanings", byDefault)

    fun setWordMeanings(context: Context, shown: Boolean) = store(context).edit().putBoolean("word_meanings", shown).apply()

    /** How large the verses are drawn: 1 is the usual size. */
    fun textScale(context: Context): Float = store(context).getFloat("text_scale", 1f)

    fun setTextScale(context: Context, scale: Float) = store(context).edit().putFloat("text_scale", scale).apply()

    /** The script the verses are written in: the Madinah mushaf's until the user chooses another. */
    fun script(context: Context): Script = Script.of(store(context).getString("script", null))

    fun setScript(context: Context, script: Script) = store(context).edit().putString("script", script.key).apply()

    /**
     * What the reader has chosen of [kind] (see [Pack]): the script as above; word meanings
     * in English and the Saheeh International translation until they choose others; and
     * no exegesis until they choose one.
     */
    fun pack(context: Context, kind: Kind): Pack? = when (kind) {
        Kind.SCRIPT -> Pack.of(script(context))
        else -> store(context).getString("pack_${kind.key}", null)?.let { kept -> runCatching { Pack.of(org.json.JSONObject(kept)) }.getOrNull() }
            ?: when (kind) {
                Kind.MEANINGS -> Pack.ENGLISH
                Kind.TRANSLATION -> Pack.SAHEEH
                else -> null
            }
    }

    fun setPack(context: Context, pack: Pack) {
        if (pack.kind == Kind.SCRIPT) setScript(context, Script.of(pack.id)) else store(context).edit().putString("pack_${pack.kind.key}", pack.json().toString()).apply()
    }

    /** Back to what [kind] is until something is chosen. */
    fun clearPack(context: Context, kind: Kind) {
        if (kind == Kind.SCRIPT) setScript(context, Script.MADINAH) else store(context).edit().remove("pack_${kind.key}").apply()
    }

    /** Whether letters are coloured by the rule of recitation they fall under. */
    fun tajweed(context: Context): Boolean = store(context).getBoolean("tajweed", true)

    fun setTajweed(context: Context, shown: Boolean) = store(context).edit().putBoolean("tajweed", shown).apply()

    /** Light, dark, or following the phone: the phone, until the user chooses. */
    /** Whether the tajweed model, which checks the reader's own recitation, is to be fetched by the background work. */
    fun tajweedWanted(context: Context): Boolean = store(context).getBoolean("tajweed_wanted", false)

    fun setTajweedWanted(context: Context, wanted: Boolean) = store(context).edit().putBoolean("tajweed_wanted", wanted).apply()

    /** The best a voice exercise has come to so far, by the exercise's name; 0 before the first go. */
    fun voiceBest(context: Context, exercise: String): Float = store(context).getFloat("voice_best_$exercise", 0f)

    fun setVoiceBest(context: Context, exercise: String, value: Float) = store(context).edit().putFloat("voice_best_$exercise", value).apply()

    fun appearance(context: Context): Appearance = Appearance.of(store(context).getString("appearance", null))

    fun setAppearance(context: Context, appearance: Appearance) = store(context).edit().putString("appearance", appearance.key).apply()

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
