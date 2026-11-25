package app.spark.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import app.spark.R

// Every role is set: anything left out falls back to Material's purple baseline.
// Warm orange for what can be tapped, indigo beside it.
private val Day = lightColorScheme(
    primary = Color(0xFF9A4600),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBC8),
    onPrimaryContainer = Color(0xFF321200),
    inversePrimary = Color(0xFFFFB68B),
    secondary = Color(0xFF765848),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBC8),
    onSecondaryContainer = Color(0xFF2B160A),
    tertiary = Color(0xFF4D5BA8),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDEE0FF),
    onTertiaryContainer = Color(0xFF02105E),
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF221A15),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF221A15),
    surfaceVariant = Color(0xFFF4DED3),
    onSurfaceVariant = Color(0xFF52443B),
    surfaceTint = Color(0xFF9A4600),
    inverseSurface = Color(0xFF382E29),
    inverseOnSurface = Color(0xFFFFEDE5),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF85736A),
    outlineVariant = Color(0xFFD7C2B8),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFF8F5),
    surfaceDim = Color(0xFFE8D7CF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1EA),
    surfaceContainer = Color(0xFFFCEBE2),
    surfaceContainerHigh = Color(0xFFF6E5DC),
    surfaceContainerHighest = Color(0xFFF0DFD7),
)

private val Night = darkColorScheme(
    primary = Color(0xFFFFB68B),
    onPrimary = Color(0xFF522300),
    primaryContainer = Color(0xFF753400),
    onPrimaryContainer = Color(0xFFFFDBC8),
    inversePrimary = Color(0xFF9A4600),
    secondary = Color(0xFFE6BEAB),
    onSecondary = Color(0xFF432A1D),
    secondaryContainer = Color(0xFF5C4032),
    onSecondaryContainer = Color(0xFFFFDBC8),
    tertiary = Color(0xFFBBC3FF),
    onTertiary = Color(0xFF1C2A77),
    tertiaryContainer = Color(0xFF35438F),
    onTertiaryContainer = Color(0xFFDEE0FF),
    background = Color(0xFF19120D),
    onBackground = Color(0xFFF0DFD7),
    surface = Color(0xFF19120D),
    onSurface = Color(0xFFF0DFD7),
    surfaceVariant = Color(0xFF52443B),
    onSurfaceVariant = Color(0xFFD7C2B8),
    surfaceTint = Color(0xFFFFB68B),
    inverseSurface = Color(0xFFF0DFD7),
    inverseOnSurface = Color(0xFF382E29),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF9F8D83),
    outlineVariant = Color(0xFF52443B),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF413731),
    surfaceDim = Color(0xFF19120D),
    surfaceContainerLowest = Color(0xFF140D08),
    surfaceContainerLow = Color(0xFF221A15),
    surfaceContainer = Color(0xFF261E19),
    surfaceContainerHigh = Color(0xFF312823),
    surfaceContainerHighest = Color(0xFF3D332D),
)

/** Light or dark as the phone is set. */
@Composable
fun SparkTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Night else Day, content = content)
}

/** The green of a right answer: the text, and the box it sits in. */
object Right {
    val ink: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF8BD99A) else Color(0xFF1E6B34)
    val box: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF1B3D25) else Color(0xFFD3F0D8)
}

/** A subject's colours on a reel, which is always dark: the top of its background, and what is lit against it. */
data class Look(val deep: Color, val glow: Color)

fun look(topic: String): Look = when (topic) {
    "maths" -> Look(Color(0xFF2A2F92), Color(0xFFAEB7FF))
    "science" -> Look(Color(0xFF0B5F58), Color(0xFF7FE5D5))
    "computing" -> Look(Color(0xFF5B2A9E), Color(0xFFD3B2FF))
    "ideas" -> Look(Color(0xFF9B3612), Color(0xFFFFC08C))
    // A subject the app wasn't built knowing gets a colour of its own, the same one every time.
    else -> ((topic.hashCode() % 360 + 360) % 360).toFloat().let { Look(Color.hsv(it, 0.65f, 0.5f), Color.hsv(it, 0.35f, 1f)) }
}

@Composable
fun topicName(topic: String): String = when (topic) {
    "maths" -> stringResource(R.string.topic_maths)
    "science" -> stringResource(R.string.topic_science)
    "computing" -> stringResource(R.string.topic_computing)
    "ideas" -> stringResource(R.string.topic_ideas)
    else -> topic.replaceFirstChar { it.titlecase() }
}
