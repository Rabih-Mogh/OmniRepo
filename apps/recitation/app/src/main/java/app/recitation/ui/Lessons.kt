package app.recitation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.recitation.R
import app.recitation.core.Drills
import app.recitation.core.Rule
import app.recitation.core.Topic
import app.recitation.data.Hear

/**
 * What a lesson says: when the thing comes (none for letters, which come everywhere), how
 * it is said, the slip most often made with it, and words of the Quran that show it. All
 * but the words are strings of the app, in its language.
 */
class Lesson(val whenItComes: Int?, val how: Int, val slip: Int?, val examples: String)

/**
 * The lesson on [topic]; for letters, on the pair [letters]. What is taught is what the
 * books of tajweed agree on for the reading of Hafs from Asim: the rule, the letters that
 * bring it about, how it is made, and its length in counts.
 */
fun lessonOf(topic: Topic, letters: String = ""): Lesson = when (topic) {
    Topic.GHUNNAH -> Lesson(R.string.lesson_ghunnah_when, R.string.lesson_ghunnah_how, R.string.lesson_ghunnah_slip, "إِنَّ · ثُمَّ · ٱلنَّاسِ")
    Topic.IKHFA -> Lesson(R.string.lesson_ikhfa_when, R.string.lesson_ikhfa_how, R.string.lesson_ikhfa_slip, "مِن قَبْلِ · أَنتُمْ · عَلِيمًا قَدِيرًا")
    Topic.IQLAB -> Lesson(R.string.lesson_iqlab_when, R.string.lesson_iqlab_how, R.string.lesson_iqlab_slip, "مِنۢ بَعْدِ · سَمِيعٌۢ بَصِيرٌ · أَنۢبِئْهُم")
    Topic.IDGHAM -> Lesson(R.string.lesson_idgham_when, R.string.lesson_idgham_how, R.string.lesson_idgham_slip, "مَن يَقُولُ · مِن وَلِىٍّ · مِن رَّبِّهِمْ · هُدًى لِّلْمُتَّقِينَ")
    Topic.QALQALAH -> Lesson(R.string.lesson_qalqalah_when, R.string.lesson_qalqalah_how, R.string.lesson_qalqalah_slip, "يَخْلُقْ · ٱلْفَلَقِ · أَحَدٌ · وَتَبَّ")
    Topic.MADD_NATURAL -> Lesson(R.string.lesson_madd_natural_when, R.string.lesson_madd_natural_how, R.string.lesson_madd_natural_slip, "قَالَ · يَقُولُ · قِيلَ")
    Topic.MADD_MUNFASIL -> Lesson(R.string.lesson_madd_munfasil_when, R.string.lesson_madd_munfasil_how, R.string.lesson_madd_munfasil_slip, "بِمَآ أُنزِلَ · قُوٓا۟ أَنفُسَكُمْ · إِنَّآ أَعْطَيْنَـٰكَ")
    Topic.MADD_MUTTASIL -> Lesson(R.string.lesson_madd_muttasil_when, R.string.lesson_madd_muttasil_how, R.string.lesson_madd_muttasil_slip, "ٱلسَّمَآءِ · جَآءَ · سُوٓءَ")
    Topic.MADD_LAZIM -> Lesson(R.string.lesson_madd_lazim_when, R.string.lesson_madd_lazim_how, R.string.lesson_madd_lazim_slip, "ٱلضَّآلِّينَ · ٱلْحَآقَّةُ · الٓمٓ")
    Topic.MADD_ARID -> Lesson(R.string.lesson_madd_arid_when, R.string.lesson_madd_arid_how, R.string.lesson_madd_arid_slip, "ٱلْعَـٰلَمِينَ · نَسْتَعِينُ · خَوْفٍ")
    Topic.STOP -> Lesson(R.string.lesson_stop_when, R.string.lesson_stop_how, R.string.lesson_stop_slip, "عَلِيمًا ← عَلِيمَا · رَحْمَةً ← رَحْمَهْ · ٱلْفَلَقِ ← ٱلْفَلَقْ")
    Topic.LETTERS -> Drills.PAIRS.indexOf(letters).coerceAtLeast(0).let { pair -> Lesson(null, PAIR_LESSONS[pair], null, PAIR_WORDS[pair]) }
}

private val PAIR_LESSONS = intArrayOf(
    R.string.lesson_pair_1, R.string.lesson_pair_2, R.string.lesson_pair_3, R.string.lesson_pair_4, R.string.lesson_pair_5, R.string.lesson_pair_6, R.string.lesson_pair_7,
)

/** Words of the Quran with each letter of a pair, in the pairs' order. */
private val PAIR_WORDS = listOf(
    "ٱلصِّرَٰطَ · نَسْتَعِينُ", "ٱلضَّآلِّينَ · ٱلدِّينِ", "ٱلصِّرَٰطَ · أَنْعَمْتَ", "ٱلظَّـٰلِمِينَ · ٱلَّذِينَ", "ٱلْمُسْتَقِيمَ · مَـٰلِكِ", "ٱلرَّحِيمِ · ٱهْدِنَا", "ٱلْعَـٰلَمِينَ · إِيَّاكَ",
)

