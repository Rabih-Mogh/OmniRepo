package app.recitation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.recitation.R
import app.recitation.core.Family
import app.recitation.core.Rule

/** Opens the page of the rules of recitation, from wherever the colours are explained. */
val LocalTajweedRules = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * A family of rules as the scholars of recitation name it: in Arabic, in Latin letters for
 * readers of English, and a word on it (how many counts it is held).
 */
class FamilyName(val arabic: String, val latin: String, val note: Int)

/** A rule by its Arabic name, the same in Latin letters, what it is, and words from the Quran that show it. */
class RuleName(val arabic: String, val latin: String, val meaning: Int, val example: String)

fun Family.named(): FamilyName = when (this) {
    Family.NASAL -> FamilyName("الغُنَّة", "Ghunnah", R.string.tajweed_family_nasal)
    Family.MADD_LAZIM -> FamilyName("المدّ اللازم", "Madd lazim", R.string.tajweed_family_madd_lazim)
    Family.MADD_WAJIB -> FamilyName("المدّ الواجب", "Madd wajib", R.string.tajweed_family_madd_wajib)
    Family.MADD_JAIZ -> FamilyName("المدّ الجائز", "Madd ja’iz", R.string.tajweed_family_madd_jaiz)
    Family.MADD_TABII -> FamilyName("المدّ الطبيعي", "Madd tabi‘i", R.string.tajweed_family_madd_tabii)
    Family.ECHO -> FamilyName("القلقلة", "Qalqalah", R.string.tajweed_family_echo)
    Family.HEAVY -> FamilyName("التفخيم", "Tafkhim", R.string.tajweed_family_heavy)
    Family.QUIET -> FamilyName("الإدغام وما لا يُلفَظ", "Idgham and silent letters", R.string.tajweed_family_quiet)
}

fun Rule.named(): RuleName = when (this) {
    Rule.GHUNNAH -> RuleName("النون والميم المشدَّدتان", "Ghunnah of a doubled n or m", R.string.tajweed_ghunnah, "إِنَّ · ثُمَّ")
    Rule.IKHFA -> RuleName("الإخفاء الحقيقي", "Ikhfa’ haqiqi", R.string.tajweed_ikhfa, "مِن قَبْلِ · أَنتُمْ")
    Rule.IKHFA_SHAFAWI -> RuleName("الإخفاء الشفوي", "Ikhfa’ shafawi", R.string.tajweed_ikhfa_shafawi, "تَرْمِيهِم بِحِجَارَةٍ")
    Rule.IQLAB -> RuleName("الإقلاب", "Iqlab", R.string.tajweed_iqlab, "مِنۢ بَعْدِ · سَمِيعٌۢ بَصِيرٌ")
    Rule.IDGHAM_GHUNNAH -> RuleName("الإدغام بغُنَّة", "Idgham bi-ghunnah", R.string.tajweed_idgham_ghunnah, "مَن يَقُولُ · مِن وَلِىٍّ")
    Rule.IDGHAM_SHAFAWI -> RuleName("الإدغام الشفوي", "Idgham shafawi", R.string.tajweed_idgham_shafawi, "لَهُم مَّا يَشَآءُونَ")
    Rule.MADD_LAZIM -> RuleName("المدّ اللازم", "Madd lazim", R.string.tajweed_madd_lazim, "ٱلضَّآلِّينَ · الٓمٓ")
    Rule.MADD_MUTTASIL -> RuleName("المدّ الواجب المتّصل", "Madd wajib muttasil", R.string.tajweed_madd_muttasil, "ٱلسَّمَآءِ · سُوٓءَ")
    Rule.MADD_MUNFASIL -> RuleName("المدّ الجائز المنفصل", "Madd ja’iz munfasil", R.string.tajweed_madd_munfasil, "بِمَآ أُنزِلَ")
    Rule.MADD_SILAH_KUBRA -> RuleName("مدّ الصِّلة الكبرى", "Madd as-silah al-kubra", R.string.tajweed_madd_silah_kubra, "مَالَهُۥٓ أَخْلَدَهُۥ")
    Rule.MADD_ARID -> RuleName("المدّ العارض للسكون", "Madd ‘arid lis-sukun", R.string.tajweed_madd_arid, "ٱلْعَـٰلَمِينَ · نَسْتَعِينُ")
    Rule.MADD_LIN -> RuleName("مدّ اللِّين", "Madd al-lin", R.string.tajweed_madd_lin, "خَوْفٍ · ٱلْبَيْتِ")
    Rule.MADD_TABII -> RuleName("المدّ الطبيعي", "Madd tabi‘i", R.string.tajweed_madd_tabii, "مَـٰلِكِ · ذَٰلِكَ")
    Rule.MADD_SILAH_SUGHRA -> RuleName("مدّ الصِّلة الصغرى", "Madd as-silah as-sughra", R.string.tajweed_madd_silah_sughra, "إِنَّهُۥ كَانَ")
    Rule.MADD_IWAD -> RuleName("مدّ العِوَض", "Madd al-‘iwad", R.string.tajweed_madd_iwad, "عَلِيمًا حَكِيمًا")
    Rule.QALQALAH -> RuleName("القلقلة", "Qalqalah", R.string.tajweed_qalqalah, "يَخْلُقْ · ٱلْفَلَقِ")
    Rule.TAFKHIM_RA -> RuleName("تفخيم الراء", "Tafkhim ar-ra’", R.string.tajweed_tafkhim_ra, "رَبِّ · مَرْيَمَ")
    Rule.TAFKHIM_LAM -> RuleName("تفخيم لام لفظ الجلالة", "Tafkhim of the lam of Allah’s name", R.string.tajweed_tafkhim_lam, "قَالَ ٱللَّهُ")
    Rule.IDGHAM_BILA_GHUNNAH -> RuleName("الإدغام بغير غُنَّة", "Idgham bi-ghayr ghunnah", R.string.tajweed_idgham_bila_ghunnah, "مِن رَّبِّهِمْ · هُدًى لِّلْمُتَّقِينَ")
    Rule.IDGHAM_MUTAMATHILAYN -> RuleName("إدغام المتماثلَين", "Idgham al-mutamathilayn", R.string.tajweed_idgham_mutamathilayn, "ٱضْرِب بِّعَصَاكَ")
    Rule.IDGHAM_MUTAJANISAYN -> RuleName("إدغام المتجانسَين", "Idgham al-mutajanisayn", R.string.tajweed_idgham_mutajanisayn, "قَد تَّبَيَّنَ · ٱرْكَب مَّعَنَا")
    Rule.IDGHAM_MUTAQARIBAYN -> RuleName("إدغام المتقاربَين", "Idgham al-mutaqaribayn", R.string.tajweed_idgham_mutaqaribayn, "قُل رَّبِّ · نَخْلُقكُّم")
    Rule.LAM_SHAMSIYYAH -> RuleName("اللام الشمسية", "Lam shamsiyyah", R.string.tajweed_lam_shamsiyyah, "ٱلشَّمْسِ · ٱلنَّاسِ")
    Rule.HAMZAT_WASL -> RuleName("همزة الوصل", "Hamzat al-wasl", R.string.tajweed_hamzat_wasl, "بِسْمِ ٱللَّهِ")
    Rule.SILENT -> RuleName("ما لا يُلفَظ", "Silent letters", R.string.tajweed_silent, "قَالُوا۟ · أَنَا۠")
}

