package app.accentcoach.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.accentcoach.PracticeViewModel
import app.accentcoach.Stage
import app.accentcoach.core.Assessment
import app.accentcoach.core.Verdict
import app.accentcoach.core.WordResult

@Composable
fun PracticeScreen(vm: PracticeViewModel) {
    val context = LocalContext.current
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startRecording()
    }
    fun record() {
        val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (ok) vm.startRecording() else askMic.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Accent Coach", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("General American", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = vm.focus == null, onClick = { vm.chooseFocus(null) }, label = { Text("All") })
            vm.focuses.forEach { f ->
                FilterChip(selected = vm.focus == f, onClick = { vm.chooseFocus(f) }, label = { Text(f) })
            }
        }

        SentenceCard(vm)

        when (val s = vm.stage) {
            Stage.Idle -> RecordButton(recording = false, level = 0f, onClick = ::record)
            Stage.Recording -> {
                RecordButton(recording = true, level = vm.level, onClick = vm::stopRecording)
                HeardBox(vm.live, "Listening… tap to stop when you've finished.")
            }
            Stage.Analysing -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Comparing your sounds…")
                }
                HeardBox(vm.live, null)
            }
            is Stage.Done -> Result(vm, s.assessment)
        }

        vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun SentenceCard(vm: PracticeViewModel) {
    val s = vm.sentence
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.focus.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text("${vm.visible.indexOf(s) + 1} of ${vm.visible.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(s.text, style = MaterialTheme.typography.headlineSmall)
            Text(
                "/" + s.words.joinToString(" ") { it.phonemes.joinToString("") } + "/",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                color = MaterialTheme.colorScheme.primary,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { vm.listen(slow = false) }) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Listen")
                }
                OutlinedButton(onClick = { vm.listen(slow = true) }) { Text("Slowly") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = vm::previous) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous sentence") }
                IconButton(onClick = vm::next) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next sentence") }
            }
        }
    }
}

@Composable
private fun RecordButton(recording: Boolean, level: Float, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
            if (recording) {
                // A halo that swells with your voice, so you can see the mic is hearing you.
                Box(
                    Modifier
                        .size(120.dp)
                        .scale(0.75f + 0.25f * (level * 3f).coerceAtMost(1f))
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)),
                )
            }
            FilledIconButton(
                onClick = onClick,
                modifier = Modifier.size(84.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (recording) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                ),
            ) {
                if (recording) {
                    Box(Modifier.size(26.dp).clip(MaterialTheme.shapes.extraSmall).background(Color.White))
                } else {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(Color.White))
                }
            }
        }
        Text(
            if (recording) "Tap to stop" else "Tap and read the sentence aloud",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The sounds heard, as IPA, while you speak. */
@Composable
private fun HeardBox(heard: List<String>, hint: String?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("WHAT I HEAR", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                if (heard.isEmpty()) "…" else "/" + heard.joinToString("") + "/",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Result(vm: PracticeViewModel, a: Assessment) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${a.score}%", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold, color = scoreColor(a.score))
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        a.heard.isEmpty() -> "I didn't hear anything. Try again a little closer to the phone."
                        a.score >= 90 -> "native-like"
                        a.score >= 70 -> "close, a few sounds to work on"
                        else -> "keep practising the highlighted sounds"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                a.words.forEach { WordColumn(it) }
            }
            Legend()
            Text(
                "Everything I heard: /" + a.heard.joinToString("") + "/",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (a.tips.isNotEmpty()) {
        Text("WHAT TO WORK ON", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        a.tips.forEach { tip ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tip.sound, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = verdictColor(Verdict.WRONG))
                    Text(tip.advice, style = MaterialTheme.typography.bodyMedium)
                    Text("In: " + tip.words.joinToString(", ") { it.trim('.', ',', '?', '!') }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = vm::playMine, enabled = vm.hasRecording, modifier = Modifier.weight(1f)) { Text("Play mine") }
        OutlinedButton(onClick = { vm.listen(slow = false) }, modifier = Modifier.weight(1f)) { Text("Native") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        FilledTonalButton(onClick = vm::tryAgain, modifier = Modifier.weight(1f)) { Text("Try again") }
        FilledTonalButton(onClick = vm::next, modifier = Modifier.weight(1f)) { Text("Next sentence") }
    }
}

/** A word, its native sounds, and what you said, each sound coloured by how native it was. */
@Composable
private fun WordColumn(w: WordResult) {
    val wordColor = when {
        w.needsWork -> verdictColor(Verdict.WRONG)
        w.slightlyOff -> verdictColor(Verdict.CLOSE)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(w.word.text.trim('.', ',', '?', '!'), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = wordColor)
        Text(native(w), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(yours(w), style = MaterialTheme.typography.bodyLarge)
    }
}

private fun native(w: WordResult): AnnotatedString = buildAnnotatedString {
    w.phonemes.forEach { p -> p.expected?.let { append(it) } }
}

private fun yours(w: WordResult): AnnotatedString = buildAnnotatedString {
    for (p in w.phonemes) {
        val color = verdictColor(p.verdict)
        when (p.verdict) {
            // Show the native sound you skipped, struck through.
            Verdict.MISSING -> withStyle(SpanStyle(color = color, textDecoration = TextDecoration.LineThrough)) { append(p.expected ?: "") }
            Verdict.OK -> withStyle(SpanStyle(color = color)) { append(p.heard ?: "") }
            else -> withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(p.heard ?: "") }
        }
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(
            Verdict.OK to "native",
            Verdict.CLOSE to "slightly off",
            Verdict.WRONG to "accent",
            Verdict.MISSING to "missed",
        ).forEach { (v, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(verdictColor(v)))
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun scoreColor(score: Int): Color = when {
    score >= 90 -> verdictColor(Verdict.OK)
    score >= 70 -> verdictColor(Verdict.CLOSE)
    else -> verdictColor(Verdict.WRONG)
}
