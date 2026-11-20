package app.recitation.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A verse: [verse] is its number in [surah], counted from 1. */
data class VerseRef(val surah: Int, val verse: Int) : Comparable<VerseRef> {
    override fun compareTo(other: VerseRef): Int = compareValuesBy(this, other, { it.surah }, { it.verse })
}

/** A named marker for a place reached. A pin sits on exactly one verse; putting it on another moves it. */
data class Pin(val id: String, val name: String, val at: VerseRef)

/**
 * A named set of verses kept to come back to, in the Quran's order. The one whose id is
 * [Marks.LATER] is there from the start and can't be deleted; the app names it itself, in
 * the phone's language, so its [name] is empty.
 */
data class VerseCollection(val id: String, val name: String, val verses: List<VerseRef>) {
    val builtIn: Boolean get() = id == Marks.LATER
}

/** The pins and collections, kept in a file on the phone. */
class Marks(private val file: File) {

    private val _pins = MutableStateFlow<List<Pin>>(emptyList())
    private val _collections = MutableStateFlow(listOf(VerseCollection(LATER, "", emptyList())))

    val pins: StateFlow<List<Pin>> = _pins.asStateFlow()

    /** Every collection, the built-in one first. */
    val collections: StateFlow<List<VerseCollection>> = _collections.asStateFlow()

    init {
        load()
    }

    /** Leaves a new pin called [name] on [at]. */
    fun addPin(name: String, at: VerseRef): Pin {
        val pin = Pin(newId(), name.trim(), at)
        _pins.value += pin
        save()
        return pin
    }

    /** Moves the pin to [at]: a pin is only ever on one verse. */
    fun movePin(id: String, at: VerseRef) {
        _pins.value = _pins.value.map { if (it.id == id) it.copy(at = at) else it }
        save()
    }

    fun removePin(id: String) {
        _pins.value = _pins.value.filter { it.id != id }
        save()
    }

    fun addCollection(name: String): VerseCollection {
        val collection = VerseCollection(newId(), name.trim(), emptyList())
        _collections.value += collection
        save()
        return collection
    }

    /** Deletes a collection the user made. The built-in one stays. */
    fun removeCollection(id: String) {
        _collections.value = _collections.value.filter { it.id != id || it.builtIn }
        save()
    }

    /** Puts [verse] in the collection, or takes it out. */
    fun setSaved(collectionId: String, verse: VerseRef, saved: Boolean) {
        _collections.value = _collections.value.map {
            if (it.id != collectionId) it else it.copy(verses = if (saved) (it.verses + verse).distinct().sorted() else it.verses - verse)
        }
        save()
    }

    private fun newId(): String = generateSequence(System.currentTimeMillis()) { it + 1 }.map { it.toString(36) }
        .first { id -> _pins.value.none { it.id == id } && _collections.value.none { it.id == id } }

    private fun load() {
        try {
            if (!file.exists()) return
            val json = JSONObject(file.readText())
            val pins = json.getJSONArray("pins")
            _pins.value = List(pins.length()) { i -> pins.getJSONObject(i).let { Pin(it.getString("id"), it.getString("name"), VerseRef(it.getInt("surah"), it.getInt("verse"))) } }
            val collections = json.getJSONArray("collections")
            val read = List(collections.length()) { i ->
                val collection = collections.getJSONObject(i)
                val verses = collection.getJSONArray("verses")
                VerseCollection(
                    collection.getString("id"), collection.getString("name"),
                    List(verses.length()) { k -> verses.getJSONArray(k).let { VerseRef(it.getInt(0), it.getInt(1)) } },
                )
            }
            _collections.value = listOf(read.firstOrNull { it.builtIn } ?: VerseCollection(LATER, "", emptyList())) + read.filter { !it.builtIn }
        } catch (_: Exception) {
            // An unreadable file: start afresh rather than refuse to open.
        }
    }

    private fun save() {
        val json = JSONObject()
            .put("pins", JSONArray(_pins.value.map { JSONObject().put("id", it.id).put("name", it.name).put("surah", it.at.surah).put("verse", it.at.verse) }))
            .put(
                "collections",
                JSONArray(
                    _collections.value.map { collection ->
                        JSONObject().put("id", collection.id).put("name", collection.name)
                            .put("verses", JSONArray(collection.verses.map { JSONArray().put(it.surah).put(it.verse) }))
                    },
                ),
            )
        val part = File(file.path + ".part")
        part.writeText(json.toString())
        part.renameTo(file)
    }

    companion object {
        /** The built-in collection: verses one has a question about, to study later. */
        const val LATER = "later"

        @Volatile private var instance: Marks? = null

        fun get(context: Context): Marks =
            instance ?: synchronized(this) { instance ?: Marks(File(context.applicationContext.filesDir, "marks.json")).also { instance = it } }
    }
}
