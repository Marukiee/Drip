package nl.markmaaktmedia.drip.island.ui

import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import nl.markmaaktmedia.drip.island.Alert
import nl.markmaaktmedia.drip.island.AlertKind
import nl.markmaaktmedia.drip.island.IslandContent
import nl.markmaaktmedia.drip.island.IslandController
import nl.markmaaktmedia.drip.island.IslandHub
import nl.markmaaktmedia.drip.island.IslandLayout
import nl.markmaaktmedia.drip.island.LiveKind
import nl.markmaaktmedia.drip.island.PrivacyInfo
import nl.markmaaktmedia.drip.island.Stage
import androidx.compose.ui.graphics.asImageBitmap
import nl.markmaaktmedia.drip.ui.theme.DripIcons

/** Draws what belongs in the island for one [Scene]. */
@Composable
fun SceneContent(scene: Scene, privacy: PrivacyInfo, controller: IslandController, hub: IslandHub, layout: IslandLayout) {
    val density = LocalDensity.current
    val m = LocalCardMetrics.current
    when (scene.stage) {
        Stage.HIDDEN -> Unit
        Stage.IDLE -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            PrivacyDots(privacy, Modifier.padding(end = with(density) { (layout.idleHeight / 2f).toDp() } - 3.dp))
        }
        Stage.COMPACT -> {
            val height = with(density) { layout.compactHeight.toDp() }
            CompactContent(scene.content, privacy, height, Modifier.fillMaxSize())
        }
        Stage.MINI -> {
            val alert = (scene.content as? IslandContent.Notice)?.alert
            val height = with(density) { layout.compactHeight.toDp() }
            if (alert != null) MiniContent(alert, height, Modifier.fillMaxSize())
        }
        Stage.ALERT -> {
            val alert = (scene.content as? IslandContent.Notice)?.alert
            if (alert != null) AlertPopup(alert, layout, controller, hub)
        }
        Stage.EXPANDED -> Column(Modifier.fillMaxWidth().padding(top = m.topInset).padding(m.padding)) {
            when (val c = scene.content) {
                is IslandContent.Music -> MusicCard(
                    c.playing, hub, layout.settings, controller.players.collectAsState().value,
                    onPlayer = { controller.showPlayer(it, step = playerStep(controller.players.value, c.playing.packageName, it)) },
                    onSwipe = { controller.switchPlayer(it) },
                )
                is IslandContent.Call -> CallCard(c.call)
                is IslandContent.Live -> LiveCard(c.live)
                is IslandContent.Charging -> ChargingCard(c.battery)
                // The live state, not the one from when the card opened: after a tap on the switch it must show off.
                is IslandContent.Torch -> TorchCard(hub.torch.collectAsState().value, hub)
                is IslandContent.Privacy -> PrivacyCard(hub.privacy.collectAsState().value) { controller.collapse() }
                is IslandContent.Notice -> when (c.alert.kind) {
                    // The slim announcements open into the card of what they are about.
                    AlertKind.VOLUME, AlertKind.RINGER -> VolumeCard(hub)
                    AlertKind.CHARGING, AlertKind.BATTERY_FULL, AlertKind.UNPLUGGED -> ChargingCard(hub.battery.collectAsState().value)
                    else -> NoticeCard(c.alert, controller, hub)
                }
                null -> Unit
            }
        }
    }
}

/** Which way the card slides going from the player of [from] to that of [to]: on through the list, or back. */
private fun playerStep(players: List<nl.markmaaktmedia.drip.island.NowPlaying>, from: String, to: String): Int {
    val a = players.indexOfFirst { it.packageName == from }
    val b = players.indexOfFirst { it.packageName == to }
    return if (b < a) -1 else 1
}

/**
 * The closed island with something going on: a small thing at each end. Each sits in the middle of the round end of the
 * island, the same distance from the top, the side and the bottom, so a cover or an icon is concentric with the curve
 * around it instead of being pushed into it.
 */
