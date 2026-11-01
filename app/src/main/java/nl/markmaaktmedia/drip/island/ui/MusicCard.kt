package nl.markmaaktmedia.drip.island.ui

import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.graph
import nl.markmaaktmedia.drip.island.Haptics
import nl.markmaaktmedia.drip.island.IslandHub
import nl.markmaaktmedia.drip.island.NowPlaying
import nl.markmaaktmedia.drip.island.RepeatMode
import nl.markmaaktmedia.drip.ui.theme.DripIcons

/**
 * The opened music card: cover, title, a wavy line that moves while the song plays, the controls, and the volume as a
 * bar along the bottom whose corners follow the island.
 */
@Composable
fun MusicCard(np: NowPlaying, hub: IslandHub) {
    val scheme = MaterialTheme.colorScheme
    val c = np.commands

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        // The cover as a Material 3 Expressive cookie: soft, a little playful, never a plain square.
        Picture(np.art, 64.dp, MaterialShapes.Cookie9Sided.toShape(), DripIcons.Music)
        Column(Modifier.weight(1f)) {
            Title(np.title, size = 19)
            Subtitle(np.artist)
        }
        AppIcon(null, np.packageName, 22.dp)
    }

    Spacer(Modifier.height(14.dp))
    SeekBar(np)
    Spacer(Modifier.height(10.dp))

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        val repeat = np.repeat
        if (repeat != null) {
            IslandIconButton(
                if (repeat == RepeatMode.ONE) DripIcons.RepeatOne else DripIcons.Repeat, stringResource(R.string.island_repeat), c::cycleRepeat,
                tint = if (repeat == RepeatMode.OFF) IslandInk.Secondary else scheme.primary, size = 44.dp, iconSize = 22.dp,
            )
        } else if (np.shuffle != null) {
            IslandIconButton(
                DripIcons.Shuffle, stringResource(R.string.island_shuffle), c::toggleShuffle,
                tint = if (np.shuffle) scheme.primary else IslandInk.Secondary, size = 44.dp, iconSize = 22.dp,
            )
        } else {
            Spacer(Modifier.width(44.dp))
        }
        IslandIconButton(
            DripIcons.SkipPreviousFilled, stringResource(R.string.island_previous), c::previous,
            container = IslandInk.Surface, size = 52.dp, iconSize = 26.dp,
        )
        PlayButton(np.playing, c::playPause)
        IslandIconButton(
            DripIcons.SkipNextFilled, stringResource(R.string.island_next), c::next,
            container = IslandInk.Surface, size = 52.dp, iconSize = 26.dp,
        )
        val liked = np.liked
        if (liked != null) {
            IslandIconButton(
                if (liked) DripIcons.FavoriteFilled else DripIcons.Favorite, stringResource(R.string.island_like), c::toggleLike,
                tint = if (liked) scheme.primary else IslandInk.Primary, size = 44.dp, iconSize = 22.dp,
            )
        } else {
            Spacer(Modifier.width(44.dp))
        }
    }

    Spacer(Modifier.height(14.dp))
    VolumeBar(hub)
}

/** Play is a pill and pause a squircle: the button morphs between the two, the way Material 3 Expressive buttons do. */
@Composable
private fun PlayButton(playing: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val context = LocalContext.current
    val corner by animateDpAsState(
        when {
            pressed -> 12.dp
            playing -> 18.dp
            else -> 28.dp
        },
        spring(dampingRatio = 0.55f, stiffness = 500f), label = "playCorner",
    )
    val width by animateDpAsState(if (playing) 84.dp else 76.dp, spring(0.6f, 500f), label = "playWidth")
    Box(
        Modifier
            .size(width, 56.dp)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(source, null) {
                context.graph.haptics.play(Haptics.Kind.TAP)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(playing, animationSpec = tween(160), label = "playIcon") { now ->
            Icon(
                if (now) DripIcons.PauseFilled else DripIcons.PlayFilled,
                contentDescription = stringResource(if (now) R.string.island_pause else R.string.island_play),
                tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp),
            )
        }
    }
}

