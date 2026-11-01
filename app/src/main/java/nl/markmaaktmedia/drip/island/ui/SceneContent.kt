package nl.markmaaktmedia.drip.island.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import nl.markmaaktmedia.drip.island.Alert
import nl.markmaaktmedia.drip.island.IslandContent
import nl.markmaaktmedia.drip.island.IslandController
import nl.markmaaktmedia.drip.island.IslandHub
import nl.markmaaktmedia.drip.island.IslandLayout
import nl.markmaaktmedia.drip.island.LiveKind
import nl.markmaaktmedia.drip.island.PrivacyInfo
import nl.markmaaktmedia.drip.island.Stage
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
            val side = with(density) { (layout.compactHeight / 2f).toDp() }
            CompactContent(scene.content, privacy, Modifier.fillMaxSize().padding(horizontal = side * 0.55f + 4.dp))
        }
        Stage.ALERT -> {
            val alert = (scene.content as? IslandContent.Notice)?.alert
            val top = with(density) { (if (layout.notch) layout.fit.statusBarHeight.toFloat() else 0f).toDp() }
            if (alert != null) AlertContent(alert, Modifier.fillMaxSize().padding(top = top).padding(horizontal = 14.dp, vertical = 8.dp))
        }
        Stage.EXPANDED -> Column(Modifier.fillMaxWidth().padding(top = m.topInset).padding(m.padding)) {
            when (val c = scene.content) {
                is IslandContent.Music -> MusicCard(c.playing, hub)
                is IslandContent.Call -> CallCard(c.call)
                is IslandContent.Live -> LiveCard(c.live)
                is IslandContent.Charging -> ChargingCard(c.battery)
                is IslandContent.Torch -> TorchCard(c.torch, hub)
                is IslandContent.Notice -> NoticeCard(c.alert, controller)
                null -> Unit
            }
        }
    }
}

/** The closed island with something going on: a small thing on the left, a small thing on the right. */
@Composable
private fun CompactContent(content: IslandContent?, privacy: PrivacyInfo, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        when (content) {
            is IslandContent.Music -> {
                Picture(content.playing.art, 22.dp, RoundedCornerShape(6.dp), DripIcons.Music)
                Spacer(Modifier.weight(1f))
                if (content.playing.playing) Equalizer(accent, Modifier.size(22.dp, 16.dp))
                else Icon(DripIcons.PauseFilled, null, tint = IslandInk.Secondary, modifier = Modifier.size(18.dp))
            }
            is IslandContent.Call -> {
                Icon(DripIcons.PhoneInTalkFilled, null, tint = IslandInk.Green, modifier = Modifier.size(20.dp))
                Spacer(Modifier.weight(1f))
                val start = content.call.startedAt
                if (start != null) {
                    val now = rememberNow()
                    SlotText(formatClock(now - start), IslandInk.Green)
                } else {
                    SlotText(content.call.caller, IslandInk.Green)
                }
            }
            is IslandContent.Live -> {
                val live = content.live
                if (live.appIcon != null) Picture(live.appIcon, 20.dp, CircleShape)
                else Icon(liveIcon(live.kind), null, tint = accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.weight(1f))
                val base = live.chronometerBase
                when {
                    base != null -> {
                        val now = rememberNow()
                        SlotText(formatClock(if (live.countDown) base - now else now - base), live.accent?.let { Color(it) } ?: accent)
                    }
                    live.progress != null -> ProgressRing(live.progress, live.accent?.let { Color(it) } ?: accent, Modifier.size(18.dp))
                    live.shortText != null -> SlotText(live.shortText, IslandInk.Primary)
                    else -> Unit
                }
            }
            is IslandContent.Charging -> {
                Icon(DripIcons.BoltFilled, null, tint = IslandInk.Green, modifier = Modifier.size(20.dp))
                Spacer(Modifier.weight(1f))
                SlotText("${content.battery.level}%", IslandInk.Green)
            }
            is IslandContent.Torch -> {
                Icon(DripIcons.FlashlightFilled, null, tint = IslandInk.Yellow, modifier = Modifier.size(20.dp))
                Spacer(Modifier.weight(1f))
                PrivacyDots(privacy)
            }
            else -> Spacer(Modifier.weight(1f))
        }
        if (content !is IslandContent.Torch && (privacy.camera || privacy.microphone)) {
            Spacer(Modifier.width(6.dp))
            PrivacyDots(privacy)
        }
    }
}

@Composable
private fun SlotText(text: String, color: Color) =
    Text(text, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)

/** A short announcement: what it is about on the left, the words in the middle, a bar for a level. */
@Composable
private fun AlertContent(alert: Alert, modifier: Modifier) {
    val accent = alert.accent?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            alert.image != null -> Picture(alert.image, 40.dp, CircleShape)
            alert.iconRes != null -> Box(Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                Icon(painterResource(alert.iconRes), null, tint = accent, modifier = Modifier.size(22.dp))
            }
            else -> AppIcon(null, alert.packageName, 36.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Title(alert.title, size = 15)
            val level = alert.level
            if (level != null && alert.text == null) {
                Spacer(Modifier.height(8.dp))
                LevelBar(level, accent, Modifier.fillMaxWidth().height(6.dp))
            } else if (alert.text != null) {
                Subtitle(alert.text)
            }
        }
        if (alert.level != null && alert.text != null) {
            Text("${(alert.level * 100).toInt()}%", color = accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        if (alert.image != null && alert.packageName != null) AppIcon(null, alert.packageName, 18.dp)
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

/** Four bars that bob like a level meter while music plays. */
@Composable
private fun Equalizer(color: Color, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "eq")
    val phases = listOf(0, 180, 90, 270).mapIndexed { i, offset ->
        t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(420 + i * 70, delayMillis = 0), RepeatMode.Reverse, initialStartOffset = androidx.compose.animation.core.StartOffset(offset)), label = "bar$i")
    }
    Canvas(modifier) {
        val bars = phases.size
        val gap = size.width * 0.12f
        val w = (size.width - gap * (bars - 1)) / bars
        phases.forEachIndexed { i, p ->
            val h = size.height * p.value
            drawRoundRect(color, Offset(i * (w + gap), (size.height - h) / 2), Size(w, h), CornerRadius(w / 2, w / 2))
        }
    }
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
    LiveKind.GENERIC -> DripIcons.Notifications
}
