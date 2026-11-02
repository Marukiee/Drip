package nl.markmaaktmedia.drip.island.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch
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
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.draw.drawBehind
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
 *
 * Everything that changes, changes with a movement: a new cover drops in over the old one, the title rolls, the cover
 * slowly turns while the song plays and settles a little smaller when it pauses.
 */
@Composable
fun MusicCard(np: NowPlaying, hub: IslandHub, settings: nl.markmaaktmedia.drip.data.IslandSettings) {
    val c = np.commands
    val lyricsOpen by hub.lyricsOpen.collectAsState()
    val lyrics = settings.showLyrics && lyricsOpen

    // The cover and the title open the player itself (Spotify's now playing screen, say), and the island closes.
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val dip by animateFloatAsState(if (pressed) 0.97f else 1f, spring(0.5f, 600f), label = "headerDip")
    val context = LocalContext.current
    Row(
        Modifier
            .graphicsLayer { scaleX = dip; scaleY = dip }
            .clickable(source, null) {
                context.graph.haptics.play(Haptics.Kind.TAP)
                c.openPlayer()
                hub.demo(nl.markmaaktmedia.drip.island.Demo.COLLAPSE)
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Cover(np)
        Column(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = np.title to np.artist,
                transitionSpec = {
                    (slideInVertically(spring(0.8f, 400f)) { it / 2 } + fadeIn(tween(220, 60)))
                        .togetherWith(slideOutVertically(spring(0.8f, 400f)) { -it / 2 } + fadeOut(tween(120)))
                },
                label = "title",
            ) { (title, artist) ->
                Column {
                    Title(title, size = 19)
                    Subtitle(artist)
                }
            }
            np.next?.let { next ->
                Text(
                    stringResource(R.string.island_next_up, next), color = IslandInk.Tertiary, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        AppIcon(null, np.packageName, 22.dp)
    }

    Spacer(Modifier.height(14.dp))
    SeekBar(np)
    Spacer(Modifier.height(12.dp))

    // The three that matter most joined in the middle, the extras two by two at the sides.
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Box {
                SideButton(DripIcons.Shuffle, R.string.island_shuffle, active = np.shuffle, onClick = c::toggleShuffle)
                // Spotify's third way, smart shuffle: a small spark lands on the shuffle sign.
                androidx.compose.animation.AnimatedVisibility(
                    np.smartShuffle, Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp),
                    enter = scaleIn(spring(0.5f, 500f)) + fadeIn(tween(120)), exit = scaleOut(tween(140)) + fadeOut(tween(120)),
                ) { Spark(MaterialTheme.colorScheme.primary) }
            }
            SideButton(
                if (np.repeat == RepeatMode.ONE) DripIcons.RepeatOne else DripIcons.Repeat, R.string.island_repeat,
                active = np.repeat?.let { it != RepeatMode.OFF }, onClick = c::cycleRepeat,
            )
        }
        TransportGroup(np.playing, c)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            // The lyrics open under the card; where the sound plays is in the volume card, and here without lyrics.
            if (settings.showLyrics) {
                SideButton(DripIcons.Lyrics, R.string.island_lyrics, active = lyricsOpen) {
                    hub.lyricsOpen.value = !lyricsOpen
                }
            } else {
                OutputButton(hub)
            }
            SideButton(
                if (np.liked == true) DripIcons.FavoriteFilled else DripIcons.Favorite, R.string.island_like,
                active = np.liked, onClick = c::toggleLike,
            )
        }
    }

    Spacer(Modifier.height(14.dp))
    VolumeBar(hub, np, concentric = !lyrics)
    // Fades in at its full height at once, so the island grows to it in one movement instead of with every frame.
    androidx.compose.animation.AnimatedVisibility(
        visible = lyrics,
        enter = fadeIn(tween(240, 120)),
        exit = fadeOut(tween(160)),
    ) {
        Column {
            Spacer(Modifier.height(10.dp))
            LyricsPanel(np, hub)
        }
    }
}

/**
 * The cover as a Material 3 Expressive cookie. It turns slowly while the song plays and stands still, a little smaller,
 * when it pauses; a new song drops its cover in over the old one.
 */
@Composable
private fun Cover(np: NowPlaying) {
    val turn = remember { Animatable(0f) }
    LaunchedEffect(np.playing) {
        if (np.playing) {
            while (true) turn.animateTo(turn.value + 360f, tween(24_000, easing = LinearEasing))
        }
    }
    val size by animateFloatAsState(if (np.playing) 1f else 0.86f, spring(0.55f, 380f), label = "coverSize")
    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = np.art,
            transitionSpec = {
                (scaleIn(spring(0.6f, 420f), initialScale = 0.6f) + fadeIn(tween(200)))
                    .togetherWith(scaleOut(tween(220), targetScale = 1.15f) + fadeOut(tween(200)))
            },
            label = "cover",
        ) { art ->
            Box(Modifier.graphicsLayer { scaleX = size; scaleY = size; rotationZ = turn.value }) {
                Picture(art, 64.dp, MaterialShapes.Cookie9Sided.toShape(), DripIcons.Music)
            }
        }
    }
}

