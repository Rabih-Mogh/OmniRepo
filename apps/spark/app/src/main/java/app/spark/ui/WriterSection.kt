package app.spark.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.spark.R
import app.spark.data.Writer

/** Where a free key is made. */
private const val KEYS = "https://aistudio.google.com/api-keys"

/**
 * Reels written on the phone: how to get a key and where to paste it, and once there is
 * one, what the writer is doing and a way to set it going by hand.
 */
@Composable
fun WriterSection() {
    val context = LocalContext.current
    val key by Writer.key.collectAsStateWithLifecycle()
    val state by Writer.state.collectAsStateWithLifecycle()
    var noBrowser by remember { mutableStateOf(false) }
    val note = MaterialTheme.typography.bodyMedium
    val faint = MaterialTheme.colorScheme.onSurfaceVariant

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.writer_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.writer_about), style = note, color = faint)

        val saved = key
        if (saved == null) {
            var text by rememberSaveable { mutableStateOf("") }
            Text(stringResource(R.string.writer_steps), style = note)
            OutlinedButton(onClick = { noBrowser = !openPage(context, Uri.parse(KEYS)) }) { Text(stringResource(R.string.writer_open)) }
            if (noBrowser) Text(stringResource(R.string.wishes_send_failed), style = note, color = faint)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // A key is letters and digits from the left, whatever the phone's language, and nothing for a keyboard to learn.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(
                        text,
                        { text = it },
                        Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.writer_key_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                    )
                }
                Button(onClick = { Writer.key(context, text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.writer_key_save)) }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.writer_key_saved, saved.takeLast(4)), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = { Writer.key(context, "") }) { Text(stringResource(R.string.writer_key_remove)) }
            }
            val written = remember(state) { Writer.written(context) }
            Button(onClick = { Writer.start(context, quiet = false) }, enabled = state !is Writer.State.Working && !written) {
                Text(stringResource(R.string.writer_now))
            }
            val says = when (val now = state) {
                Writer.State.Idle -> stringResource(if (written) R.string.writer_written else R.string.writer_waiting)
                Writer.State.Checking -> stringResource(R.string.writer_checking)
                Writer.State.Works -> stringResource(R.string.writer_works)
                is Writer.State.Working -> stringResource(
                    R.string.writer_working,
                    now.step,
                    Writer.STEPS,
                    stringResource(listOf(R.string.writer_step_news, R.string.writer_step_reels, R.string.writer_step_lessons)[(now.step - 1).coerceIn(0, 2)]),
                )
                is Writer.State.Done -> stringResource(R.string.writer_done, now.reels, now.lessons)
                is Writer.State.Failed -> stringResource(
                    when (now.why) {
                        Writer.Why.OFFLINE -> R.string.writer_failed_offline
                        Writer.Why.KEY -> R.string.writer_failed_key
                        Writer.Why.QUOTA -> R.string.writer_failed_quota
                        Writer.Why.EMPTY -> R.string.writer_failed_empty
                        Writer.Why.OTHER -> R.string.writer_failed_other
                    },
                )
            }
            Text(says, style = note, color = faint)
            // Google's own words for what went wrong, in case they say more.
            (state as? Writer.State.Failed)?.said?.let {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(it, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = faint)
                }
            }
        }
        Text(stringResource(R.string.writer_privacy), style = note, color = faint)
    }
}
