package app.accentcoach.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.accentcoach.PracticeViewModel
import app.accentcoach.Stage
import app.accentcoach.core.MyWord

/** The My words page: the words the learner added, or one of them open to practise. */
@Composable
fun Words(vm: PracticeViewModel, record: () -> Unit) {
    val word = vm.word
    if (word == null) WordList(vm) else WordPage(vm, word, record)
}

@Composable
private fun WordList(vm: PracticeViewModel) {
    var typed by rememberSaveable { mutableStateOf("") }
    val add = { vm.addWord(typed) { typed = "" } }

    Text(
        "Add the words you find hard to say. Each gets its native sounds, and you practise it like a sentence.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it },
            label = { Text("A word or short phrase") },
            singleLine = true,
            enabled = !vm.adding,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            modifier = Modifier.weight(1f),
        )
        Button(onClick = add, enabled = !vm.adding && typed.isNotBlank()) { Text("Add") }
    }
    if (vm.adding) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 3.dp)
            Spacer(Modifier.width(12.dp))
            Text("Working out how it's said…", style = MaterialTheme.typography.bodyMedium)
        }
    }
    vm.addError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

    if (vm.words.isEmpty()) {
        Text("No words yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Label(if (vm.words.size == 1) "1 WORD" else "${vm.words.size} WORDS")
    }
    vm.words.forEach { word ->
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth().clip(CardDefaults.shape).clickable { vm.open(word) },
        ) {
            Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(word.text, style = MaterialTheme.typography.titleMedium)
                    Text(ipa(word), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        word.best?.let { "Best: $it%" } ?: "Not tried yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { vm.removeWord(word) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Remove ${word.text}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** One of the learner's own words: hear it, say it, and see how each sound came out. */
@Composable
private fun WordPage(vm: PracticeViewModel, word: MyWord, record: () -> Unit) {
    BackRow("My words", onBack = { vm.back() }, trailing = word.best?.let { "Best: $it%" })
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "MY WORD",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
            )
            Text(word.text, style = MaterialTheme.typography.headlineMedium)
            Text(ipa(word), style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), color = MaterialTheme.colorScheme.primary)
            if (word.heard) {
                Text(
                    "This isn't in the app's dictionary, so its sounds are the ones heard in the phone's voice. " +
                        "If they look wrong, trust Listen over them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

    when (val s = vm.stage) {
        Stage.Idle -> RecordButton(recording = false, level = 0f, onClick = record, prompt = "Tap and say it aloud")
        Stage.Recording -> {
            RecordButton(recording = true, level = vm.level, onClick = vm::stopRecording)
            HeardBox(vm.live, "Listening… tap to stop when you've finished.")
        }
        Stage.Analysing -> {
            Analysing()
            HeardBox(vm.live, null)
        }
        is Stage.Done -> Result(vm, s.assessment, next = "My words", onNext = { vm.back() })
    }
}

private fun ipa(word: MyWord): String = "/" + word.words.joinToString(" ") { it.phonemes.joinToString("") } + "/"