@Composable
private fun CompactContent(content: IslandContent?, privacy: PrivacyInfo, height: Dp, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val gap = (height * 0.15f).coerceIn(3.dp, 6.dp)
    val slot = height - gap * 2
    // Text cannot be round, so it ends where the curve of the end begins to close in.
    val textInset = height / 2 - 4.dp
    Box(modifier) {
        val start = Modifier.align(Alignment.CenterStart).padding(start = gap).size(slot)
        val endSlot = Modifier.align(Alignment.CenterEnd).padding(end = gap).size(slot)
        val endText = Modifier.align(Alignment.CenterEnd).padding(end = textInset)
        val icon = slot * 0.72f
        when (content) {
            is IslandContent.Music -> {
                val playing = content.playing.playing
                val shrink by animateFloatAsState(if (playing) 1f else 0.82f, spring(0.55f, 420f), label = "compactCover")
                AnimatedContent(
                    targetState = content.playing.art, modifier = start,
                    transitionSpec = {
                        (scaleIn(spring(0.6f, 450f), initialScale = 0.5f) + fadeIn(tween(160)))
                            .togetherWith(scaleOut(tween(180), targetScale = 1.2f) + fadeOut(tween(160)))
                    },
                    label = "compactArt",
                ) { art ->
                    Picture(art, slot, CircleShape, DripIcons.Music, Modifier.graphicsLayer { scaleX = shrink; scaleY = shrink })
                }
                Box(endSlot, contentAlignment = Alignment.Center) {
                    Equalizer(accent, playing, Modifier.size(slot * 0.62f, slot * 0.5f))
                }
            }
            is IslandContent.Call -> {
                Box(start, contentAlignment = Alignment.Center) {
                    Icon(DripIcons.PhoneInTalkFilled, null, tint = IslandInk.Green, modifier = Modifier.size(icon))
                }
                val begin = content.call.startedAt
                if (begin != null) {
                    val now = rememberNow()
                    SlotText(formatClock(now - begin), IslandInk.Green, endText)
                } else {
                    SlotText(content.call.caller, IslandInk.Green, endText)
                }
            }
            is IslandContent.Live -> {
                val live = content.live
                val color = live.accent?.let { Color(it) } ?: accent
                when {
                    // A route shows its turn arrow, a download its own symbol: what it is about, not which app.
                    live.kind == LiveKind.NAVIGATION && live.picture != null ->
                        androidx.compose.foundation.Image(
                            remember(live.picture) { live.picture.asImageBitmap() }, null,
                            modifier = start.padding(slot * 0.08f),
                        )
                    live.finished -> Box(start, contentAlignment = Alignment.Center) {
                        Icon(DripIcons.CheckCircleFilled, null, tint = color, modifier = Modifier.size(icon))
                    }
                    live.kind == LiveKind.DOWNLOAD || live.kind == LiveKind.NAVIGATION ->
                        Box(start, contentAlignment = Alignment.Center) { Icon(liveIcon(live.kind), null, tint = color, modifier = Modifier.size(icon)) }
                    live.appIcon != null -> Picture(live.appIcon, slot, CircleShape, null, start)
                    else -> Box(start, contentAlignment = Alignment.Center) { Icon(liveIcon(live.kind), null, tint = color, modifier = Modifier.size(icon)) }
                }
                val base = live.chronometerBase
                when {
                    live.kind == LiveKind.NAVIGATION -> (live.shortText ?: live.title).let { SlotText(it, IslandInk.Primary, endText) }
                    live.finished && live.shortText != null -> SlotText(live.shortText, color, endText)
                    base != null -> {
                        val now = rememberNow()
                        SlotText(formatClock(if (live.countDown) base - now else now - base), color, endText)
                    }
                    live.progress != null -> Box(endSlot, contentAlignment = Alignment.Center) {
                        ProgressRing(live.progress, color, Modifier.size(slot * 0.8f))
                    }
                    live.shortText != null -> SlotText(live.shortText, IslandInk.Primary, endText)
                    else -> Unit
                }
            }
            is IslandContent.Charging -> {
                Box(start, contentAlignment = Alignment.Center) {
                    Icon(DripIcons.BoltFilled, null, tint = IslandInk.Green, modifier = Modifier.size(icon))
                }
                SlotText("${content.battery.level}%", IslandInk.Green, endText)
            }
            is IslandContent.Torch -> {
                Box(start, contentAlignment = Alignment.Center) {
                    Icon(DripIcons.FlashlightFilled, null, tint = IslandInk.Yellow, modifier = Modifier.size(icon))
                }
            }
            is IslandContent.Privacy -> PrivacyCompact(content.privacy, start, endSlot, slot)
            else -> Unit
        }
        if (privacy.camera || privacy.microphone) {
            PrivacyDots(privacy, Modifier.align(Alignment.TopCenter).padding(top = gap))
        }
    }
}

