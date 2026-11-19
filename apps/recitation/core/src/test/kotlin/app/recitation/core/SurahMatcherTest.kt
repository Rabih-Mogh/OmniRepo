package app.recitation.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SurahMatcherTest {

    private fun number(title: String) = SurahMatcher.match(title)?.number

    @Test
    fun `every surah is found by its own name`() {
        for (surah in SURAHS) {
            assertEquals(surah.number, number("سورة ${surah.arabic}"), surah.arabic)
            assertEquals(surah.number, number("Surah ${surah.latin}"), surah.latin)
        }
    }

    @Test
    fun `titles as playlists really write them`() {
        assertEquals(1, number("001 | سورة الفاتحة | مشاري العفاسي | بدون إعلانات"))
        assertEquals(3, number("003 سورة ال عمران | مشاري العفاسي | بدون إعلانات"))
        // The name is misspelt, so the number at the front decides.
        assertEquals(7, number("007 سورة اأعراف | مشاري العفاسي | بدون إعلانات"))
        assertEquals(36, number("Surah Yaseen (Ya-Sin) Full | Beautiful Recitation"))
        assertEquals(2, number("Surat Al-Baqara - Sheikh Maher Al Muaiqly"))
        assertEquals(18, number("سُورَةُ الكَهْفِ كاملة - ماهر المعيقلي"))
        assertEquals(67, number("Mishary Rashid Alafasy - Surah 67"))
        assertEquals(50, number("سورة ق - عبد الباسط عبد الصمد"))
    }

    @Test
    fun `the reciter's name isn't taken for the surah when the surah is named`() {
        assertEquals(12, number("سورة يوسف - محمد صديق المنشاوي"))
        assertEquals(19, number("Muhammad Ayyub | Surah Maryam"))
    }

    @Test
    fun `no guess when nothing in the title points at a surah`() {
        assertNull(number("Beautiful Quran recitation, really emotional"))
        assertNull(number("تلاوة خاشعة من صلاة التراويح"))
    }
}
