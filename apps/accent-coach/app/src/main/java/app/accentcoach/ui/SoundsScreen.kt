package app.accentcoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.accentcoach.DrillRun
import app.accentcoach.PracticeViewModel
import app.accentcoach.Stage
import app.accentcoach.core.DrillItem
import app.accentcoach.core.DrillResult
import app.accentcoach.core.Drills
import app.accentcoach.core.Lesson
import app.accentcoach.core.Said
import app.accentcoach.core.Verdict

/** The Sounds page: the lessons, one of them open, or its drill. */
@Composable
fun Sounds(vm: PracticeViewModel, record: () -> Unit) {
    val lesson = vm.lesson
    val run = vm.drill
    when {
        lesson == null -> LessonList(vm)
        run == null -> LessonPage(vm, lesson)
        run.finished -> DrillEnd(vm, run)
        else -> DrillStep(vm, run, record)
    }
}

@Composable
private fun LessonList(vm: PracticeViewModel) {
    Text(
        "Each sound has a lesson on how it is made, then a drill of words with it. " +
            "A lesson is learned once three in four of its drill come out native.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Label("${vm.lessons.count { vm.practice.learned(it) }} OF ${vm.lessons.size} LEARNED")
    vm.lessons.forEach { lesson ->
        val best = vm.practice.drills[lesson.id]
        val learned = vm.practice.learned(lesson)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth().clip(CardDefaults.shape).clickable { vm.open(lesson) },
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "/${lesson.sound}/",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(64.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(lesson.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            best == null -> "Not tried yet"
                            learned -> "Learned. Best: $best of ${lesson.items.size}"
                            else -> "Best so far: $best of ${lesson.items.size}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (learned) Icon(Icons.Rounded.Check, contentDescription = "Learned", tint = verdictColor(Verdict.OK))
            }
        }
    }
}

/** A lesson: how the sound is made, the usual slip, and its words to hear before the drill. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LessonPage(vm: PracticeViewModel, lesson: Lesson) {
    BackRow("Sounds", onBack = { vm.back() })
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("/${lesson.sound}/", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            Text(lesson.title, style = MaterialTheme.typography.headlineSmall)
            Label("HOW IT'S MADE")
            Text(lesson.made, style = MaterialTheme.typography.bodyLarge)
            Label("THE USUAL SLIP")
            Text(lesson.slip, style = MaterialTheme.typography.bodyLarge)
        }
    }

    Label("WORDS WITH IT. TAP ONE TO HEAR IT.", Modifier.padding(start = 4.dp, top = 4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        lesson.words.forEach { word ->
            AssistChip(onClick = { vm.listen(slow = false, text = word.text) }, label = { Text(word.text) })
        }
    }
    if (lesson.pairs.isNotEmpty()) {
        Label("PAIRS IT TELLS APART", Modifier.padding(start = 4.dp, top = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            lesson.pairs.forEach { (with, without) ->
                AssistChip(
                    onClick = { vm.listen(slow = true, text = "${with.text}, ${without.text}") },
                    label = { Text("${with.text} · ${without.text}") },
                )
            }
        }
    }

    Button(onClick = vm::startDrill, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text("Start the drill (${lesson.items.size} to say)")
    }
    vm.practice.drills[lesson.id]?.let { best ->
        Text(
            if (vm.practice.learned(lesson)) "Learned. Best: $best of ${lesson.items.size}." else "Best so far: $best of ${lesson.items.size}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/** One item of a drill: the word or pair to say, then how its sound came out. */
@Composable
private fun DrillStep(vm: PracticeViewModel, run: DrillRun, record: () -> Unit) {
    val item = run.item ?: return
    val count = run.lesson.items.size
    BackRow(run.lesson.title, onBack = { vm.back() }, trailing = "${run.at + 1} of $count")
    LinearProgressIndicator(progress = { run.at / count.toFloat() }, modifier = Modifier.fillMaxWidth())

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (item.isPair) "SAY BOTH, ONE AFTER THE OTHER" else "SAY THE WORD",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
            )
            Text(item.text, style = MaterialTheme.typography.headlineMedium)
            NativeSounds(run.lesson, item)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { vm.listen(slow = false) }) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Listen")
                }
                OutlinedButton(onClick = { vm.listen(slow = true) }) { Text("Slowly") }
            }
        }
    }

    when (vm.stage) {
        Stage.Idle -> RecordButton(recording = false, level = 0f, onClick = record, prompt = "Tap and say it aloud")
        Stage.Recording -> {
            RecordButton(recording = true, level = vm.level, onClick = vm::stopRecording)
            HeardBox(vm.live, "Listening… tap to stop when you've finished.")
        }
        Stage.Analysing -> {
            Analysing()
            HeardBox(vm.live, null)
        }
        is Stage.Done -> run.result?.let { StepResult(vm, run, it) }
    }
}