/** A four-pointed spark, the sign of smart shuffle. */
@Composable
private fun Spark(color: androidx.compose.ui.graphics.Color) {
    Canvas(Modifier.size(9.dp)) {
        val c = size.width / 2
        val inner = c * 0.32f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(c, 0f)
            quadraticTo(c + inner * 0.4f, c - inner * 0.4f, size.width, c)
            quadraticTo(c + inner * 0.4f, c + inner * 0.4f, c, size.height)
            quadraticTo(c - inner * 0.4f, c + inner * 0.4f, 0f, c)
            quadraticTo(c - inner * 0.4f, c - inner * 0.4f, c, 0f)
            close()
        }
        drawPath(path, color)
    }
}

/** Shuffle, repeat, like: a small round button, lit when on, grey when the player does not offer it. */
@Composable
private fun SideButton(icon: Painter, label: Int, active: Boolean?, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val tint by animateColorAsState(
        when (active) {
            null -> IslandInk.Tertiary.copy(alpha = 0.25f)
            true -> scheme.primary
            false -> IslandInk.Secondary
        },
        tween(220), label = "sideTint",
    )
    IslandIconButton(
        icon, stringResource(label),
        { if (active == null) context.graph.haptics.play(Haptics.Kind.TICK) else onClick() },
        size = 40.dp, iconSize = 21.dp, tint = tint,
    )
}

/**
 * Where the sound plays. Pressed, the speaker sends out two rings before Android's own panel opens, so the tap is
 * answered at once even though the panel takes a moment.
 */
@Composable
private fun OutputButton(hub: IslandHub) {
    val ring = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val color = MaterialTheme.colorScheme.primary
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(40.dp)) {
            val p = ring.value
            if (p > 0f && p < 1f) {
                for (i in 0..1) {
                    val q = (p * 1.4f - i * 0.4f).coerceIn(0f, 1f)
                    if (q <= 0f) continue
                    drawCircle(color.copy(alpha = (1f - q) * 0.6f), radius = size.minDimension / 2 * (0.5f + q * 0.6f), style = Stroke(2.dp.toPx()))
                }
            }
        }
        IslandIconButton(
            DripIcons.SpeakerGroup, stringResource(R.string.island_output),
            {
                scope.launch {
                    ring.snapTo(0f)
                    launch { ring.animateTo(1f, tween(520, easing = LinearEasing)) }
                    kotlinx.coroutines.delay(200)
                    hub.openOutput?.invoke()
                }
            },
            size = 40.dp, iconSize = 21.dp, tint = IslandInk.Secondary,
        )
    }
}

/**
 * Previous, play and next as one Material 3 Expressive button group: joined, round on the outside, with small corners
 * where they meet. A pressed button swells and rounds its inner corners; its neighbours make room.
 */
@Composable
private fun TransportGroup(playing: Boolean, c: nl.markmaaktmedia.drip.island.MediaCommands) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        GroupButton(DripIcons.SkipPreviousFilled, R.string.island_previous, Side.START, IslandInk.Surface, IslandInk.Primary, 54.dp, c::previous)
        PlayButton(playing, c::playPause)
        GroupButton(DripIcons.SkipNextFilled, R.string.island_next, Side.END, IslandInk.Surface, IslandInk.Primary, 54.dp, c::next)
    }
}

private enum class Side { START, END }

@Composable
private fun GroupButton(icon: Painter, label: Int, side: Side, container: Color, tint: Color, width: Dp, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val context = LocalContext.current
    val inner by animateDpAsState(if (pressed) 28.dp else 8.dp, spring(0.55f, 600f), label = "inner")
    val grow by animateDpAsState(if (pressed) width + 8.dp else width, spring(0.5f, 600f), label = "grow")
    val outer = 28.dp
    val shape = if (side == Side.START) RoundedCornerShape(outer, inner, inner, outer) else RoundedCornerShape(inner, outer, outer, inner)
    Box(
        Modifier
            .size(grow, 56.dp)
            .clip(shape)
            .background(container)
            .clickable(source, null) {
                context.graph.haptics.play(Haptics.Kind.TAP)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, stringResource(label), tint = tint, modifier = Modifier.size(26.dp))
    }
}

