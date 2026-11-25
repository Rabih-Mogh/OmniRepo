package app.spark.core

import java.time.LocalDate

/** What a reel is: an idea worth knowing, something that just happened, or a question whose answer waits for a tap. */
enum class Kind { IDEA, NEWS, PUZZLE }

/** Where a piece of news comes from. */
data class Source(val title: String, val url: String)

/** The moving picture on a reel or a lesson's page. The app draws each kind itself from these numbers. */
sealed interface Visual {
    /** A number that counts up from zero. */
    data class Number(val value: Double, val decimals: Int, val prefix: String, val suffix: String) : Visual

    /** Bars that grow, one of them picked out. */
    data class Bars(val labels: List<String>, val values: List<Double>, val highlight: Int?) : Visual

    /** [total] dots, [lit] of them lit: "2 in 23". */
    data class Dots(val total: Int, val lit: Int) : Visual

    /** A curve drawn from left to right. */
    data class Curve(val plot: Expr, val from: Double, val to: Double) : Visual

    /** Steps that appear one after another, each leading to the next. */
    data class Chain(val items: List<String>) : Visual
}

/** One screen of the feed. [day] is the day its pack was made, counted from 1970. */
data class Reel(
    val id: String,
    val day: Long,
    val topic: String,
    val tags: List<String>,
    val kind: Kind,
    val hook: String,
    val beats: List<String>,
    val takeaway: String,
    val visual: Visual?,
    val source: Source?,
    val lesson: String?,
)

data class Lesson(
    val id: String,
    val day: Long,
    val topic: String,
    val tags: List<String>,
    val title: String,
    val summary: String,
    val steps: List<Step>,
)

/** One page of a lesson. Every kind but [Read] asks for something and is marked. */
sealed interface Step {
    val text: String

    data class Read(override val text: String, val title: String?, val visual: Visual?) : Step

    /** One of [wrong] and [right], shown in a shuffled order. */
    data class Choice(override val text: String, val right: String, val wrong: List<String>, val why: String) : Step

    /** [items] in their right order; they are shown shuffled. */
    data class Order(override val text: String, val items: List<String>, val why: String) : Step

    /** A guess on a slider, right when it lands within [tolerance] of [answer]. */
    data class Estimate(
        override val text: String,
        val min: Double,
        val max: Double,
        val step: Double,
        val answer: Double,
        val tolerance: Double,
        val unit: String,
        val why: String,
    ) : Step

    /**
     * A curve bent by dragging the parameter `a`. With a [target] it is right once the curve
     * passes through that point; without one there is nothing to get wrong.
     */
    data class Explore(
        override val text: String,
        val plot: Expr,
        val xFrom: Double,
        val xTo: Double,
        val yFrom: Double,
        val yTo: Double,
        val param: Param,
        val target: Point?,
        val shows: List<Show>,
        val why: String,
    ) : Step {
        /** Whether the curve passes through the target with the parameter at [a]: within a 25th of the picture's height. */
        fun hits(a: Double): Boolean =
            target != null && kotlin.math.abs(plot.at(target.x, a) - target.y) <= (yTo - yFrom) / 25
    }
}

/** The slider under an [Step.Explore]: the values `a` can take. */
data class Param(val min: Double, val max: Double, val step: Double, val start: Double) {
    /** Every value the slider can rest on. */
    fun values(): List<Double> = (0..((max - min) / step + 1e-9).toInt()).map { min + it * step }
}

data class Point(val x: Double, val y: Double)

/** A number shown beside an [Step.Explore], worked out from `a` as it is dragged. */
data class Show(val label: String, val value: Expr)

data class Pack(val day: Long, val reels: List<Reel>, val lessons: List<Lesson>)

/**
 * Reads a pack: the JSON file of reels and lessons made on one day. `apps/spark/content/GENERATE.md`
 * describes the file for whoever writes one. The limits here are what keeps a reel
 * readable on a phone's screen.
 */
object Packs {
    const val FORMAT = 1

    /** The most text a reel can carry, with a visual, and still fit a small phone's screen without scrolling. */
    const val SCREENFUL = 520

    /** What a pack's text holds, and everything wrong with it. A reel or lesson with something wrong is left out of [pack]. */
    data class Reading(val pack: Pack?, val problems: List<String>)

    private val ID = Regex("[a-z0-9-]{3,80}")
    private val TOPIC = Regex("[a-z]{2,20}")
    private val TAG = Regex("[a-z0-9][a-z0-9 -]{1,29}")