/** A clock, a percentage or a word at the end of the closed island. Numbers roll when they change. */
@Composable
private fun SlotText(text: String, color: Color, modifier: Modifier) {
    if (text.length <= 9 && text.any(Char::isDigit)) RollingText(text, color, 14.sp, modifier)
    else Text(text, modifier = modifier, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/**
 * A state or a level in the closed island: the symbol in the round end on the left, a slim bar or a word on the right.
 * Moving the volume on and on only moves the bar.
 */
@Composable
private fun MiniContent(alert: Alert, height: Dp, modifier: Modifier) {
    val accent = alertAccent(alert)
    val gap = (height * 0.15f).coerceIn(3.dp, 6.dp)
    val slot = height - gap * 2
    Box(modifier) {
        Box(Modifier.align(Alignment.CenterStart).padding(start = gap).size(slot), contentAlignment = Alignment.Center) {
            when {
                alert.image != null -> Picture(alert.image, slot, CircleShape)
                alert.iconRes != null -> Icon(painterResource(alert.iconRes), null, tint = accent, modifier = Modifier.size(slot * 0.72f))
            }
        }
        val level = alert.level
        val end = Modifier.align(Alignment.CenterEnd).padding(end = height / 2 - 2.dp)
        if (level != null && alert.kind != AlertKind.CHARGING && alert.kind != AlertKind.BATTERY_FULL) {
            val shown by animateFloatAsState(level, spring(1f, 700f), label = "level")
            LevelBar(shown, accent, end.size(72.dp, 5.dp))
        } else {
            val words = when (alert.kind) {
                AlertKind.CHARGING, AlertKind.BATTERY_FULL -> alert.text ?: alert.level?.let { "${(it * 100).toInt()}%" } ?: alert.title
                else -> alert.title
            }
            Text(words, end, color = accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A short announcement: what it is about on the left, the words in the middle, a bar for a level. */
/**
 * The colour of an announcement: what the app gave it, else the colour Android itself uses for that kind of thing
 * (green for charging, red for a battery that runs out, orange for battery saver), else the island's accent.
 */
@Composable
fun alertAccent(alert: Alert): Color = alert.accent?.let { Color(it) } ?: when (alert.kind) {
    AlertKind.CHARGING, AlertKind.BATTERY_FULL -> IslandInk.Green
    AlertKind.BATTERY_LOW, AlertKind.UNPLUGGED -> if (alert.kind == AlertKind.BATTERY_LOW) IslandInk.Red else IslandInk.Secondary
    AlertKind.BATTERY_SAVER -> IslandInk.Orange
    AlertKind.ALARM -> IslandInk.Orange
    else -> MaterialTheme.colorScheme.primary
}

/** What the small drop of a second thing shows: one picture or symbol, the most telling one. */
@Composable
fun SecondContent(content: IslandContent, slot: Dp) {
    val accent = MaterialTheme.colorScheme.primary
    val icon = slot * 0.8f
    Box(Modifier.size(slot), contentAlignment = Alignment.Center) {
        when (content) {
            is IslandContent.Music -> Picture(content.playing.art, slot, CircleShape, DripIcons.Music)
            is IslandContent.Call -> Icon(DripIcons.PhoneInTalkFilled, null, tint = IslandInk.Green, modifier = Modifier.size(icon))
            is IslandContent.Live -> {
                val live = content.live
                val color = live.accent?.let { Color(it) } ?: accent
                when {
                    live.kind == LiveKind.NAVIGATION && live.picture != null ->
                        androidx.compose.foundation.Image(remember(live.picture) { live.picture.asImageBitmap() }, null, Modifier.size(icon))
                    live.progress != null -> ProgressRing(live.progress, color, Modifier.size(slot * 0.9f))
                    else -> Icon(liveIcon(live.kind), null, tint = color, modifier = Modifier.size(icon))
                }
            }
            is IslandContent.Charging -> Icon(DripIcons.BoltFilled, null, tint = IslandInk.Green, modifier = Modifier.size(icon))
            is IslandContent.Torch -> Icon(DripIcons.FlashlightFilled, null, tint = IslandInk.Yellow, modifier = Modifier.size(icon))
            is IslandContent.Notice -> Icon(DripIcons.Notifications, null, tint = accent, modifier = Modifier.size(icon))
            is IslandContent.Privacy -> content.privacy.kinds().firstOrNull()?.let { PrivacyGlyph(it, slot) }
        }
    }
}

/** A filled bar for a level, round at the ends. */
@Composable
fun LevelBar(level: Float, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(color.copy(alpha = 0.25f), cornerRadius = r)
        drawRoundRect(color, size = Size(size.width * level.coerceIn(0f, 1f), size.height), cornerRadius = r)
    }
}

/**
 * Four bars that bob like a level meter while music plays. On pause they sink, one after the other, into four calm dots;
 * when the music comes back they spring up again.
 */
@Composable
private fun Equalizer(color: Color, playing: Boolean, modifier: Modifier) {
    // This runs for as long as a song plays, so it is the island's biggest cost. Bars this small lose nothing at half the
    // frame rate; while paused it stops asking for frames at all, once the bars have sunk.
    val clock = remember { mutableLongStateOf(0L) }
    LaunchedEffect(playing) {
        val end = if (playing) Long.MAX_VALUE else SystemClock.uptimeMillis() + EqSinkMs
        while (SystemClock.uptimeMillis() < end) {
            withFrameMillis { clock.longValue = it }
            delay(EqFrameMs)
        }
    }
    val lift = (0 until EqBars).map { i ->
        animateFloatAsState(if (playing) 1f else 0f, spring(0.6f, 260f - i * 30f), label = "lift$i")
    }
    val tint by animateColorAsState(if (playing) color else IslandInk.Secondary, tween(300), label = "eqTint")
    // A layer of its own, so a frame of the bars leaves the rest of the island alone.
    Canvas(modifier.graphicsLayer { }) {
        val gap = size.width * 0.12f
        val w = (size.width - gap * (EqBars - 1)) / EqBars
        for (i in 0 until EqBars) {
            val h = w + (size.height * eqLevel(clock.longValue, i) - w) * lift[i].value
            drawRoundRect(tint, Offset(i * (w + gap), (size.height - h) / 2), Size(w, h), CornerRadius(w / 2, w / 2))
        }
    }
}

private const val EqBars = 4

/** Waits this long for the next frame: every second one at 60 Hz, every third at 120 Hz. */
private const val EqFrameMs = 24L

/** About how long the slowest bar takes to sink when the music pauses. */
private const val EqSinkMs = 900L
private val EqOffsets = longArrayOf(0, 180, 90, 270)

/** How high bar [i] stands at [t]: up and down in its own time, so the four never move as one. */
private fun eqLevel(t: Long, i: Int): Float {
    val stroke = 420L + i * 70L
    val at = (t + EqOffsets[i]) % (stroke * 2)
    val x = if (at < stroke) at.toFloat() / stroke else 2f - at.toFloat() / stroke
    return 0.25f + 0.75f * FastOutSlowInEasing.transform(x)
}

@Composable
fun ProgressRing(progress: Float, color: Color, modifier: Modifier, stroke: Dp = 2.5.dp) {
    Canvas(modifier) {
        val s = stroke.toPx()
        drawArc(color.copy(alpha = 0.25f), 0f, 360f, false, style = Stroke(s), topLeft = Offset(s / 2, s / 2), size = Size(size.width - s, size.height - s))
        drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, style = Stroke(s, cap = StrokeCap.Round), topLeft = Offset(s / 2, s / 2), size = Size(size.width - s, size.height - s))
    }
}

/** Green for a camera in use, orange for a microphone, as Android itself shows them. */
@Composable
fun PrivacyDots(privacy: PrivacyInfo, modifier: Modifier = Modifier) {
    if (!privacy.camera && !privacy.microphone) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (privacy.camera) Box(Modifier.size(6.dp).clip(CircleShape).background(IslandInk.Green))
        if (privacy.microphone) Box(Modifier.size(6.dp).clip(CircleShape).background(IslandInk.Orange))
    }
}

@Composable
fun liveIcon(kind: LiveKind) = when (kind) {
    LiveKind.TIMER -> DripIcons.Timer
    LiveKind.STOPWATCH -> DripIcons.Hourglass
    LiveKind.NAVIGATION -> DripIcons.Navigation
    LiveKind.DELIVERY -> DripIcons.Delivery
    LiveKind.RIDE -> DripIcons.Car
    LiveKind.RECORDING -> DripIcons.Mic
    LiveKind.DOWNLOAD -> DripIcons.Download
    LiveKind.INSTALL -> DripIcons.InstallMobile
    LiveKind.CALENDAR -> DripIcons.Calendar
    LiveKind.SPORTS -> DripIcons.Sports
    LiveKind.GENERIC -> DripIcons.Notifications
}