/** Elapsed time, the wave, the time left. Drag or tap the wave to jump in the song. */
@Composable
private fun SeekBar(np: NowPlaying) {
    val scheme = MaterialTheme.colorScheme
    val duration = np.durationMs.coerceAtLeast(1)
    // The position moves on every frame while playing, but only the wave reads it, in drawing.
    val clock = remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(np.playing, np.positionAtElapsed) {
        while (np.playing) withFrameMillis { clock.longValue = SystemClock.elapsedRealtime() }
    }
    fun position(at: Long) = (np.positionMs + if (np.playing) ((at - np.positionAtElapsed) * np.speed).toLong() else 0L).coerceIn(0L, duration)
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val amplitude by animateFloatAsState(if (np.playing && !dragging) 1f else 0f, tween(300), label = "wave")
    // The time labels only need to change once a second; this recomposes them that often and no more.
    rememberNow(1000)
    val shown = if (dragging) (dragFraction * duration).toLong() else position(SystemClock.elapsedRealtime())
    val line = with(LocalDensity.current) { 4.dp.toPx() }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TimeText(formatClock(shown))
        Box(
            Modifier
                .weight(1f)
                .height(28.dp)
                .then(
                    if (np.canSeek && np.durationMs > 0) {
                        Modifier
                            .pointerInput(np.durationMs) {
                                detectTapGestures { offset -> np.commands.seekTo((offset.x / size.width * duration).toLong()) }
                            }
                            .pointerInput(np.durationMs) {
                                detectHorizontalDragGestures(
                                    onDragStart = { dragging = true; dragFraction = it.x / size.width },
                                    onDragEnd = { dragging = false; np.commands.seekTo((dragFraction * duration).toLong()) },
                                    onDragCancel = { dragging = false },
                                ) { change, _ ->
                                    change.consume()
                                    dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                }
                            }
                    } else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            val progress = { if (dragging) dragFraction else position(clock.longValue) / duration.toFloat() }
            LinearWavyProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().height(14.dp),
                color = scheme.primary,
                trackColor = IslandInk.SurfaceHigh,
                stroke = Stroke(width = line, cap = StrokeCap.Round),
                trackStroke = Stroke(width = line, cap = StrokeCap.Round),
                gapSize = 8.dp,
                amplitude = { amplitude },
            )
            // The handle: an upright bar in the gap between the wave and the track, as on a Material 3 Expressive
            // slider. It grows while it is dragged.
            val handle by animateDpAsState(if (dragging) 26.dp else 20.dp, spring(0.6f, 600f), label = "handle")
            val handleColor = scheme.primary
            Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                val w = 4.dp.toPx()
                val h = handle.toPx()
                val x = (progress().coerceIn(0f, 1f) * size.width + 4.dp.toPx()).coerceIn(w / 2, size.width - w / 2)
                drawRoundRect(handleColor, Offset(x - w / 2, (size.height - h) / 2), Size(w, h), CornerRadius(w / 2))
            }
        }
        TimeText("-" + formatClock(duration - shown))
    }
}

@Composable
private fun TimeText(text: String) = Text(text, color = IslandInk.Secondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)

/** The media volume as a thick bar: its bottom corners concentric with the island, its top corners small. */
@Composable
private fun VolumeBar(hub: IslandHub) {
    val m = LocalCardMetrics.current
    val scheme = MaterialTheme.colorScheme
    val volume by hub.musicVolume.collectAsState()
    var local by remember { mutableStateOf<Float?>(null) }
    val shown by animateFloatAsState(local ?: volume, spring(1f, 900f), label = "volume")
    val context = LocalContext.current
    val shape = RoundedCornerShape(topStart = m.buttonInnerRadius, topEnd = m.buttonInnerRadius, bottomStart = m.buttonOuterRadius, bottomEnd = m.buttonOuterRadius)
    fun set(fraction: Float) {
        val f = fraction.coerceIn(0f, 1f)
        local = f
        hub.setMusicVolume?.invoke(f)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(IslandInk.Surface)
            .pointerInput(Unit) { detectTapGestures { set(it.x / size.width) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { context.graph.haptics.play(Haptics.Kind.TICK) },
                    onDragEnd = { local = null },
                    onDragCancel = { local = null },
                ) { change, _ ->
                    change.consume()
                    set(change.position.x / size.width)
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(shown.coerceIn(0.001f, 1f))
                .background(scheme.primary),
        )
        Icon(
            if (shown <= 0.001f) DripIcons.VolumeOff else DripIcons.VolumeUp, contentDescription = stringResource(R.string.island_volume),
            tint = if (shown > 0.12f) scheme.onPrimary else IslandInk.Secondary,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp).size(22.dp),
        )
    }
}