/** The colour a topic has: its rule's, in the legend of the colours; none for a stop and for letters. */
@Composable
fun Topic.ink(): Color? = rule()?.let { LocalTajweed.current.ink(it.family) }

/**
 * A lesson: the rule by name with its colour; words from the reader's own recordings that
 * show it, to hear it in their reciter's voice before anything is said about it
 * ([hears]); then when it comes, how it is said, the slip most often made, and words of
 * the Quran that show it. At its foot, the way to practising it.
 * [learned] is whether an exercise on it has gone well already.
 */
@Composable
fun LessonContent(topic: Topic, letters: String, hears: List<Hear>, learned: Boolean, onHear: (Hear) -> Unit, onPractise: () -> Unit, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    val lesson = lessonOf(topic, letters)
    val rule: Rule? = topic.rule()
    Column(Modifier.fillMaxSize()) {
        Banner {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(if (topic == Topic.LETTERS) stringResource(R.string.practice_pairs_title) else topic.named(letters), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    // Under the name in Latin letters, the rule's own name.
                    if (!arabic && rule != null) Text(rule.named().arabic, style = MaterialTheme.typography.bodySmall, color = LocalBanner.current.quiet, maxLines = 1)
                }
                if (learned) Icon(AppIcons.Check, stringResource(R.string.lesson_learned), Modifier.padding(end = 12.dp).size(22.dp))
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp).testTag("lesson"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // The rule by its own name, in the colour it has on the page.
            if (rule != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    topic.ink()?.let { Box(Modifier.size(14.dp).background(it, CircleShape)) }
                    Text(rule.named().arabic, fontFamily = QuranFont, fontSize = 26.sp, lineHeight = 44.sp, color = topic.ink() ?: colors.primary)
                }
            }
            if (topic == Topic.LETTERS) {
                // The two letters, large, and where each is made.
                val tips = stringArrayResource(R.array.letter_tips)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally)) {
                        for (letter in letters) Text(letter.toString(), fontFamily = QuranFont, fontSize = 64.sp, lineHeight = 96.sp, color = colors.primary)
                    }
                }
                for (letter in letters) TIP_LETTERS.indexOf(letter).takeIf { it >= 0 }?.let { Text(tips[it], style = MaterialTheme.typography.bodyLarge) }
            }
            // First, how it sounds: words from the reader's own recordings that show it, in their
            // reciter's voice, each played at a tap. Nothing is played until one is tapped.
            Text(stringResource(R.string.lesson_hear), Modifier.semantics { heading() }, style = MaterialTheme.typography.labelLarge, color = colors.primary)
            if (hears.isEmpty()) {
                Text(stringResource(R.string.lesson_hear_none), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            } else {
                for (hear in hears) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surfaceContainerLow).clickable(role = Role.Button, onClickLabel = stringResource(R.string.spot_play)) { onHear(hear) }
                            .padding(horizontal = 14.dp, vertical = 6.dp).testTag("lesson-hear"),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(AppIcons.Sound, null, Modifier.size(22.dp), tint = colors.primary)
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            Text(hear.words, Modifier.weight(1f), fontFamily = QuranFont, fontSize = 26.sp, lineHeight = 54.sp, color = colors.onSurface)
                        }
                    }
                }
            }
            lesson.whenItComes?.let { Part(stringResource(R.string.lesson_when), stringResource(it)) }
            Part(stringResource(if (topic == Topic.LETTERS) R.string.lesson_differ else R.string.lesson_how), stringResource(lesson.how))
            lesson.slip?.let { Part(stringResource(R.string.lesson_slip), stringResource(it), mark = colors.error) }
            // Words of the Quran that show it, from the right whatever the phone's language.
            Text(stringResource(R.string.lesson_examples), Modifier.semantics { heading() }, style = MaterialTheme.typography.labelLarge, color = colors.primary)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Text(lesson.examples, Modifier.fillMaxWidth(), fontFamily = QuranFont, fontSize = 28.sp, lineHeight = 58.sp, color = colors.onSurface)
            }
        }
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(12.dp)) {
            ThemeButton(stringResource(R.string.lesson_practise), onPractise, Modifier.fillMaxWidth().testTag("lesson-practise"), icon = AppIcons.Mic, filled = true)
        }
    }
}

/** A part of a lesson: its heading and what it says. */
@Composable
private fun Part(heading: String, text: String, mark: Color = MaterialTheme.colorScheme.primary) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(heading, Modifier.semantics { heading() }, style = MaterialTheme.typography.labelLarge, color = mark)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** A lesson, with its words played from the reader's recordings: each when it is tapped, and none before. */
@Composable
fun LessonScreen(topic: Topic, letters: String, hears: List<Hear>, learned: Boolean, onPractise: () -> Unit, onBack: () -> Unit) {
    val clip = rememberClip()
    LessonContent(topic, letters, hears, learned, onHear = { clip.play(it.sound, it.fromMs, it.untilMs) }, onPractise = onPractise, onBack = onBack)
}
