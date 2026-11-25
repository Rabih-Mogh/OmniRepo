package app.spark.core

import java.util.Locale

/**
 * What the app sends to whoever prepares the next batch: the things the user wants to
 * learn, and what has held their attention so far. It is plain text, written to be read by
 * a person or a model. The app opens it as a new issue on the repo for the user to submit,
 * so nothing is sent without them seeing it.
 */
object Letter {
    const val TITLE = "Spark wishes"

    fun write(wishes: List<String>, taste: Taste): String = buildString {
        append("Sent from the Spark app. The next scheduled run reads the newest issue with this title.\n\n")
        append("## Wishes\n")
        if (wishes.isEmpty()) append("(none)\n")
        // One line each, so a wish can't pass for a heading or a second wish.
        for (wish in wishes) append("- ").append(wish.replace(Regex("\\s+"), " ").trim()).append('\n')
        append("\n## Taste\nFrom -1 (skipped) to 1 (held my attention), with the number of reels it comes from.\n")
        if (taste.topics.isEmpty()) append("(nothing watched yet)\n")
        for ((topic, leaning) in taste.topics.entries.sortedByDescending { it.value.value }) {
            append("- ").append(topic).append(": ").append(number(leaning.value)).append(" (").append(leaning.count).append(")\n")
        }
        val tags = taste.tags.entries.filter { it.value.count >= 2 }.sortedByDescending { it.value.value }
        val liked = tags.filter { it.value.value > 0.15 }.take(12)
        val skipped = tags.filter { it.value.value < -0.15 }.takeLast(8)
        if (liked.isNotEmpty()) append("\nMore of: ").append(liked.joinToString { it.key }).append('\n')
        if (skipped.isNotEmpty()) append("Less of: ").append(skipped.joinToString { it.key }).append('\n')
    }

    private fun number(value: Double): String = String.format(Locale.ROOT, "%.2f", value)
}
