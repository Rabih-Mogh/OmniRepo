package app.recitation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.recitation.R

/**
 * A menu's panel, which stands beside the page rather than over it: opening it slides the
 * whole [page] aside by the panel's width, and the panel comes in behind it from the
 * screen's edge. It comes from the side reading starts at, or from the other when
 * [fromEnd]: the side its button is on. At its head are [title] and a cross that closes
 * it; tapping what is left of the page closes it too, and so does Back.
 *
 * The page's own top bar goes aside with the page, so the bars' colour carries on across
 * the panel's top, behind the phone's status bar, where the phone's icons are drawn light.
 */
@Composable
fun SidePanel(
    open: Boolean,
    onClose: () -> Unit,
    title: String,
    fromEnd: Boolean = false,
    tag: String = "menu",
    panel: @Composable ColumnScope.() -> Unit,
    page: @Composable () -> Unit,
) {
    BackHandler(enabled = open, onBack = onClose)
    val shown by animateFloatAsState(if (open) 1f else 0f, tween(280), label = "panel")
    // Which edge of the screen it comes from, whichever way the language reads.
    val fromLeft = fromEnd == (LocalLayoutDirection.current == LayoutDirection.Rtl)
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // A strip of the page stays in sight, to tap to close.
        val width = minOf(maxWidth - 56.dp, 340.dp)
        val reach = with(LocalDensity.current) { width.toPx() }
        Box(Modifier.fillMaxSize().graphicsLayer { translationX = (if (fromLeft) reach else -reach) * shown }) {
            page()
            if (open) {
                val close = stringResource(R.string.close)
                Box(Modifier.matchParentSize().clickable(remember { MutableInteractionSource() }, indication = null, onClickLabel = close, onClick = onClose))
            }
        }
        // Only there while it is open or on its way: closed, nothing of it is laid out.
        if (shown > 0f) {
            val edge = colors.outlineVariant
            Column(
                Modifier.align(if (fromLeft) AbsoluteAlignment.TopLeft else AbsoluteAlignment.TopRight).width(width).fillMaxHeight()
                    .graphicsLayer { translationX = (if (fromLeft) -reach else reach) * (1f - shown) }
                    .background(colors.surface)
                    // A hairline down the side the page is on.
                    .drawBehind {
                        val x = if (fromLeft) size.width - 0.5.dp.toPx() else 0.5.dp.toPx()
                        drawLine(edge, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                    }
                    .testTag(tag),
            ) {
                Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(LocalBanner.current.background))
                // What is in it is written in the page's ink, by day and by night.
                CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
                Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(if (fromLeft) WindowInsetsSides.Left else WindowInsetsSides.Right))) {
                    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge, color = colors.primary, maxLines = 1)
                        IconButton(onClick = onClose) { Icon(AppIcons.Close, stringResource(R.string.close)) }
                    }
                    HorizontalDivider(color = colors.outlineVariant)
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                            .windowInsetsPadding(WindowInsets.navigationBars).padding(vertical = 12.dp),
                        content = panel,
                    )
                }
                }
            }
        }
    }
}
