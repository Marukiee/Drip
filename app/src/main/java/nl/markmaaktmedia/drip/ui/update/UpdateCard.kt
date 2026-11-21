package nl.markmaaktmedia.drip.ui.update

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.markmaaktmedia.drip.BuildConfig
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.graph
import nl.markmaaktmedia.drip.ui.components.bouncyClickable
import nl.markmaaktmedia.drip.ui.theme.DripIcons
import nl.markmaaktmedia.drip.ui.theme.DripMotion
import nl.markmaaktmedia.drip.ui.theme.PillShape
import nl.markmaaktmedia.drip.ui.theme.groupedShape
import nl.markmaaktmedia.drip.update.ReleaseInfo
import nl.markmaaktmedia.drip.update.UpdateState

/**
 * The card at the top of the home screen when a new version is out, in the shape of the other cards there. At rest it
 * names the version and the first lines of what is new, with Later and Update side by side over the whole width. Update
 * turns itself into the bar that fills with the download while Later shrinks away, and when the download is in, the
 * install starts without another tap: the bar stays full and says so, the icon becomes a round check, and the app
 * comes back as the new version. Nothing swaps: every change is the same card moving.
 *
 * [gap] is the room below it, inside what grows and shrinks, so a hidden card leaves no gap in the list.
 */
@Composable
fun UpdateCard(modifier: Modifier = Modifier, gap: Dp = 0.dp) {
    val graph = LocalContext.current.graph
    val updater = graph.updater
    val state by updater.state.collectAsState()
    val dismissed by graph.prefs.dismissedUpdate.collectAsState(initial = null)
    val phase = updateCardPhase(state)
    val release = state.release
    val visible = phase == UpdateCardPhase.Failed || (phase != null && release != null && release.tag != dismissed)
    // What the card last showed stays while it leaves, so it does not empty out on its way.
    var shown by remember { mutableStateOf<UpdateState?>(null) }
    if (visible) shown = state

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(DripMotion.fadeSpec()) + expandVertically(DripMotion.sizeSpring()),
        exit = fadeOut(DripMotion.fadeSpec()) + shrinkVertically(DripMotion.sizeSpring()),
        modifier = modifier,
    ) {
        shown?.let { CardBody(it, Modifier.padding(bottom = gap)) }
    }
}