    fun read(text: String): Reading {
        val problems = ArrayList<String>()
        val root = try {
            Json.read(text) as? Map<*, *>
        } catch (e: Json.Malformed) {
            return Reading(null, listOf("not JSON: ${e.message}"))
        } ?: return Reading(null, listOf("the pack isn't a JSON object"))

        val top = Fields(root, "pack", problems)
        val format = top.whole("format")
        if (format != FORMAT) return Reading(null, problems + "pack: format $format isn't one this version reads (it reads $FORMAT)")
        val day = try {
            LocalDate.parse(top.text("day", 10))
        } catch (_: Exception) {
            return Reading(null, problems + "pack: \"day\" must be a date like 2026-11-26")
        }

        val ids = HashSet<String>()
        val lessons = top.children("lessons", 0..20).mapNotNull { lesson(it, day, ids) }
        val known = lessons.map { it.id }.toSet()
        val reels = top.children("reels", 0..60).mapNotNull { reel(it, day, ids, known) }
        // A lesson is kept even so: the app lists every lesson. But the feed has no way into this one.
        for (lesson in lessons) if (reels.none { it.lesson == lesson.id }) problems += "${lesson.id}: no reel leads to this lesson"
        return Reading(Pack(day.toEpochDay(), reels, lessons), problems)
    }

    /** The names of the packs on offer, from the text of the branch's `index.json`. Null when the text isn't one. */
    fun index(text: String): List<String>? {
        val root = try {
            Json.read(text) as? Map<*, *>
        } catch (_: Json.Malformed) {
            null
        }
        if (root == null || root["format"] != FORMAT.toDouble()) return null
        return (root["packs"] as? List<*>)?.filterIsInstance<String>()?.take(100)
    }

    private fun reel(f: Fields, day: LocalDate, ids: MutableSet<String>, lessons: Set<String>): Reel? {
        val id = id(f, day, ids)
        val kind = when (val name = f.text("kind", 10)) {
            "idea" -> Kind.IDEA
            "news" -> Kind.NEWS
            "puzzle" -> Kind.PUZZLE
            else -> {
                if (name.isNotEmpty()) f.wrong("\"kind\" is \"$name\"; it must be idea, news or puzzle")
                Kind.IDEA
            }
        }
        val source = f.child("source")?.let { s ->
            val url = s.text("url", 300)
            if (!url.startsWith("https://") || url.any { it.isWhitespace() }) s.wrong("\"url\" must be an https:// address")
            Source(s.text("title", 60), url)
        }
        if (kind == Kind.NEWS && source == null) f.wrong("news needs a \"source\"")
        val lesson = f.textOrNull("lesson", 80)
        if (lesson != null && lesson !in lessons) f.wrong("its lesson \"$lesson\" isn't in this pack")
        val hook = f.text("hook", 90)
        val beats = f.texts("beats", 1..4, 150)
        val takeaway = f.text("takeaway", 170)
        val length = hook.length + beats.sumOf { it.length } + takeaway.length
        if (length > SCREENFUL) f.wrong("its hook, beats and takeaway come to $length characters; a reel is one screen, which holds $SCREENFUL")
        val reel = Reel(
            id = id,
            day = day.toEpochDay(),
            topic = topic(f),
            tags = tags(f),
            kind = kind,
            hook = hook,
            beats = beats,
            takeaway = takeaway,
            visual = f.child("visual")?.let(::visual),
            source = source,
            lesson = lesson,
        )
        return reel.takeIf { f.ok }
    }

    private fun lesson(f: Fields, day: LocalDate, ids: MutableSet<String>): Lesson? {
        val id = id(f, day, ids)
        val steps = f.children("steps", 3..10).map(::step)
        if (steps.isNotEmpty() && steps.all { it is Step.Read }) f.wrong("a lesson needs a step that asks for something")
        val lesson = Lesson(
            id = id,
            day = day.toEpochDay(),
            topic = topic(f),
            tags = tags(f),
            title = f.text("title", 60),
            summary = f.text("summary", 140),
            steps = steps,
        )
        return lesson.takeIf { f.ok }
    }

    // An id starts with its pack's day, so no two packs can ever use the same one.
    private fun id(f: Fields, day: LocalDate, ids: MutableSet<String>): String {
        val id = f.text("id", 80)
        f.rename(id)
        if (id.isNotEmpty() && (!ID.matches(id) || !id.startsWith("$day-"))) f.wrong("\"id\" must start with \"$day-\" and use only a–z, 0–9 and hyphens")
        if (!ids.add(id)) f.wrong("the id is used twice")
        return id
    }

    private fun topic(f: Fields): String {
        val topic = f.text("topic", 20)
        if (topic.isNotEmpty() && !TOPIC.matches(topic)) f.wrong("\"topic\" must be one lowercase word")
        return topic
    }

    private fun tags(f: Fields): List<String> {
        val tags = f.texts("tags", 1..5, 30)
        if (tags.any { !TAG.matches(it) }) f.wrong("\"tags\" must be lowercase words")
        return tags
    }