/** The item's native sounds, the ones the drill judges picked out. */
@Composable
private fun NativeSounds(lesson: Lesson, item: DrillItem) {
    val judged = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    Text(
        buildAnnotatedString {
            append("/")
            Drills.sounds(lesson, item).forEachIndexed { w, word ->
                if (w > 0) append(" ")
                for ((sound, counts) in word) if (counts) withStyle(judged) { append(sound) } else append(sound)
            }
            append("/")
        },
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepResult(vm: PracticeViewModel, run: DrillRun, result: DrillResult) {
    val last = run.at == run.lesson.items.lastIndex
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(saidLabel(result.said), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = saidColor(result.said))
            Text(
                when (result.said) {
                    Said.UNHEARD -> "I didn't hear anything. Try again a little closer to the phone."
                    Said.NATIVE -> if (run.item?.isPair == true) "The two words came out different, each with its own sound." else "That was a native /${run.lesson.sound}/."
                    else -> result.judged.filter { it.verdict != Verdict.OK }.joinToString(" ") {
                        if (it.heard == null) "/${it.expected}/ wasn't heard." else "I heard /${it.heard}/ where /${it.expected}/ belongs."
                    }
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            if (result.said != Said.UNHEARD) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    result.assessment.words.forEach { WordColumn(it) }
                }
                Text(
                    (if (run.item?.isPair == true) "Only the sound the two words differ in counts here." else "Only the /${run.lesson.sound}/ counts here.") +
                        " Everything I heard: /" + result.assessment.heard.joinToString("") + "/",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (result.said == Said.CLOSE || result.said == Said.OFF) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label("HOW IT'S MADE")
                Text(run.lesson.made, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = vm::playMine, enabled = vm.hasRecording, modifier = Modifier.weight(1f)) { Text("Play mine") }
        OutlinedButton(onClick = { vm.listen(slow = false) }, modifier = Modifier.weight(1f)) { Text("Native") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        FilledTonalButton(onClick = vm::tryAgain, modifier = Modifier.weight(1f)) { Text("Try again") }
        FilledTonalButton(onClick = vm::nextItem, modifier = Modifier.weight(1f)) { Text(if (last) "Finish" else "Next") }
    }
}

/** The end of a drill: how many of its items were native, and whether that makes the lesson learned. */
@Composable
private fun DrillEnd(vm: PracticeViewModel, run: DrillRun) {
    val count = run.lesson.items.size
    val learned = Drills.learned(run.native, count)
    BackRow(run.lesson.title, onBack = { vm.back() })
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "${run.native} of $count native",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = verdictColor(if (learned) Verdict.OK else Verdict.CLOSE),
            )
            Text(
                if (learned) "That is three in four or better, so this lesson is learned."
                else "Three in four makes the lesson learned. Read how the sound is made once more and have another go.",
                style = MaterialTheme.typography.bodyLarge,
            )
            run.lesson.items.forEachIndexed { i, item ->
                val said = run.results[i]?.said ?: Said.UNHEARD
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(saidColor(said)))
                    Spacer(Modifier.width(10.dp))
                    Text(item.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(saidLabel(said), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        FilledTonalButton(onClick = vm::startDrill, modifier = Modifier.weight(1f)) { Text("Drill again") }
        FilledTonalButton(onClick = { vm.back() }, modifier = Modifier.weight(1f)) { Text("Back to the lesson") }
    }
}

@Composable
private fun BackRow(title: String, onBack: () -> Unit, trailing: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = modifier)
}

private fun saidLabel(said: Said): String = when (said) {
    Said.NATIVE -> "Native"
    Said.CLOSE -> "Close"
    Said.OFF -> "Not yet"
    Said.UNHEARD -> "Nothing heard"
}

private fun saidColor(said: Said): Color = verdictColor(
    when (said) {
        Said.NATIVE -> Verdict.OK
        Said.CLOSE -> Verdict.CLOSE
        Said.OFF -> Verdict.WRONG
        Said.UNHEARD -> Verdict.MISSING
    },
)
