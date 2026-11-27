package app.recitation.data

import app.recitation.core.Case
import app.recitation.core.Family
import app.recitation.core.Rule
import java.io.File

/**
 * One question of spotting the rule: a [word] of [verse] of [surah] (both from 1), the
 * family of rules at work in it, and the families to choose among. Where a recording of
 * the surah has been listened to, the word can be heard: [sound] from [fromMs] until
 * [untilMs]. [rule] is the rule of that family in the word, to be taught once it is answered.
 */
class Spot(
    val surah: Int, val verse: Int, val word: String, val family: Family, val options: List<Family>,
    val sound: File? = null, val fromMs: Int = 0, val untilMs: Int = 0, val rule: Rule? = null,
)

/** A verse to echo: [verse] of [surah] (both from 1) as [sound] has it recited, from [fromMs] until [untilMs]. */
class Echo(val surah: Int, val verse: Int, val sound: File, val fromMs: Int, val untilMs: Int)

/**
 * Words of a recording that show a rule, to be heard in a lesson: [words] as written, in
 * [sound] from [fromMs] until [untilMs]. [case] is what makes them an example of it: the
 * letter the rule falls on in them and what brings it about, for saying so under them.
 */
class Hear(val words: String, val sound: File, val fromMs: Int, val untilMs: Int, val case: Case? = null)

/**
 * A verse as the reciter of a recording on the phone says it: [sound] from [fromMs] until
 * [untilMs]. And within it, the words that show what an exercise is about, from [placeFromMs]
 * until [placeUntilMs]; -1 where those couldn't be told.
 */
class Voice(val sound: File, val fromMs: Int, val untilMs: Int, val placeFromMs: Int = -1, val placeUntilMs: Int = -1)