    private fun visual(f: Fields): Visual? = when (val kind = f.text("kind", 10)) {
        "number" -> {
            val decimals = f.wholeOrNull("decimals") ?: 0
            if (decimals !in 0..3) f.wrong("\"decimals\" must be 0 to 3")
            Visual.Number(f.number("value"), decimals, f.textOrNull("prefix", 12).orEmpty(), f.textOrNull("suffix", 12).orEmpty())
        }
        "bars" -> {
            val labels = f.texts("labels", 2..6, 14)
            val values = f.numbers("values", 2..6)
            val highlight = f.wholeOrNull("highlight")
            if (labels.size != values.size) f.wrong("bars need as many labels as values")
            if (values.any { it < 0 } || values.none { it > 0 }) f.wrong("bar values can't be negative, and one must be above zero")
            if (highlight != null && highlight !in values.indices) f.wrong("\"highlight\" must be the position of a bar, counted from 0")
            Visual.Bars(labels, values, highlight)
        }
        "dots" -> {
            val total = f.whole("total")
            val lit = f.whole("lit")
            if (total !in 2..200 || lit !in 0..total) f.wrong("dots need a total of 2 to 200 and no more lit than that")
            Visual.Dots(total, lit)
        }
        "curve" -> {
            val plot = f.formula("plot")
            val (from, to) = f.range("x")
            if (plot != null && from < to && samples(from, to).any { !plot.at(it).isFinite() }) f.wrong("the curve has no value somewhere between $from and $to")
            plot?.let { Visual.Curve(it, from, to) }
        }
        "chain" -> Visual.Chain(f.texts("items", 2..5, 22))
        else -> {
            if (kind.isNotEmpty()) f.wrong("\"kind\" is \"$kind\"; a visual is a number, bars, dots, curve or chain")
            null
        }
    }

    private fun step(f: Fields): Step = when (val kind = f.text("kind", 10)) {
        "read" -> Step.Read(f.text("text", 500, lines = true), f.textOrNull("title", 60), f.child("visual")?.let(::visual))
        "choice" -> {
            val options = f.texts("options", 2..4, 90)
            if (options.distinct().size != options.size) f.wrong("two options are the same")
            Step.Choice(f.text("text", 300), options.firstOrNull().orEmpty(), options.drop(1), f.text("why", 300))
        }
        "order" -> {
            val items = f.texts("items", 3..5, 70)
            if (items.distinct().size != items.size) f.wrong("two items are the same")
            Step.Order(f.text("text", 300), items, f.text("why", 300))
        }
        "estimate" -> {
            val (min, max) = f.range("range")
            val step = f.number("step")
            val answer = f.number("answer")
            val tolerance = f.number("tolerance")
            when {
                !(step > 0) || (max - min) / step > 1000 -> f.wrong("\"step\" must be above zero and give at most 1000 stops")
                answer !in min..max -> f.wrong("\"answer\" is outside the range")
                // The slider only rests on its stops, so one of them has to count as right.
                !(tolerance >= 0) || kotlin.math.abs(min + Math.round((answer - min) / step) * step - answer) > tolerance + 1e-9 ->
                    f.wrong("no stop of the slider is within \"tolerance\" of the answer")
            }
            Step.Estimate(f.text("text", 300), min, max, step, answer, tolerance, f.textOrNull("unit", 12).orEmpty(), f.text("why", 300))
        }
        "explore" -> explore(f)
        else -> {
            if (kind.isNotEmpty()) f.wrong("\"kind\" is \"$kind\"; a step is read, choice, order, estimate or explore")
            Step.Read("", null, null)
        }
    }

    private fun explore(f: Fields): Step {
        val plot = f.formula("plot")
        val (xFrom, xTo) = f.range("x")
        val (yFrom, yTo) = f.range("y")
        val param = f.child("param")?.let { p ->
            val (min, max) = p.range("range")
            val step = p.number("step")
            val start = p.number("start")
            if (!(step > 0) || (max - min) / step > 1000) p.wrong("\"step\" must be above zero and give at most 1000 stops")
            if (start !in min..max) p.wrong("\"start\" is outside the range")
            Param(min, max, step, start)
        }
        if (param == null) f.wrong("\"param\" is missing")
        val target = f.child("target")?.let { Point(it.number("x"), it.number("y")) }
        val shows = f.children("show", 0..3, required = false).mapNotNull { s -> s.formula("value")?.let { Show(s.text("label", 24), it) } }
        val step = Step.Explore(
            text = f.text("text", 300),
            plot = plot ?: Expr.parse("0"),
            xFrom = xFrom, xTo = xTo, yFrom = yFrom, yTo = yTo,
            param = param ?: Param(0.0, 1.0, 1.0, 0.0),
            target = target,
            shows = shows,
            why = f.text("why", 300),
        )
        if (f.ok && target != null) {
            when {
                target.x !in xFrom..xTo || target.y !in yFrom..yTo -> f.wrong("the target is outside the picture")
                step.hits(step.param.start) -> f.wrong("the curve already passes through the target at the start")
                step.param.values().none(step::hits) -> f.wrong("no stop of the slider takes the curve through the target")
            }
        }
        return step
    }

