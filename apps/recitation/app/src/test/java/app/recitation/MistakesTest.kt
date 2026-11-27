package app.recitation

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recitation.core.Awaited
import app.recitation.core.Correction
import app.recitation.core.Listened
import app.recitation.core.Drill
import app.recitation.core.Fault
import app.recitation.core.Madd
import app.recitation.core.Topic
import app.recitation.core.WordRecited
import app.recitation.data.Mistakes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/** What the reader has been corrected on: kept for the exercises that go over it, and taken off once it is put right. */
@RunWith(AndroidJUnit4::class)
class MistakesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun file() = File(folder.root, "mistakes.json")
    /** A word that was said, with the ghunnah and the madd of these tests listened for in it. */
    private fun word(index: Int, vararg faults: Correction) = WordRecited(
        index, false, faults.toList(), emptyList(),
        listened = listOf(Awaited(0, 4, Listened.GHUNNAH, index, 4, symbol = 'ن'), Awaited(4, 8, Listened.MADD, index, 4, Madd.MUTTASIL, 'ا')),
    )
    private val ghunnah = Correction(Fault.GHUNNAH_SHORT, clear = false, wanted = "ن")
    private val madd = Correction(Fault.MADD_SHORT, clear = false, madd = Madd.MUTTASIL, wantedCounts = 4, heardCounts = 2)

    @Test
    fun whatWasCorrectedIsKeptByTopicAndTakenOffWhenItIsPutRight() {
        val mistakes = Mistakes(file())
        mistakes.note(78, 2, listOf(word(0), word(1, ghunnah)))
        mistakes.note(78, 5, listOf(word(0, madd), word(1, ghunnah)))
        var tally = mistakes.tally.value
        // The latest first; the topic corrected most often is the weakest.
        assertEquals(listOf(Drill(78, 5, Topic.MADD_MUTTASIL), Drill(78, 5, Topic.GHUNNAH), Drill(78, 2, Topic.GHUNNAH)), tally.open)
        assertEquals(listOf(Topic.GHUNNAH to 2, Topic.MADD_MUTTASIL to 1), tally.weakest)
        // 78:5 recited again with its madd sound: only its ghunnah is still to go over. How often it came up stays.
        mistakes.note(78, 5, listOf(word(0), word(1, ghunnah)))
        tally = mistakes.tally.value
        assertEquals(listOf(Drill(78, 5, Topic.GHUNNAH), Drill(78, 2, Topic.GHUNNAH)), tally.open)
        assertEquals(3, tally.counts[Topic.GHUNNAH])
        // And 78:2 recited soundly: it is off the list.
        mistakes.note(78, 2, listOf(word(0), word(1)))
        assertEquals(listOf(Drill(78, 5, Topic.GHUNNAH)), mistakes.tally.value.open)
    }

    @Test
    fun whatWasCorrectedInAWordStaysToGoOverWhenTheWordIsNotSaidTheNextTime() {
        val mistakes = Mistakes(file())
        mistakes.note(78, 2, listOf(word(0), word(1, ghunnah)))
        // Recited again, but only its first word: the ghunnah wasn't put right, it wasn't said.
        val unsaid = WordRecited(1, false, listOf(Correction(Fault.WORD_MISSING, clear = true)), emptyList())
        mistakes.note(78, 2, listOf(WordRecited(0, false, emptyList(), emptyList()), unsaid))
        assertEquals(listOf(Drill(78, 2, Topic.GHUNNAH)), mistakes.tally.value.open)
        // And a word not said isn't counted against any rule.
        assertEquals(mapOf(Topic.GHUNNAH to 1), mistakes.tally.value.counts)
    }

    @Test
    fun aLetterSaidForAnotherIsKeptByItsPairAndMistakesInTheWordingAreNot() {
        val mistakes = Mistakes(file())
        mistakes.note(1, 6, listOf(word(1, Correction(Fault.LETTER, clear = true, wanted = "ص", heard = "س")), word(2, Correction(Fault.VOWEL, clear = true))))
        mistakes.note(1, 7, listOf(word(0, Correction(Fault.LETTER, clear = true, wanted = "ص", heard = "ك"))))
        assertEquals(listOf(Drill(1, 6, Topic.LETTERS, "صس")), mistakes.tally.value.open)
    }

    @Test
    fun aLessonLearnedStaysLearned() {
        val mistakes = Mistakes(file())
        mistakes.learned(Topic.IKHFA)
        mistakes.learned(Topic.LETTERS, "صس")
        mistakes.note(78, 2, listOf(word(1, ghunnah)))
        val again = Mistakes(file()).tally.value
        assertEquals(true, again.learned(Topic.IKHFA))
        assertEquals(true, again.learned(Topic.LETTERS, "صس"))
        // Another pair of letters is another lesson.
        assertEquals(false, again.learned(Topic.LETTERS, "قك"))
        assertEquals(false, again.learned(Topic.GHUNNAH))
    }

    @Test
    fun aVerseRecitedForOneRuleIsNotedForThatRuleAlone() {
        val mistakes = Mistakes(file())
        // 78:5 recited freely: its madd and its ghunnah are both to go over.
        mistakes.note(78, 5, listOf(word(0, madd), word(1, ghunnah)))
        // Recited again in an exercise on the ghunnah, with the ghunnah sound and another madd held short:
        // the ghunnah is off the list; the madd is neither counted again nor taken as put right.
        mistakes.note(78, 5, listOf(word(0, madd), word(1)), only = Drill(78, 5, Topic.GHUNNAH))
        var tally = mistakes.tally.value
        assertEquals(listOf(Drill(78, 5, Topic.MADD_MUTTASIL)), tally.open)
        assertEquals(mapOf(Topic.MADD_MUTTASIL to 1, Topic.GHUNNAH to 1), tally.counts)
        // And in an exercise on the ghunnah where the ghunnah is cut short, only that is noted.
        mistakes.note(78, 6, listOf(word(0, madd), word(1, ghunnah)), only = Drill(78, 6, Topic.GHUNNAH))
        tally = mistakes.tally.value
        assertEquals(listOf(Drill(78, 6, Topic.GHUNNAH), Drill(78, 5, Topic.MADD_MUTTASIL)), tally.open)
        assertEquals(mapOf(Topic.MADD_MUTTASIL to 1, Topic.GHUNNAH to 2), tally.counts)
    }

    @Test
    fun aLessonIsLearnedOnceThreeGoesInFourOfItsExerciseAreSound() {
        val mistakes = Mistakes(file())
        // Five of eight: not yet. The best so far is kept.
        mistakes.scored(Topic.IKHFA, "", 5, 8)
        assertEquals(false, mistakes.tally.value.learned(Topic.IKHFA))
        assertEquals(5 to 8, mistakes.tally.value.best(Topic.IKHFA))
        // A worse go doesn't take it away.
        mistakes.scored(Topic.IKHFA, "", 2, 8)
        assertEquals(5 to 8, mistakes.tally.value.best(Topic.IKHFA))
        // Six of eight: learned, and it stays learned and is there next time.
        mistakes.scored(Topic.IKHFA, "", 6, 8)
        mistakes.scored(Topic.IKHFA, "", 1, 8)
        val again = Mistakes(file()).tally.value
        assertEquals(true, again.learned(Topic.IKHFA))
        assertEquals(6 to 8, again.best(Topic.IKHFA))
        // A rule with few verses to practise on: three of three.
        mistakes.scored(Topic.MADD_LAZIM, "", 2, 3)
        assertEquals(false, mistakes.tally.value.learned(Topic.MADD_LAZIM))
        mistakes.scored(Topic.MADD_LAZIM, "", 3, 3)
        assertEquals(true, mistakes.tally.value.learned(Topic.MADD_LAZIM))
        // Nothing recited yet is no score at all.
        assertEquals(null, mistakes.tally.value.best(Topic.QALQALAH))
    }

    @Test
    fun itIsStillThereNextTime() {
        Mistakes(file()).note(78, 2, listOf(word(1, ghunnah)))
        val again = Mistakes(file()).tally.value
        assertEquals(listOf(Drill(78, 2, Topic.GHUNNAH)), again.open)
        assertEquals(mapOf(Topic.GHUNNAH to 1), again.counts)
    }
}
