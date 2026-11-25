package app.spark.core

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/** How much a topic or a tag has held the user so far, from -1 (skipped) to 1 (loved), and over how many reels. */
data class Leaning(val value: Double = 0.0, val count: Int = 0) {
    /** Moves a part of the way towards [reward]; the latest reels count the most. */
    fun after(reward: Double, rate: Double): Leaning = Leaning(value + rate * (reward - value), count + 1)
}

/** What the app has learned about the user's attention. It never leaves the phone unless they send it. */
data class Taste(val topics: Map<String, Leaning> = emptyMap(), val tags: Map<String, Leaning> = emptyMap()) {

    fun after(reel: Reel, reward: Double): Taste = Taste(
        topics + (reel.topic to (topics[reel.topic] ?: Leaning()).after(reward, 0.2)),
        tags + reel.tags.associateWith { (tags[it] ?: Leaning()).after(reward, 0.3) },
    )
}

/**
 * How one reel went. [share] is how much of its running time it was on screen: 1 is all of
 * it, and more is someone lingering.
 */
data class Watch(val share: Double, val liked: Boolean = false, val lesson: Boolean = false, val less: Boolean = false) {

    /** From -1 to 1: what this says about showing more reels like it. */
    fun reward(): Double = when {
        less -> -1.0
        // Flicked past before the first line could be read.
        share < 0.15 && !liked && !lesson -> -0.6
        else -> (share.coerceIn(0.0, 1.0) * 0.5 + (if (liked) 0.5 else 0.0) + (if (lesson) 0.4 else 0.0)).coerceAtMost(1.0)
    }
}

/**
 * Chooses what comes next in the feed. Each reel not yet seen is given a score, and the
 * highest goes next:
 *
 * - how the user has taken to its topic and its tags;
 * - a wish it answers;
 * - how new it is, which counts most for news;
 * - a nudge towards topics shown least so far, so the feed keeps trying things and
 *   doesn't narrow to one subject;
 * - a mark against the topic of the reels just before it;
 * - a little chance, so two days with the same taste don't run in the same order.
 */
object Feed {

    /** The best of [pool] to show after [before], or null when everything has been seen. */
    fun next(
        pool: Collection<Reel>,
        taste: Taste,
        seen: Set<String>,
        before: List<Reel>,
        wishes: List<String>,
        today: Long,
        random: Random,
    ): Reel? {
        val shown = before.mapTo(HashSet()) { it.id }
        val wanted = wishes.flatMapTo(HashSet(), ::words)
        return pool
            .filter { it.id !in seen && it.id !in shown }
            .maxByOrNull { score(it, taste, wanted, today) + repeat(it, before) + random.nextDouble() * 0.2 }
    }

    internal fun score(reel: Reel, taste: Taste, wanted: Set<String>, today: Long): Double {
        val topic = taste.topics[reel.topic] ?: Leaning()
        val tags = reel.tags.map { taste.tags[it]?.value ?: 0.0 }.ifEmpty { listOf(0.0) }.average()
        val age = (today - reel.day).coerceAtLeast(0).toDouble()
        val fresh = when {
            reel.kind != Kind.NEWS -> 0.25 * exp(-age / 10)
            age > 14 -> -0.5 // old news
            else -> 0.8 * exp(-age / 2)
        }
        val reelsSoFar = taste.topics.values.sumOf { it.count }
        val untried = 0.35 * sqrt(ln(reelsSoFar + 2.0) / (topic.count + 1.0))
        return topic.value + 0.6 * tags + (if (answers(reel, wanted)) 0.7 else 0.0) + fresh + untried
    }

    // Two reels on one topic in a row is fine; a third is a rut.
    private fun repeat(reel: Reel, before: List<Reel>): Double {
        val last = before.takeLast(2).reversed()
        return when {
            last.size == 2 && last.all { it.topic == reel.topic } -> -1.0
            last.firstOrNull()?.topic == reel.topic -> -0.4
            else -> 0.0
        }
    }

    /** Whether [reel] is about something the user asked for, going by the words of its tags and its hook. */
    fun answers(reel: Reel, wishes: List<String>): Boolean = answers(reel, wishes.flatMapTo(HashSet(), ::words))

    private fun answers(reel: Reel, wanted: Set<String>): Boolean =
        wanted.isNotEmpty() && (reel.tags.any { tag -> words(tag).any(wanted::contains) } || words(reel.hook).any(wanted::contains))

    // The words that carry a subject: four letters or more, so "the" and "how" match nothing.
    private fun words(text: String): List<String> =
        text.lowercase().split(Regex("[^\\p{L}\\p{Nd}]+")).filter { it.length >= 4 && it !in COMMON }

    private val COMMON = setOf(
        "about", "after", "also", "been", "does", "from", "have", "into", "learn", "more", "most", "much",
        "that", "their", "them", "then", "there", "these", "they", "this", "want", "what", "when", "where",
        "which", "will", "with", "work", "works", "would", "your",
    )
}
