package nl.markmaaktmedia.drip.island.ui

import android.media.AudioManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.graph
import nl.markmaaktmedia.drip.island.Haptics
import nl.markmaaktmedia.drip.island.IslandHub
import nl.markmaaktmedia.drip.ui.theme.DripIcons
import androidx.compose.runtime.collectAsState

/**
 * The opened volume: what the slim volume bar in the notch opens into. Media, ringer and alarm as bars under the finger,
 * the ringer mode as a button group, and where the sound plays.
 */
@Composable
fun VolumeCard(hub: IslandHub) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    // Read again and again while the card is open, so a volume button shows at once.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { tickAfter(100); tick++ } }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(DripIcons.VolumeUp, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Title(stringResource(R.string.island_volume), Modifier.weight(1f), size = 18)
        IslandIconButton(DripIcons.SpeakerGroup, stringResource(R.string.island_output), { hub.openOutput?.invoke() }, size = 40.dp, iconSize = 21.dp, tint = IslandInk.Secondary)
    }
    Spacer(Modifier.height(12.dp))

    val mode = remember(tick) { runCatching { audio.ringerMode }.getOrDefault(AudioManager.RINGER_MODE_NORMAL) }
    RingerGroup(mode) { wanted -> runCatching { audio.ringerMode = wanted } }
    Spacer(Modifier.height(12.dp))

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Music on a speaker elsewhere comes first: that is what the volume buttons move now.
        val playing by hub.nowPlaying.collectAsState()
        playing?.let { np ->
            np.remote?.takeIf { it.adjustable }?.let { speaker ->
                ExpressiveSlider(
                    value = speaker.level,
                    onChange = { f, _ -> np.commands.setRemoteVolume(f) },
                    icon = DripIcons.SpeakerGroup, label = speaker.name ?: np.appLabel,
                    height = 40.dp, steps = speaker.steps,
                )
            }
        }
        StreamBar(audio, AudioManager.STREAM_MUSIC, DripIcons.Music, stringResource(R.string.island_media), tick, last = false)
        StreamBar(audio, AudioManager.STREAM_RING, DripIcons.Notifications, stringResource(R.string.island_ring), tick, last = false)
        StreamBar(audio, AudioManager.STREAM_ALARM, DripIcons.Alarm, stringResource(R.string.island_alarm), tick, last = true)
    }
}

/** Sound, vibrate, silent: joined like a Material 3 Expressive button group, the one in use filled. */
@Composable
private fun RingerGroup(mode: Int, onPick: (Int) -> Unit) {
    val context = LocalContext.current
    val options = listOf(
        Triple(AudioManager.RINGER_MODE_NORMAL, DripIcons.Notifications, R.string.island_ringer_sound),
        Triple(AudioManager.RINGER_MODE_VIBRATE, DripIcons.Vibration, R.string.island_ringer_vibrate),
        Triple(AudioManager.RINGER_MODE_SILENT, DripIcons.NotificationsOff, R.string.island_ringer_silent),
    )
    Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEachIndexed { i, (value, icon, label) ->
            val on = value == mode
            val container by animateColorAsState(if (on) MaterialTheme.colorScheme.primary else IslandInk.Surface, tween(220), label = "ringer$i")
            val content by animateColorAsState(if (on) MaterialTheme.colorScheme.onPrimary else IslandInk.Primary, tween(220), label = "ringerText$i")
            // Joined like a Material 3 Expressive button group; the chosen one lets go of its neighbours and becomes a
            // pill of its own, its inner corners rounding out on a spring.
            val outer = 22.dp
            val inner by animateDpAsState(if (on) outer else 6.dp, spring(0.6f, 500f), label = "ringerCorner$i")
            val shape = when (i) {
                0 -> RoundedCornerShape(outer, inner, inner, outer)
                options.lastIndex -> RoundedCornerShape(inner, outer, outer, inner)
                else -> RoundedCornerShape(inner)
            }
            Row(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(shape)
                    .background(container)
                    .pointerInput(value) {
                        detectTapGestures {
                            context.graph.haptics.play(Haptics.Kind.TAP)
                            onPick(value)
                        }
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = content, modifier = Modifier.size(18.dp))
                Text(stringResource(label), color = content, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp), maxLines = 1)
            }
        }
    }
}

/**
 * One stream as a Material 3 Expressive slider. The last one has its bottom corners concentric with the island. The
 * volume is set off the main thread and only on a real step.
 */
@Composable
private fun StreamBar(audio: AudioManager, stream: Int, icon: Painter, label: String, tick: Int, last: Boolean) {
    val scope = rememberCoroutineScope()
    val max = remember(stream) { runCatching { audio.getStreamMaxVolume(stream) }.getOrDefault(15).coerceAtLeast(1) }
    val min = remember(stream) { runCatching { audio.getStreamMinVolume(stream) }.getOrDefault(0) }
    val actual = remember(tick) { runCatching { audio.getStreamVolume(stream) }.getOrDefault(0) }
    var lastIndex by remember { mutableIntStateOf(actual) }
    ExpressiveSlider(
        value = (actual - min).toFloat() / (max - min).coerceAtLeast(1),
        onChange = { f, _ ->
            val index = min + Math.round(f * (max - min))
            if (index != lastIndex) {
                lastIndex = index
                scope.launch(Dispatchers.Default) { runCatching { audio.setStreamVolume(stream, index, 0) } }
            }
        },
        icon = icon, label = label, concentric = last, height = 40.dp, steps = max - min,
    )
}