/** Play is a pill and pause a squircle: the button morphs between the two, the way Material 3 Expressive buttons do. */
@Composable
private fun PlayButton(playing: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val context = LocalContext.current
    val corner by animateDpAsState(
        when {
            pressed -> 28.dp
            playing -> 10.dp
            else -> 28.dp
        },
        spring(dampingRatio = 0.55f, stiffness = 500f), label = "playCorner",
    )
    val width by animateDpAsState(if (pressed) 92.dp else if (playing) 84.dp else 76.dp, spring(0.55f, 500f), label = "playWidth")
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
        AnimatedContent(
            targetState = playing,
            transitionSpec = {
                (scaleIn(spring(0.5f, 700f), initialScale = 0.4f) + fadeIn(tween(120)))
                    .togetherWith(scaleOut(tween(120), targetScale = 0.4f) + fadeOut(tween(100)))
            },
            label = "playIcon",
        ) { now ->
            Icon(
                if (now) DripIcons.PauseFilled else DripIcons.PlayFilled,
                contentDescription = stringResource(if (now) R.string.island_pause else R.string.island_play),
                tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp),
            )
        }
    }
}

/**
 * The progress of the song, the way Tandem draws it: the played part and the rest as two lines with an upright handle
 * between them and a gap on both sides. The played part is a wave while the music plays; it rises on a spring when the
 * music starts and eases flat when it stops, and it flattens towards the handle so the line meets it in its middle.
 *
 * The wave and the position move 20 times a second, not on every frame: that is smooth for something this slow and keeps
 * the island from drawing at the full refresh rate for as long as the card is open. Drag or tap to jump in the song.
 */
