package app.recitation.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The few Material icons the app needs that aren't in the small icon set it ships with. */
object AppIcons {
    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(addPathNodes(path), fill = SolidColor(Color.Black))
            .build()

    /** An icon drawn in lines with round ends: each part is a path and the width of its line, or null for a filled shape. */
    private fun lines(name: String, vararg parts: Pair<String, Float?>): ImageVector {
        val builder = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for ((path, width) in parts) {
            if (width == null) {
                builder.addPath(addPathNodes(path), fill = SolidColor(Color.Black))
            } else {
                builder.addPath(addPathNodes(path), stroke = SolidColor(Color.Black), strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            }
        }
        return builder.build()
    }

    // The letter ain as the verses' own font (Amiri Quran) draws it, for the icons made of letters.
    private const val AIN_SMALL = "M2.37 11.12Q2.74 10.96 3.25 11.25Q3.51 11.39 3.68 11.55Q3.86 11.7 3.96 11.85Q4.09 12.02 3.8 11.97Q2.27 11.66 1.17 13.03Q1.12 13.11 1.21 13.19Q2.07 13.98 3.23 13.86Q3.41 13.85 3.81 13.61Q4.54 13.17 4.95 12.92Q5.36 12.68 5.44 12.63Q5.66 12.52 5.55 12.84Q5.27 13.63 5.12 13.7Q4.63 13.92 4.02 14.3Q3.41 14.67 2.68 15.2Q1.08 16.36 0.85 18.4Q0.74 19.49 1.37 20.27Q2.4 21.56 5.95 21.43Q6.43 21.41 7.06 21.34Q7.7 21.26 8.48 21.13Q8.63 21.1 8.64 21.16Q8.7 21.35 7.29 22.05Q6.5 22.45 6.08 22.48Q4.27 22.67 3.1 22.41Q0.38 21.79 0.48 19.07Q0.58 16.33 2.14 14.81Q1.16 14.42 0.85 14Q0.73 13.84 0.74 13.61Q0.8 13.02 1.05 12.55Q1.62 11.49 2.37 11.12Z"
    private const val AIN_LARGE = "M12.21 1.07Q12.9 0.77 13.85 1.3Q14.34 1.58 14.68 1.87Q15.01 2.16 15.2 2.44Q15.43 2.76 14.9 2.67Q12.02 2.09 9.96 4.66Q9.87 4.8 10.03 4.96Q11.65 6.45 13.83 6.21Q14.15 6.19 14.92 5.75Q16.29 4.92 17.05 4.45Q17.82 3.99 17.98 3.9Q18.4 3.69 18.19 4.29Q17.65 5.77 17.38 5.91Q16.45 6.33 15.3 7.04Q14.15 7.74 12.79 8.74Q9.77 10.92 9.36 14.74Q9.15 16.78 10.33 18.27Q12.25 20.68 18.93 20.45Q19.83 20.4 21.03 20.26Q22.22 20.12 23.68 19.87Q23.96 19.82 23.98 19.94Q24.1 20.28 21.45 21.6Q19.97 22.35 19.18 22.42Q15.78 22.76 13.57 22.28Q8.48 21.12 8.66 16Q8.85 10.85 11.77 8Q9.94 7.26 9.36 6.47Q9.12 6.17 9.15 5.75Q9.26 4.64 9.73 3.76Q10.79 1.76 12.21 1.07Z"
    private const val AIN_OVER = "M9.44 0.55Q9.87 0.36 10.46 0.69Q10.77 0.86 10.98 1.05Q11.19 1.23 11.3 1.4Q11.45 1.6 11.11 1.54Q9.33 1.18 8.04 2.78Q7.98 2.87 8.09 2.97Q9.09 3.89 10.45 3.75Q10.65 3.73 11.13 3.46Q11.98 2.94 12.45 2.65Q12.93 2.36 13.03 2.31Q13.29 2.18 13.16 2.55Q12.83 3.48 12.66 3.56Q12.08 3.82 11.37 4.26Q10.65 4.7 9.8 5.32Q7.93 6.68 7.67 9.06Q7.54 10.33 8.27 11.25Q9.47 12.75 13.62 12.6Q14.19 12.58 14.93 12.49Q15.67 12.4 16.58 12.24Q16.75 12.21 16.77 12.29Q16.84 12.5 15.19 13.33Q14.27 13.79 13.78 13.83Q11.66 14.05 10.29 13.74Q7.12 13.02 7.23 9.84Q7.35 6.63 9.17 4.86Q8.03 4.4 7.67 3.91Q7.52 3.72 7.54 3.46Q7.61 2.77 7.9 2.22Q8.56 0.98 9.44 0.55Z"

    val Pause = icon("Pause", "M6,19h4V5H6v14zM14,5v14h4V5h-4z")

    /** A pen: the hand the verses are written in. */
    val Script = icon("Script", "M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z")

    /** A letter of one script beside a letter of another: a translation. */
    val Translate = icon("Translate", "M12.87,15.07l-2.54,-2.51 0.03,-0.03c1.74,-1.94 2.98,-4.17 3.71,-6.53L17,6L17,4h-7L10,2L8,2v2L1,4v1.99h11.17C11.5,7.92 10.44,9.75 9,11.35 8.07,10.32 7.3,9.19 6.69,8h-2c0.73,1.63 1.73,3.17 2.98,4.56l-5.09,5.02L4,19l5,-5 3.11,3.11 0.76,-2.04zM18.5,10h-2L12,22h2l1.12,-3h4.75L21,22h2l-4.5,-12zM15.88,17l1.62,-4.33L19.12,17h-3.24z")

    /** A page of writing: an exegesis. */
    val Exegesis = icon("Exegesis", "M19,5v14H5V5h14m0,-2H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2zM14,17H7v-2h7v2zM17,13H7v-2h10v2zM17,9H7V7h10v2z")

    val Delete = icon("Delete", "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z")
    val SkipNext = icon("SkipNext", "M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z")
    val SkipPrevious = icon("SkipPrevious", "M6,6h2v12H6zM9.5,12l8.5,6V6z")
    val Download = icon("Download", "M5,20h14v-2H5v2zM19,9h-4V3H9v6H5l7,7 7,-7z")
    val Add = icon("Add", "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")
    val Remove = icon("Remove", "M19,13H5v-2h14v2z")

    /** Light or dark: a disc, half of it filled. */
    val Appearance = lines("Appearance", "M12,3.5a8.5,8.5 0 1,0 0,17a8.5,8.5 0 1,0 0,-17Z" to 1.9f, "M12,3.5a8.5,8.5 0 0,1 0,17Z" to null)

    /** Text size: the letter ain small, and large beside it. */
    val TextSize = icon("TextSize", AIN_SMALL + AIN_LARGE)

    /** From one verse to another: what lies between two brackets. */
    val Range = lines(
        "Range",
        "M8,5H5V19H8" to 1.9f, "M16,5H19V19H16" to 1.9f,
        "M7.8,12a1.2,1.2 0 1,0 2.4,0a1.2,1.2 0 1,0 -2.4,0Z" to null, "M10.8,12a1.2,1.2 0 1,0 2.4,0a1.2,1.2 0 1,0 -2.4,0Z" to null, "M13.8,12a1.2,1.2 0 1,0 2.4,0a1.2,1.2 0 1,0 -2.4,0Z" to null,
    )
    val Close = icon("Close", "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z")
    val Retry = icon("Retry", "M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -7.99,8s3.57,8 7.99,8c3.73,0 6.84,-2.55 7.73,-6h-2.08c-0.82,2.33 -3.04,4 -5.65,4 -3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 4.22,1.78L13,11h7V4l-2.35,2.35z")
    val Play = icon("Play", "M8,5v14l11,-7z")

    /** Word meanings: an Arabic letter with a Latin one under it, as a word has its meaning under it. */
    val Meanings = lines("Meanings", AIN_OVER to null, "M9.2,17.7C10.5,16 15,15.7 15,18.5V23.3" to 2f, "M15,20H12C9.1,20 9.1,23.3 11.8,23.3C13.5,23.3 15,22.5 15,21.2" to 2f)

    /** Follow the recitation: lines of text, the one being recited heavier, and beside them an arrow for the way to it, up the page or down. */
    val FollowUp = lines("FollowUp", "M11,6H21M11,18H21" to 1.5f, "M12,12H20" to 4.2f, "M5,18V6.5M2,9.5L5,6.5L8,9.5" to 2f)
    val FollowDown = lines("FollowDown", "M11,6H21M11,18H21" to 1.5f, "M12,12H20" to 4.2f, "M5,6V17.5M2,14.5L5,17.5L8,14.5" to 2f)

    /** The Quran tab: an open book. */
    val Book = icon("Book", "M12,6.5Q9,4.4 3.5,5.2V18.2Q9,17.4 12,19.5Q15,17.4 20.5,18.2V5.2Q15,4.4 12,6.5ZM11.3,7.8V17.6Q9,16.3 5,16.6V6.7Q9,6.4 11.3,7.8ZM12.7,7.8Q15,6.4 19,6.7V16.6Q15,16.3 12.7,17.6Z")

    /** The Playlists tab: a list with a play mark. */
    val Playlists = icon("Playlists", "M3,10h11v2H3zM3,6h11v2H3zM3,14h7v2H3zM16,13v8l6,-4z")

    val Menu = icon("Menu", "M3,18h18v-2H3v2zM3,13h18v-2H3v2zM3,6v2h18V6H3z")
    val Search = icon(
        "Search",
        "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5z" +
            "M9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z",
    )

    /** The Stats tab: three bars of a chart. */
    val Stats = icon("Stats", "M5,9.2h3V19H5zM10.6,5h2.8v14h-2.8zM16.2,13H19v6h-2.8z")

    /** A pin left on a verse to mark the place reached. */
    val Pin = icon("Pin", "M16,9V4h1c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1H7C6.45,2 6,2.45 6,3s0.45,1 1,1h1v5c0,1.66 -1.34,3 -3,3v2h5.97v7l1,1 1,-1v-7H19v-2c-1.66,0 -3,-1.34 -3,-3z")

    /** A verse not in the collection yet: a bookmark in outline. */
    val BookmarkOutline = icon("BookmarkOutline", "M17,3H7c-1.1,0 -1.99,0.9 -1.99,2L5,21l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2zM17,18l-5,-2.18L7,18V5h10v13z")

    /** Not finished yet, or waiting its turn: an hourglass. */
    val Hourglass = icon("Hourglass", "M6,2v6h0.01L6,8.01 10,12l-4,4 0.01,0.01H6V22h12v-5.99h-0.01L18,16l-4,-4 4,-3.99 -0.01,-0.01H18V2H6zM16,16.5V20H8v-3.5l4,-4 4,4zM12,11.5l-4,-4V4h8v3.5l-4,4z")

    /** Sound: the bars of an equaliser. */
    val Sound = icon("Sound", "M7,18h2V6H7v12zM11,22h2V2h-2v20zM3,14h2v-4H3v4zM15,18h2V6h-2v12zM19,10v4h2v-4h-2z")

    /** Sound heard only in part: the first bars, then dots where the rest would be. */
    val SoundPart = icon("SoundPart", "M3,14h2v-4H3v4zM7,18h2V6H7v12zM11,11h2v2h-2zM15,11h2v2h-2zM19,11h2v2h-2z")

    /** The place reached: a bookmark. */
    val Bookmark = icon("Bookmark", "M17,3H7c-1.1,0 -1.99,0.9 -1.99,2L5,21l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2z")

    // What the app is at, each kind of work with its own picture (see Doing.kt).

    /** Listening to a recitation: an ear, with the sound coming to it. */
    val Ear = icon("Ear", "M17,20c-0.29,0 -0.56,-0.06 -0.76,-0.15 -0.71,-0.37 -1.21,-0.88 -1.71,-2.38 -0.51,-1.56 -1.47,-2.29 -2.39,-3 -0.79,-0.61 -1.61,-1.24 -2.32,-2.53C9.29,10.98 9,9.93 9,9c0,-2.8 2.2,-5 5,-5s5,2.2 5,5h2c0,-3.93 -3.07,-7 -7,-7S7,5.07 7,9c0,1.26 0.38,2.65 1.07,3.9 0.91,1.65 1.98,2.48 2.85,3.15 0.81,0.62 1.39,1.07 1.71,2.05 0.6,1.82 1.37,2.84 2.73,3.55 0.51,0.23 1.07,0.35 1.64,0.35 2.21,0 4,-1.79 4,-4h-2c0,1.1 -0.9,2 -2,2zM7.64,2.64L6.22,1.22C4.23,3.21 3,5.96 3,9s1.23,5.79 3.22,7.78l1.41,-1.41C6.01,13.74 5,11.49 5,9s1.01,-4.74 2.64,-6.36zM11.5,9c0,1.38 1.12,2.5 2.5,2.5s2.5,-1.12 2.5,-2.5 -1.12,-2.5 -2.5,-2.5 -2.5,1.12 -2.5,2.5z")

    /** The speech model: a chip. */
    val Model = icon("Model", "M15,9H9v6h6V9zM13,13h-2v-2h2v2zM21,11V9h-2V7c0,-1.1 -0.9,-2 -2,-2h-2V3h-2v2h-2V3H9v2H7c-1.1,0 -2,0.9 -2,2v2H3v2h2v2H3v2h2v2c0,1.1 0.9,2 2,2h2v2h2v-2h2v2h2v-2h2c1.1,0 2,-0.9 2,-2v-2h2v-2h-2v-2h2zM17,17H7V7h10v10z")

    /** The speech engine: a cog. */
    val Engine = icon("Engine", "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94 0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6 3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z")

    /** Finding the fastest way to run the engine: sliders being set. */
    val Tune = icon("Tune", "M3,17v2h6v-2H3zM3,5v2h10V5H3zM13,21v-2h8v-2h-8v-2h-2v6h2zM7,9v2H3v2h4v2h2V9H7zM21,13v-2H11v2h10zM15,9h2V7h4V5h-4V3h-2v6z")

    /** Matching the words to what was heard: bars of sound, each over its word. */
    val Match = icon("Match", "M5,3h2v8H5zM11,6h2v5h-2zM17,2h2v9h-2zM3,14h6v2.5H3zM10.5,14h3v2.5h-3zM15,14h6v2.5h-6zM3,19h18v2H3z")

    /** Checked and found good: a tick. */
    val Check = icon("Check", "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z")

    /** No connection: a cloud struck through. */
    val CloudOff = icon("CloudOff", "M19.35,10.04C18.67,6.59 15.64,4 12,4c-1.48,0 -2.85,0.43 -4.01,1.17l1.46,1.46C10.21,6.23 11.08,6 12,6c3.04,0 5.5,2.46 5.5,5.5v0.5H19c1.66,0 3,1.34 3,3 0,1.13 -0.64,2.11 -1.56,2.62l1.45,1.45C23.16,18.16 24,16.68 24,15c0,-2.64 -2.05,-4.78 -4.65,-4.96zM3,5.27l2.75,2.74C2.56,8.15 0,10.77 0,14c0,3.31 2.69,6 6,6h11.73l2,2L21,20.73 4.27,4 3,5.27zM7.73,10l8,8H6c-2.21,0 -4,-1.79 -4,-4s1.79,-4 4,-4h1.73z")

    /** Refused: a circle struck through. */
    val Blocked = icon("Blocked", "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM4,12c0,-4.42 3.58,-8 8,-8 1.85,0 3.55,0.63 4.9,1.69L5.69,16.9C4.63,15.55 4,13.85 4,12zM12,20c-1.85,0 -3.55,-0.63 -4.9,-1.69L18.31,7.1C19.37,8.45 20,10.15 20,12c0,4.42 -3.58,8 -8,8z")

    // Reciting to the phone, and the voice exercises.

    /** The microphone: recite, and be heard. */
    val Mic = icon("Mic", "M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3zM17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11H5c0,3.41 2.72,6.23 6,6.72V21h2v-3.28c3.28,-0.48 6,-3.3 6,-6.72h-1.7z")

    /** Stop listening: a square. */
    val Stop = icon("Stop", "M6,6h12v12H6z")

    /** Breath: air moving. */
    val Breath = lines("Breath", "M3,9H14a2.5,2.5 0 1,0 -2.5,-2.5" to 1.8f, "M3,13H18a2.5,2.5 0 1,1 -2.5,2.5" to 1.8f, "M3,17H10" to 1.8f)

    /** A note held steady: a level line between two posts. */
    val Steady = lines("Steady", "M3,12H21" to 2.4f, "M3,8.5V15.5M21,8.5V15.5" to 1.6f)

    /** The reach of a voice, low to high: an arrow with a head at each end. */
    val Span = lines("Span", "M12,3.5V20.5M8,7.5L12,3.5L16,7.5M8,16.5L12,20.5L16,16.5" to 2f)

    /** Something is wrong: a warning triangle. */
    val Warning = icon("Warning", "M1,21h22L12,2 1,21zM13,18h-2v-2h2v2zM13,14h-2v-4h2v4z")
}
