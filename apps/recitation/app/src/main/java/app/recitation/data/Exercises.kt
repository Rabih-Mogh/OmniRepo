package app.recitation.data

import app.recitation.core.Family
import java.io.File

/**
 * One question of spotting the rule: a [word] of [verse] of [surah] (both from 1), the
 * family of rules at work in it, and the families to choose among. Where a recording of
 * the surah has been listened to, the word can be heard: [sound] from [fromMs] until [untilMs].
 */
class Spot(val surah: Int, val verse: Int, val word: String, val family: Family, val options: List<Family>, val sound: File? = null, val fromMs: Int = 0, val untilMs: Int = 0)

/** A verse to echo: [verse] of [surah] (both from 1) as [sound] has it recited, from [fromMs] until [untilMs]. */
class Echo(val surah: Int, val verse: Int, val sound: File, val fromMs: Int, val untilMs: Int)