/**
 * Every rule of recitation the verses are coloured by, family by family: the colour, the
 * rule's Arabic name (with the same in Latin letters where the app is in English), what
 * the rule is, and words from the Quran that show it.
 */
@Composable
fun TajweedRulesPage(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
            Banner {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                    Text(
                        stringResource(R.string.tajweed_rules), Modifier.weight(1f).padding(vertical = 12.dp).semantics { heading() },
                        style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                    )
                    // As wide as the button at the other end, so the title is in the middle of the bar.
                    Spacer(Modifier.size(48.dp))
                }
            }
            TajweedRules(Modifier.weight(1f))
        }
    }
}

@Composable
fun TajweedRules(modifier: Modifier = Modifier) {
    val colours = LocalTajweed.current
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp)) {
        item {
            Text(stringResource(R.string.tajweed_intro), Modifier.padding(bottom = 4.dp), style = MaterialTheme.typography.bodyMedium, color = quiet)
        }
        for (family in Family.entries) {
            val name = family.named()
            item(key = "family-" + family.name) {
                Column(Modifier.padding(top = 20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(16.dp).background(colours.ink(family), CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.semantics(mergeDescendants = true) { heading() }) {
                            Text(name.arabic, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = colours.ink(family))
                            Text(
                                if (arabic) stringResource(name.note) else name.latin + " · " + stringResource(name.note),
                                style = MaterialTheme.typography.bodySmall, color = quiet,
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            for (rule in Rule.entries.filter { it.family == family }) {
                val each = rule.named()
                item(key = "rule-" + rule.name) {
                    Column(Modifier.fillMaxWidth().padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        // A family of one rule has the rule's name already.
                        if (each.arabic != name.arabic) {
                            Text(each.arabic, style = MaterialTheme.typography.titleMedium)
                            if (!arabic) Text(each.latin, style = MaterialTheme.typography.labelMedium, color = quiet)
                        }
                        Text(stringResource(each.meaning), style = MaterialTheme.typography.bodyMedium)
                        // Words from the Quran, always right to left and in the mushaf's own hand.
                        Text(
                            each.example, Modifier.fillMaxWidth().padding(top = 2.dp),
                            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = MadinahFont, fontSize = 22.sp, lineHeight = 40.sp, textDirection = TextDirection.Rtl),
                            textAlign = TextAlign.Start, color = colours.ink(family),
                        )
                    }
                }
            }
        }
        item {
            Text(stringResource(R.string.tajweed_plain), Modifier.padding(top = 24.dp), style = MaterialTheme.typography.bodySmall, color = quiet)
        }
    }
}