@Composable
private fun SeekBar(np: NowPlaying) {
    val scheme = MaterialTheme.colorScheme
    val duration = np.durationMs.coerceAtLeast(1)
    val clock = remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(np.playing, np.positionAtElapsed) {
        clock.longValue = SystemClock.elapsedRealtime()
        if (!np.playing) return@LaunchedEffect
        var last = SystemClock.uptimeMillis()
        while (true) {
            kotlinx.coroutines.delay(WaveFrameMs)
            val tick = SystemClock.uptimeMillis()
            phase.floatValue = (phase.floatValue + (tick - last) * WaveCyclesPerMs) % 1f
            clock.longValue = SystemClock.elapsedRealtime()
            last = tick
        }
    }
    fun position(at: Long) = (np.positionMs + if (np.playing) ((at - np.positionAtElapsed) * np.speed).toLong() else 0L).coerceIn(0L, duration)
    var drag by remember { mutableStateOf<Float?>(null) }
    // Where it was let go and which report of the player it waits past: the bar stays there until the player says where
    // the song really is, instead of jumping back to the old spot for a moment.
    var held by remember { mutableStateOf<Triple<Float, Long, Long>?>(null) }
    fun jump(fraction: Float) {
        held = Triple(fraction, SystemClock.elapsedRealtime(), np.positionAtElapsed)
        np.commands.seekTo((fraction * duration).toLong())
    }
    val context = LocalContext.current
    val amplitude by animateFloatAsState(if (np.playing && drag == null) 1f else 0f, spring(0.9f, 220f), label = "wave")
    val knob by animateFloatAsState(if (drag != null) 1f else 0f, spring(0.55f, 600f), label = "knob")
    val thickness by animateDpAsState(if (drag != null) 6.dp else 4.dp, spring(0.6f, 500f), label = "thickness")
    val track = IslandInk.SurfaceHigh
    // The played part fades towards the track while the music is paused, so the bar says it stands still.
    val fill by animateColorAsState(if (np.playing) scheme.primary else androidx.compose.ui.graphics.lerp(scheme.primary, track, 0.5f), tween(320), label = "fill")
    // Some players first report the old position once more after a jump, and only then the new one: the hold ends when
    // the reported position is close to where the finger left it, or after three seconds.
    val hold = held?.takeIf { (target, at, _) ->
        SystemClock.elapsedRealtime() - at < 3000 && kotlin.math.abs(position(SystemClock.elapsedRealtime()) - target * duration) > 1500
    }
    val shown = drag ?: hold?.first ?: (position(clock.longValue) / duration.toFloat())
    val wave = remember { androidx.compose.ui.graphics.Path() }

    Column(
        Modifier
            .fillMaxWidth()
            .then(
                if (np.canSeek && np.durationMs > 0) {
                    Modifier
                        .pointerInput(np.durationMs) {
                            detectTapGestures { offset ->
                                context.graph.haptics.play(Haptics.Kind.TICK)
                                jump((offset.x / size.width).coerceIn(0f, 1f))
                            }
                        }
                        .pointerInput(np.durationMs) {
                            detectHorizontalDragGestures(
                                onDragStart = { drag = (it.x / size.width).coerceIn(0f, 1f); context.graph.haptics.play(Haptics.Kind.TICK) },
                                onDragEnd = { drag?.let { jump(it) }; drag = null },
                                onDragCancel = { drag = null },
                            ) { change, _ ->
                                change.consume()
                                drag = (change.position.x / size.width).coerceIn(0f, 1f)
                            }
                        }
                } else Modifier,
            ),
    ) {
        Canvas(Modifier.fillMaxWidth().height(28.dp).graphicsLayer {}) {
            val h = thickness.toPx()
            val mid = size.height / 2
            val handleW = (4f + 2f * knob).dp.toPx()
            val handleH = (20f + 8f * knob).dp.toPx()
            val gap = 4.dp.toPx()
            val cx = (size.width * shown).coerceIn(handleW / 2, size.width - handleW / 2)

            val restFrom = cx + handleW / 2 + gap + h / 2
            val restTo = size.width - h / 2
            if (restTo > restFrom) drawLine(track, Offset(restFrom, mid), Offset(restTo, mid), h, StrokeCap.Round)

            val playedFrom = h / 2
            val playedTo = cx - handleW / 2 - gap - h / 2
            if (playedTo > playedFrom) {
                val height = amplitude.coerceAtLeast(0f) * WaveAmplitude.toPx()
                if (height < 0.05f) {
                    drawLine(fill, Offset(playedFrom, mid), Offset(playedTo, mid), h, StrokeCap.Round)
                } else {
                    val length = WaveLength.toPx()
                    val taper = WaveTaper.toPx()
                    val shift = phase.floatValue * 2f * PI.toFloat()
                    wave.reset()
                    var x = playedFrom
                    while (true) {
                        val near = ((playedTo - x) / taper).coerceIn(0f, 1f)
                        val y = mid + height * near * near * (3f - 2f * near) * sin((x - playedFrom) / length * 2f * PI.toFloat() - shift)
                        if (x == playedFrom) wave.moveTo(x, y) else wave.lineTo(x, y)
                        if (x >= playedTo) break
                        x = minOf(x + 3f, playedTo)
                    }
                    drawPath(wave, fill, style = Stroke(h, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
            }
            drawRoundRect(fill, Offset(cx - handleW / 2, mid - handleH / 2), Size(handleW, handleH), CornerRadius(handleW / 2))
        }
        rememberNow(1000)
        val at = (drag ?: hold?.first)?.let { (it * duration).toLong() } ?: position(SystemClock.elapsedRealtime())
        Row(Modifier.fillMaxWidth()) {
            TimeText(formatClock(at))
            Spacer(Modifier.weight(1f))
            TimeText("-" + formatClock(duration - at))
        }
    }
}

/** 20 steps a second: the wave drifts about a dp per step, more would only cost battery. */
private const val WaveFrameMs = 50L

/** How far the wave travels per millisecond, in wavelengths. A little over half a wavelength a second. */
private const val WaveCyclesPerMs = 0.00055f

private val WaveAmplitude = 3.dp
private val WaveLength = 34.dp
private val WaveTaper = 18.dp

@Composable
private fun TimeText(text: String) = Text(text, color = IslandInk.Secondary, fontSize = 12.sp, fontWeight = FontWeight.Medium, style = TabularNumbers)

/**
 * The media volume as a Material 3 Expressive slider along the bottom, its bottom corners concentric with the island.
 * The volume is set off the main thread and only when it really changes.
 */
@Composable
private fun VolumeBar(hub: IslandHub, np: NowPlaying, concentric: Boolean) {
    // On a speaker elsewhere (a Sonos, a Chromecast) the bar is that speaker's volume, with its name on it.
    val remote = np.remote?.takeIf { it.adjustable }
    val phone by hub.musicVolume.collectAsState()
    val volume = remote?.level ?: phone
    val requests = remember { kotlinx.coroutines.flow.MutableStateFlow<Float?>(null) }
    val target by androidx.compose.runtime.rememberUpdatedState(remote to np.commands)
    LaunchedEffect(Unit) {
        var last = -1f
        requests.collect { f ->
            if (f == null || kotlin.math.abs(f - last) < 0.01f) return@collect
            last = f
            val (speaker, commands) = target
            withContext(Dispatchers.Default) { if (speaker != null) commands.setRemoteVolume(f) else hub.setMusicVolume?.invoke(f) }
        }
    }
    ExpressiveSlider(
        value = volume,
        onChange = { f, _ -> requests.value = f },
        icon = when {
            remote != null -> DripIcons.SpeakerGroup
            volume <= 0.001f -> DripIcons.VolumeOff
            else -> DripIcons.VolumeUp
        },
        label = remote?.name,
        concentric = concentric,
        steps = remote?.steps ?: 0,
    )
}
