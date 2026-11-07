package nl.markmaaktmedia.drip.island.ui

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.island.Alert
import nl.markmaaktmedia.drip.island.AlertMessage
import nl.markmaaktmedia.drip.island.IslandController
import nl.markmaaktmedia.drip.island.IslandHub
import nl.markmaaktmedia.drip.island.IslandLayout
import nl.markmaaktmedia.drip.ui.theme.DripIcons

/** "now", "4 min", or the time of day, kept up to date while it is on screen. */
@Composable
fun rememberAgo(postedAt: Long?): String? {
    postedAt ?: return null
    val context = LocalContext.current
    val now = rememberNow(30_000L)
    val minutes = ((now - postedAt) / 60_000L).coerceAtLeast(0)
    return when {
        minutes < 1 -> stringResource(R.string.island_now)
        minutes < 60 -> stringResource(R.string.island_minutes_ago, minutes.toInt())
        else -> DateFormat.getTimeFormat(context).format(postedAt)
    }
}

/**
 * Who it is from: a contact photo with the app's icon as a small badge on it, the app's own icon, or one of Drip's
 * symbols on a soft disc of the colour of the thing.
 */
@Composable
fun NoticeAvatar(alert: Alert, size: Dp, accent: Color, badgeDelay: Int = 0) {
    val badge = remember(alert.id) { Animatable(if (badgeDelay == 0) 1f else 0f) }
    LaunchedEffect(alert.id) {
        if (badgeDelay > 0) {
            delay(badgeDelay.toLong())
            badge.animateTo(1f, spring(0.5f, 500f))
        }
    }
    Box(Modifier.size(size)) {
        when {
            alert.image != null -> Picture(alert.image, size, CircleShape)
            alert.iconRes != null -> Box(Modifier.size(size).clip(CircleShape).background(accent.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                Icon(painterResource(alert.iconRes), null, tint = accent, modifier = Modifier.size(size * 0.5f))
            }
            else -> AppIcon(null, alert.packageName, size)
        }
        if (alert.personImage && alert.packageName != null) {
            val b = size * 0.4f
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(b * 0.12f, b * 0.12f)
                    .size(b)
                    .graphicsLayer { scaleX = badge.value; scaleY = badge.value }
                    .clip(CircleShape)
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) { AppIcon(null, alert.packageName, b - 4.dp) }
        }
    }
}

/**
 * A message as it comes in, in the notch. The photo sits in the round bottom left end of the island, the same distance
 * from the side and the bottom, and a reply button does the same in the right end, so both are concentric with the
 * curve around them. In between: who, when, and the message. It all comes in one after the other: the photo pops, the
 * app's badge lands on it, the words rise in and the button follows.
 */