    private fun samples(from: Double, to: Double): List<Double> = (0..60).map { from + (to - from) * it / 60 }

    /**
     * One JSON object being read. What is wrong with it is written down, against it and
     * against the object it sits in, and the reading carries on to find the rest.
     */
    private class Fields(
        private val map: Map<*, *>,
        private var where: String,
        private val problems: MutableList<String>,
        private val parent: Fields? = null,
    ) {
        var ok = true
            private set

        fun wrong(what: String) {
            problems += "$where: $what"
            spoil()
        }

        private fun spoil() {
            ok = false
            parent?.spoil()
        }

        /** Names the object by its id in what is reported from here on. */
        fun rename(id: String) {
            if (id.isNotEmpty()) where = id
        }

        fun textOrNull(name: String, max: Int, lines: Boolean = false): String? {
            val value = map[name] ?: return null
            val text = (value as? String)?.trim()
            when {
                text.isNullOrEmpty() -> wrong("\"$name\" must be text")
                text.length > max -> wrong("\"$name\" is ${text.length} characters; the most is $max")
                !lines && '\n' in text -> wrong("\"$name\" must be one line")
                else -> return text
            }
            return null
        }

        fun text(name: String, max: Int, lines: Boolean = false): String {
            if (map[name] == null) wrong("\"$name\" is missing")
            return textOrNull(name, max, lines).orEmpty()
        }

        private fun numberOrNull(name: String): Double? {
            val value = map[name] ?: return null
            val number = (value as? Double)?.takeIf { it.isFinite() }
            if (number == null) wrong("\"$name\" must be a number")
            return number
        }

        fun number(name: String): Double {
            if (map[name] == null) wrong("\"$name\" is missing")
            return numberOrNull(name) ?: 0.0
        }

        fun wholeOrNull(name: String): Int? {
            val number = numberOrNull(name) ?: return null
            if (number != Math.rint(number) || kotlin.math.abs(number) > 1e9) {
                wrong("\"$name\" must be a whole number")
                return null
            }
            return number.toInt()
        }

        fun whole(name: String): Int {
            if (map[name] == null) wrong("\"$name\" is missing")
            return wholeOrNull(name) ?: 0
        }

        private fun list(name: String, count: IntRange, required: Boolean = true): List<*> {
            val value = map[name]
            if (value == null && !required) return emptyList<Any?>()
            val list = value as? List<*>
            if (list == null || list.size !in count) {
                wrong("\"$name\" must be a list of ${count.first} to ${count.last}")
                return emptyList<Any?>()
            }
            return list
        }

        fun texts(name: String, count: IntRange, max: Int): List<String> = list(name, count).mapNotNull { item ->
            val text = (item as? String)?.trim()
            when {
                text.isNullOrEmpty() -> wrong("\"$name\" must hold text")
                text.length > max -> wrong("\"$text\" in \"$name\" is ${text.length} characters; the most is $max")
                '\n' in text -> wrong("each of \"$name\" must be one line")
                else -> return@mapNotNull text
            }
            null
        }

        fun numbers(name: String, count: IntRange): List<Double> = list(name, count).mapNotNull { item ->
            (item as? Double)?.takeIf { it.isFinite() } ?: run {
                wrong("\"$name\" must hold numbers")
                null
            }
        }

        /** Two numbers, the first below the second. */
        fun range(name: String): Pair<Double, Double> {
            val pair = numbers(name, 2..2)
            if (pair.size != 2 || pair[0] >= pair[1]) {
                wrong("\"$name\" must be two numbers, the lower one first")
                return 0.0 to 1.0
            }
            return pair[0] to pair[1]
        }

        fun formula(name: String): Expr? {
            val text = text(name, 200)
            if (text.isEmpty()) return null
            return try {
                Expr.parse(text)
            } catch (e: Expr.Malformed) {
                wrong("\"$name\": ${e.message}")
                null
            }
        }

        /** An object inside this one. */
        fun child(name: String): Fields? {
            val value = map[name] ?: return null
            val child = value as? Map<*, *>
            if (child == null) {
                wrong("\"$name\" must be an object")
                return null
            }
            return Fields(child, "$where, $name", problems, this)
        }

        fun children(name: String, count: IntRange, required: Boolean = true): List<Fields> =
            list(name, count, required).mapIndexedNotNull { i, item ->
                val child = item as? Map<*, *>
                if (child == null) wrong("\"$name\" must hold objects")
                child?.let { Fields(it, if (where == "pack") "$name ${i + 1}" else "$where, $name ${i + 1}", problems, this) }
            }
    }
}
