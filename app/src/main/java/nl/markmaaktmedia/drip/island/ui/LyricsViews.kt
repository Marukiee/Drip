package nl.markmaaktmedia.drip.island.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.graph
import nl.markmaaktmedia.drip.island.Haptics
import nl.markmaaktmedia.drip.island.IslandHub
import nl.markmaaktmedia.drip.island.LyricLine
import nl.markmaaktmedia.drip.island.LyricsState
import nl.markmaaktmedia.drip.island.NowPlaying
import nl.markmaaktmedia.drip.island.currentLineIndex
import nl.markmaaktmedia.drip.ui.theme.DripIcons

/** How tall the lyrics are under the music card, whatever they show, so the island grows once and not again. */
val LyricsHeight: Dp = 224.dp

/** Where a song is now, between the reports of the player. */
private fun positionOf(np: NowPlaying, now: Long): Long =
    (np.positionMs + ((now - np.positionAtElapsed) * np.speed).toLong()).coerceIn(0L, np.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)

/**
 * The lyrics under the music card: a soft panel whose bottom corners follow the island. Synced lines follow the song,
 * the one being sung white and a touch larger, the ones before it fading out at the top and the ones to come dimmer
 * below; a tap on a line jumps there. Scrolling by hand leaves the list alone for a moment.
 */
@Composable
fun LyricsPanel(np: NowPlaying, hub: IslandHub) {
    val m = LocalCardMetrics.current
    val state by hub.lyrics.collectAsState()
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = m.buttonOuterRadius, bottomEnd = m.buttonOuterRadius)
    Box(
        Modifier
            .fillMaxWidth()
            .height(LyricsHeight)
            .clip(shape)
            .background(IslandInk.Surface),
    ) {
        AnimatedContent(
            state,
            contentKey = { it::class to it.key },
            transitionSpec = { fadeIn(tween(220, 80)).togetherWith(fadeOut(tween(140))) },
            label = "lyrics",
        ) { s ->
            when (s) {
                is LyricsState.Found -> if (s.synced) SyncedLines(s.lines, np) else PlainLines(s.lines)
                is LyricsState.Loading, LyricsState.Off -> LyricsNote(stringResource(R.string.island_lyrics_looking), searching = true)
                is LyricsState.Missing -> LyricsNote(stringResource(if (s.instrumental) R.string.island_lyrics_instrumental else R.string.island_lyrics_none))
                is LyricsState.AskOnline -> AskOnline()
                is LyricsState.Failed -> LyricsNote(stringResource(R.string.island_lyrics_failed), action = stringResource(R.string.island_lyrics_retry)) {
                    hub.retryLyrics?.invoke()
                }
            }
        }
    }
}

/** Fades the top and bottom edge of the list into the panel, so lines come and go instead of being cut off. */
private fun Modifier.fadedEdges(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, 0.2f to Color.Black, 0.78f to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
private fun SyncedLines(lines: List<LyricLine>, np: NowPlaying) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(np.playing) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(100)
        }
    }
    val current = currentLineIndex(lines, positionOf(np, now))
    val list = rememberLazyListState(initialFirstVisibleItemIndex = current.coerceAtLeast(0))
    var touchedAt by remember { mutableLongStateOf(0L) }
    var following by remember { mutableStateOf(false) }
    LaunchedEffect(list.isScrollInProgress) {
        if (list.isScrollInProgress && !following) touchedAt = SystemClock.elapsedRealtime()
    }
    LaunchedEffect(current) {
        if (current < 0 || SystemClock.elapsedRealtime() - touchedAt < HANDS_OFF_MS) return@LaunchedEffect
        following = true
        try {
            list.animateScrollToItem(current)
        } finally {
            following = false
        }
    }
    LazyColumn(
        Modifier.fillMaxSize().fadedEdges(),
        state = list,
        // The line being sung sits a third of the way down, with what came before fading out above it.
        contentPadding = PaddingValues(top = 64.dp, bottom = LyricsHeight - 64.dp),
    ) {
        itemsIndexed(lines, key = { i, _ -> i }) { i, line ->
            val past = i < current
            val active = i == current
            val color by animateColorAsState(
                when {
                    active -> IslandInk.Primary
                    past -> IslandInk.Primary.copy(alpha = 0.32f)
                    else -> IslandInk.Primary.copy(alpha = 0.5f)
                },
                tween(260), label = "lyricColor",
            )
            val scale by animateFloatAsState(if (active) 1f else 0.94f, spring(0.7f, 300f), label = "lyricScale")
            Text(
                line.text.ifBlank { "♪" },
                color = color, fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(remember { MutableInteractionSource() }, null, enabled = np.canSeek) {
                        context.graph.haptics.play(Haptics.Kind.TAP)
                        touchedAt = 0L
                        np.commands.seekTo(line.timeMs)
                    }
                    .padding(horizontal = 18.dp, vertical = 5.dp)
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    },
            )
        }
    }
}

@Composable
private fun PlainLines(lines: List<LyricLine>) {
    LazyColumn(
        Modifier.fillMaxSize().fadedEdges(),
        contentPadding = PaddingValues(top = 40.dp, bottom = 48.dp),
    ) {
        itemsIndexed(lines, key = { i, _ -> i }) { _, line ->
            Text(
                line.text, color = IslandInk.Primary.copy(alpha = 0.8f), fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = if (line.text.isEmpty()) 6.dp else 2.dp),
            )
        }
    }
}

/** A quiet line in the middle of the panel: looking, nothing found, no connection; with a button when there is one. */
@Composable
private fun LyricsNote(text: String, searching: Boolean = false, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        val pulse by animateFloatAsState(if (searching) 1f else 0f, tween(400), label = "lyricsPulse")
        Icon(DripIcons.Lyrics, null, tint = IslandInk.Tertiary, modifier = Modifier.size(28.dp).graphicsLayer { alpha = 0.6f + 0.4f * pulse })
        Text(text, color = IslandInk.Secondary, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
        if (action != null && onAction != null) PillButton(action, onAction, Modifier.padding(top = 14.dp))
    }
}

/** Nothing from the player and looking online is off: say what would leave the phone, and let the person switch it on. */
@Composable
private fun AskOnline() {
    val context = LocalContext.current
    LyricsNote(stringResource(R.string.island_lyrics_ask), action = stringResource(R.string.island_lyrics_online)) {
        context.graph.scope.launch { context.graph.prefs.editIsland { it.copy(lyricsOnline = true) } }
    }
}

@Composable
private fun PillButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    Box(
        modifier
            .clip(CircleShape)
            .background(scheme.primary)
            .clickable {
                context.graph.haptics.play(Haptics.Kind.TAP)
                onClick()
            }
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(label, color = scheme.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** How long the list leaves a hand-scrolled position alone before it follows the song again. */
private const val HANDS_OFF_MS = 3500L