@Composable
fun AlertPopup(alert: Alert, layout: IslandLayout, controller: IslandController, hub: IslandHub) {
    val density = LocalDensity.current
    val accent = alertAccent(alert)
    val g = layout.geometry(nl.markmaaktmedia.drip.island.Stage.ALERT)
    val h = with(density) { g.height.toDp() }
    val r = with(density) { g.clamped().bottomRadius.toDp() }
    val photo = 46.dp
    // Centred on the middle of the round end: concentric with it.
    val inset = (r - photo / 2).coerceAtLeast(8.dp)
    val middle = h - r
    val replyAction = alert.actions.firstOrNull { it.reply != null }
    val button = 40.dp

    val appear = remember(alert.id) { Animatable(0f) }
    val follow = remember(alert.id) { Animatable(0f) }
    LaunchedEffect(alert.id) { appear.animateTo(1f, spring(0.62f, 380f)) }
    LaunchedEffect(alert.id) {
        delay(90)
        follow.animateTo(1f, spring(0.7f, 320f))
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .offset(x = inset, y = middle - photo / 2)
                .graphicsLayer {
                    val s = 0.55f + 0.45f * appear.value
                    scaleX = s; scaleY = s
                    alpha = appear.value.coerceIn(0f, 1f)
                },
        ) { NoticeAvatar(alert, photo, accent, badgeDelay = 220) }

        val textStart = inset + photo + 12.dp
        val textEnd = if (replyAction != null) (r - button / 2).coerceAtLeast(10.dp) + button + 10.dp else (r * 0.6f).coerceAtLeast(14.dp)
        Column(
            Modifier
                .padding(start = textStart, end = textEnd)
                .fillMaxWidth()
                .offset(y = middle - IslandLayout.ALERT_WORDS_ABOVE_MIDDLE_DP.dp)
                .graphicsLayer {
                    translationY = (1f - follow.value) * 8.dp.toPx()
                    alpha = follow.value.coerceIn(0f, 1f)
                },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    alert.title, color = IslandInk.Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                alert.conversation?.let {
                    Text(
                        " · $it", color = IslandInk.Tertiary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                rememberAgo(alert.postedAt)?.let {
                    Text(it, color = IslandInk.Tertiary, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                }
            }
            val level = alert.level
            when {
                level != null && alert.text == null -> LevelBar(level, accent, Modifier.padding(top = 9.dp).fillMaxWidth().height(6.dp))
                alert.text != null -> AnimatedContent(
                    alert.text,
                    transitionSpec = {
                        (slideInVertically(spring(0.8f, 500f)) { it / 2 } + fadeIn(tween(160)))
                            .togetherWith(slideOutVertically(tween(140)) { -it / 2 } + fadeOut(tween(100)))
                    },
                    label = "alertText",
                ) { text ->
                    Text(text, color = IslandInk.Secondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
                }
            }
        }

        if (replyAction != null) {
            val right = (r - button / 2).coerceAtLeast(10.dp)
            IslandIconButton(
                DripIcons.Reply, replyAction.title,
                {
                    hub.replyTo.value = alert.id
                    controller.expand()
                },
                size = button, iconSize = 20.dp,
                container = accent.copy(alpha = 0.24f), tint = accent,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = -right, y = middle - button / 2)
                    .graphicsLayer {
                        scaleX = follow.value; scaleY = follow.value
                        alpha = follow.value.coerceIn(0f, 1f)
                    },
            )
        }
    }
}

/**
 * The top of an opened message: the app and when, then who it is from (and in which group), then what they said. A
 * conversation shows its last few messages as bubbles, the person's own on the right.
 */
@Composable
fun NoticeHead(alert: Alert, accent: Color) {
    if (alert.appLabel != null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppIcon(null, alert.packageName, 18.dp)
            Text(alert.appLabel, color = IslandInk.Secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            rememberAgo(alert.postedAt)?.let { Text("· $it", color = IslandInk.Tertiary, fontSize = 13.sp, maxLines = 1) }
        }
        Spacer(Modifier.size(12.dp))
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NoticeAvatar(alert, 48.dp, accent)
        Column(Modifier.weight(1f)) {
            Title(alert.title, size = 18)
            alert.conversation?.let { Subtitle(it) }
        }
    }
    val bubbles = alert.messages.size >= 2
    if (bubbles) {
        Spacer(Modifier.size(12.dp))
        MessageBubbles(alert.messages, group = alert.conversation != null, accent = accent)
    } else {
        alert.text?.let {
            Spacer(Modifier.size(10.dp))
            Text(it, color = IslandInk.Primary.copy(alpha = 0.88f), fontSize = 15.sp, lineHeight = 21.sp, maxLines = 8, overflow = TextOverflow.Ellipsis)
        }
    }
    alert.picture?.let { picture ->
        Spacer(Modifier.size(12.dp))
        val ratio = picture.width.toFloat() / picture.height.coerceAtLeast(1)
        Image(
            remember(picture) { picture.asImageBitmap() }, null, contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .aspectRatio(ratio.coerceIn(0.75f, 2.4f))
                .clip(RoundedCornerShape(18.dp)),
        )
    }
}

@Composable
private fun MessageBubbles(messages: List<AlertMessage>, group: Boolean, accent: Color) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        messages.forEachIndexed { i, m ->
            val sameAsBefore = i > 0 && messages[i - 1].sender == m.sender && messages[i - 1].mine == m.mine
            val sameAsNext = i < messages.lastIndex && messages[i + 1].sender == m.sender && messages[i + 1].mine == m.mine
            val big = 18.dp
            val small = 5.dp
            val shape = if (m.mine) {
                RoundedCornerShape(big, if (sameAsBefore) small else big, if (sameAsNext) small else big, big)
            } else {
                RoundedCornerShape(if (sameAsBefore) small else big, big, big, if (sameAsNext) small else big)
            }
            if (i > 0 && !sameAsBefore) Spacer(Modifier.size(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
                Column(
                    Modifier
                        .widthIn(max = 290.dp)
                        .clip(shape)
                        .background(if (m.mine) accent.copy(alpha = 0.3f) else IslandInk.Surface)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    if (group && !m.mine && !sameAsBefore && m.sender != null) {
                        Text(m.sender, color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    Text(m.text, color = IslandInk.Primary, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
