package app.recitation.ui

import android.content.res.Configuration
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.recitation.R
import java.util.Locale

/**
 * The language the lessons and the corrections are in, where the reader chose one for
 * them: "ar" or "en", whatever the language of the phone and of the rest of the app; null
 * until they choose, when the lessons follow the app. [set] chooses one.
 */
class Teaching(val language: String? = null, val set: (String) -> Unit = {})

val LocalTeaching = staticCompositionLocalOf { Teaching() }

/**
 * What is taught: a lesson, a correction, an exercise. Inside it the app's words are in the
 * language chosen for teaching (see [Teaching]), and laid out from the side that language
 * reads from. Where none was chosen, or it is the app's own, nothing changes.
 */
@Composable
fun Taught(content: @Composable () -> Unit) {
    val language = LocalTeaching.current.language
    val configuration = LocalConfiguration.current
    if (language == null || configuration.locales[0].language == language) {
        content()
        return
    }
    val context = LocalContext.current
    // The app's own words in that language: the same strings a phone set to it would show.
    val taught = remember(context, configuration, language) {
        val config = Configuration(configuration).apply { setLocale(Locale(language)) }
        context.createConfigurationContext(config) to config
    }
    CompositionLocalProvider(
        LocalContext provides taught.first, LocalConfiguration provides taught.second,
        LocalLayoutDirection provides if (language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr,
        content = content,
    )
}

/**
 * Turns what is taught to the other language: it reads العربية while the teaching is in
 * English, and English while it is in Arabic. It is drawn in the colour of whatever it
 * stands on: a top bar, or a card.
 */
@Composable
fun TeachSwitch(modifier: Modifier = Modifier) {
    val teaching = LocalTeaching.current
    // What the teaching is in now: the language chosen for it, or the app's.
    val arabic = (teaching.language ?: LocalConfiguration.current.locales[0].language) == "ar"
    val ink = LocalContentColor.current
    val label = stringResource(if (arabic) R.string.teach_switch_en else R.string.teach_switch_ar)
    Text(
        if (arabic) "English" else "العربية",
        modifier.clip(RoundedCornerShape(50)).border(1.dp, ink.copy(alpha = 0.45f), RoundedCornerShape(50))
            .clickable(role = Role.Button) { teaching.set(if (arabic) "en" else "ar") }
            .padding(horizontal = 12.dp, vertical = 5.dp).semantics { contentDescription = label }.testTag("teach-switch"),
        style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1,
    )
}