@Composable
private fun CardBody(state: UpdateState, modifier: Modifier) {
    val graph = LocalContext.current.graph
    val updater = graph.updater
    val scope = rememberCoroutineScope()
    val phase = updateCardPhase(state) ?: return
    val release = state.release
    val language = LocalConfiguration.current.locales[0].language
    val notes = release?.let { notesFor(it.notes, language) }.orEmpty()
    val progress = (state as? UpdateState.Downloading)?.progress ?: if (phase == UpdateCardPhase.Installing) 1f else 0f

    Column(
        modifier
            .fillMaxWidth()
            .clip(groupedShape(0, 1))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .animateContentSize(DripMotion.sizeSpring())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Badge(phase)
            Lines(phase, state, release, Modifier.weight(1f))
        }
        AnimatedVisibility(
            visible = notes.isNotEmpty() && (phase == UpdateCardPhase.Rest || phase == UpdateCardPhase.Downloading),
            enter = fadeIn(DripMotion.fadeSpec()) + expandVertically(DripMotion.sizeSpring(), expandFrom = Alignment.Top),
            exit = fadeOut(DripMotion.fadeSpec()) + shrinkVertically(DripMotion.sizeSpring(), shrinkTowards = Alignment.Top),
        ) {
            // Under the title, past the icon.
            Column(Modifier.padding(start = 58.dp, top = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                notes.forEach { line ->
                    // A line that runs on starts again under the text, not under the bullet.
                    Row {
                        Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(14.dp))
                        Text(
                            line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        Actions(
            phase = phase,
            progress = progress,
            mainLabel = when {
                phase == UpdateCardPhase.Failed -> stringResource(R.string.update_card_retry)
                state is UpdateState.NeedsPermission -> stringResource(R.string.update_allow)
                else -> stringResource(R.string.update_now)
            },
            onLater = { release?.let { scope.launch { graph.prefs.setDismissedUpdate(it.tag) } } },
            onMain = {
                when {
                    phase == UpdateCardPhase.Failed -> scope.launch { updater.retry() }
                    state is UpdateState.NeedsPermission -> updater.openInstallPermissionSettings()
                    release != null -> scope.launch { updater.downloadAndInstall(release) }
                }
            },
        )
    }
}

/** The square with the sparkle; while installing a round green check, after a failure the error sign. */
@Composable
private fun Badge(phase: UpdateCardPhase) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val green = if (dark) Color(0xFF5FD68A) else Color(0xFF1E6B3A)
    val greenContainer = if (dark) Color(0xFF1F3A2A) else Color(0xFFC6EFD3)
    val done = phase == UpdateCardPhase.Installing
    val failed = phase == UpdateCardPhase.Failed
    val corner by animateDpAsState(if (done) 23.dp else 15.dp, spring(0.7f, Spring.StiffnessMediumLow), label = "badgeCorner")
    val container by animateColorAsState(
        when { done -> greenContainer; failed -> scheme.errorContainer; else -> scheme.primaryContainer }, DripMotion.colourSpec(), label = "badgeContainer",
    )
    val ink by animateColorAsState(
        when { done -> green; failed -> scheme.onErrorContainer; else -> scheme.onPrimaryContainer }, DripMotion.colourSpec(), label = "badgeInk",
    )
    Box(Modifier.size(46.dp).clip(RoundedCornerShape(corner)).background(container), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = when { done -> 1; failed -> 2; else -> 0 },
            transitionSpec = {
                (scaleIn(spring(0.6f, Spring.StiffnessMediumLow), initialScale = 0.3f) + fadeIn(tween(200)))
                    .togetherWith(scaleOut(tween(200), targetScale = 0.3f) + fadeOut(tween(150)))
            },
            label = "badgeIcon",
        ) { which ->
            Icon(
                when (which) { 1 -> DripIcons.Check; 2 -> DripIcons.Error; else -> DripIcons.NewVersion },
                contentDescription = null, tint = ink, modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** The title and the line under it. A new look slides the old lines up and away and the new ones in from below. */
@Composable
private fun Lines(phase: UpdateCardPhase, state: UpdateState, release: ReleaseInfo?, modifier: Modifier) {
    AnimatedContent(
        targetState = phase,
        transitionSpec = {
            (slideInVertically(spring(0.8f, Spring.StiffnessMediumLow)) { it / 2 } + fadeIn(tween(220)))
                .togetherWith(slideOutVertically(spring(0.8f, Spring.StiffnessMediumLow)) { -it / 2 } + fadeOut(tween(150)))
                .using(SizeTransform(clip = true))
        },
        contentAlignment = Alignment.CenterStart,
        modifier = modifier.clipToBounds(),
        label = "updateLines",
    ) { look ->
        val version = release?.versionName.orEmpty()
        val title = when (look) {
            UpdateCardPhase.Installing -> stringResource(R.string.update_card_installing_title, version)
            UpdateCardPhase.Failed -> stringResource(R.string.update_failed)
            else -> stringResource(R.string.update_card_title, version)
        }
        val line = when (look) {
            UpdateCardPhase.Installing -> stringResource(R.string.update_card_restart)
            UpdateCardPhase.Failed -> (state as? UpdateState.Failed)?.reason.orEmpty()
            UpdateCardPhase.Downloading -> {
                val total = release?.apkSizeBytes ?: 0L
                val done = (total * ((state as? UpdateState.Downloading)?.progress ?: 0f)).toLong()
                if (total > 0) stringResource(R.string.update_card_progress, megabytes(done), megabytes(total)) else ""
            }
            UpdateCardPhase.Rest -> if (state is UpdateState.NeedsPermission) {
                stringResource(R.string.update_needs_permission)
            } else {
                stringResource(R.string.update_on_version, BuildConfig.VERSION_NAME.removeSuffix("-debug"))
            }
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (look == UpdateCardPhase.Failed) 2 else 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Later and the main button share the width, one part to two. Pressed, Later shrinks to nothing and the main button
 * becomes the bar over the whole width; a failure keeps one wide button to try again.
 */
@Composable
private fun Actions(phase: UpdateCardPhase, progress: Float, mainLabel: String, onLater: () -> Unit, onMain: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val rest = phase == UpdateCardPhase.Rest
    val pressable = rest || phase == UpdateCardPhase.Failed
    // Later's share of the width, a third at rest. Animating the share rather than a weight makes it shrink evenly,
    // in about half a second like the rest of the app's movement. A weight may not reach zero, so both stay above it.
    val share by animateFloatAsState(if (rest) 1f / 3f else 0f, spring(0.9f, 90f), label = "laterShare")
    val laterAlpha by animateFloatAsState(if (rest) 1f else 0f, DripMotion.fadeSpec(), label = "laterAlpha")
    val space = (8.dp * (share * 3f)).coerceIn(0.dp, 8.dp)

    Row(Modifier.fillMaxWidth().padding(top = 14.dp).height(46.dp)) {
        Box(
            Modifier
                .weight(share.coerceAtLeast(0.0001f))
                .fillMaxHeight()
                .graphicsLayer { alpha = laterAlpha }
                .clip(PillShape)
                .background(scheme.surfaceContainerHighest)
                .bouncyClickable(enabled = rest, onClick = onLater),
            contentAlignment = Alignment.Center,
        ) {
            // Its own width while the button narrows, cut off by the pill rather than squeezed onto two lines.
            Text(
                stringResource(R.string.update_later), color = scheme.primary, style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, modifier = Modifier.wrapContentWidth(unbounded = true),
            )
        }
        Spacer(Modifier.width(space))
        MainButton(phase, progress, mainLabel, pressable, onMain, Modifier.weight((1f - share).coerceAtLeast(0.0001f)).fillMaxHeight())
    }
}

/** Update at rest; the bar with the percentage while downloading; full, with a moving sheen, while installing. */
@Composable
private fun MainButton(phase: UpdateCardPhase, progress: Float, label: String, pressable: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val track by animateColorAsState(
        when (phase) {
            UpdateCardPhase.Downloading -> scheme.surfaceContainerHighest
            UpdateCardPhase.Installing -> scheme.primaryContainer
            else -> scheme.primary
        },
        DripMotion.colourSpec(), label = "barTrack",
    )
    val ink by animateColorAsState(
        if (pressable) scheme.onPrimary else scheme.onPrimaryContainer, DripMotion.colourSpec(), label = "barInk",
    )
    // The fill follows the download with a spring, so the steps of the download read as one movement.
    val fill by animateFloatAsState(progress, spring(1f, Spring.StiffnessLow), label = "barFill")
    val fillAlpha by animateFloatAsState(if (phase == UpdateCardPhase.Downloading) 1f else 0f, DripMotion.fadeSpec(), label = "barFillAlpha")
    val sheen = rememberInfiniteTransition(label = "sheen")
    val sweep by sheen.animateFloat(-0.6f, 1.6f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "sheenSweep")
    val installing = phase == UpdateCardPhase.Installing

    Box(
        modifier
            .clip(PillShape)
            .background(track)
            .drawWithContent {
                drawContent()
                if (installing) {
                    val x = sweep * size.width
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent),
                            start = Offset(x - size.width * 0.25f, 0f), end = Offset(x + size.width * 0.25f, size.height),
                        ),
                    )
                }
            }
            .bouncyClickable(enabled = pressable, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(fill.coerceIn(0f, 1f))
                .graphicsLayer { alpha = fillAlpha }
                .clip(PillShape)
                .background(scheme.primaryContainer),
        )
        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                (slideInVertically(spring(0.8f, Spring.StiffnessMediumLow)) { it } + fadeIn(tween(200)))
                    .togetherWith(slideOutVertically(spring(0.8f, Spring.StiffnessMediumLow)) { -it } + fadeOut(tween(150)))
            },
            label = "barLabel",
        ) { look ->
            Text(
                when (look) {
                    UpdateCardPhase.Downloading -> "${(progress * 100).toInt()}%"
                    UpdateCardPhase.Installing -> stringResource(R.string.update_card_installing)
                    else -> label
                },
                color = ink, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1,
            )
        }
    }
}
