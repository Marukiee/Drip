package nl.markmaaktmedia.drip.island.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.delay
import nl.markmaaktmedia.drip.graph
import nl.markmaaktmedia.drip.island.Haptics

/** One button of a bottom row. */
data class RowButton(
    val label: String?,
    val icon: Painter?,
    val container: Color,
    val content: Color,
    val weight: Float = 1f,
    val onClick: () -> Unit,
)

/**
 * The buttons along the bottom of a card, joined like a Material 3 Expressive button group. The two outer bottom corners
 * follow the island (concentric with its corner), the corners that face a neighbour or the content above stay small, and
 * a pressed button rounds its corners out and dips, the expressive way of saying it was pressed.
 */
@Composable
fun ConcentricButtonRow(buttons: List<RowButton>, modifier: Modifier = Modifier, height: Dp = 52.dp) {
    val m = LocalCardMetrics.current
    Row(modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        buttons.forEachIndexed { index, button ->
            RowButtonView(
                button = button,
                first = index == 0,
                last = index == buttons.lastIndex,
                outer = m.buttonOuterRadius,
                inner = m.buttonInnerRadius,
                height = height,
            )
        }
    }
}

@Composable
private fun RowScope.RowButtonView(button: RowButton, first: Boolean, last: Boolean, outer: Dp, inner: Dp, height: Dp) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val context = LocalContext.current
    // Pressed, every corner swells towards a pill; let go, they spring back to where they line up with the island.
    val pill = height / 2
    val round by animateDpAsState(if (pressed) pill else inner, spring(dampingRatio = 0.6f, stiffness = 500f), label = "corner")
    val outerNow by animateDpAsState(if (pressed) pill else outer, spring(dampingRatio = 0.6f, stiffness = 500f), label = "outer")
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "dip")
    val shape = RoundedCornerShape(
        topStart = round, topEnd = round,
        bottomStart = if (first) outerNow else round,
        bottomEnd = if (last) outerNow else round,
    )
    Box(
        Modifier
            .weight(button.weight)
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(button.container)
            .clickable(interactionSource = source, indication = null) {
                context.graph.haptics.play(Haptics.Kind.TAP)
                button.onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (button.icon != null) Icon(button.icon, contentDescription = button.label, tint = button.content, modifier = Modifier.size(22.dp))
            if (button.label != null && (button.icon == null || button.weight >= 1f)) {
                Text(button.label, color = button.content, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A round icon button with an expressive press, for controls in the middle of a card. */
@Composable
fun IslandIconButton(
    icon: Painter,
    description: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    container: Color = Color.Transparent,
    tint: Color = IslandInk.Primary,
    shape: Shape? = null,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val context = LocalContext.current
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.45f, stiffness = 700f), label = "dip")
    val corner by animateDpAsState(if (pressed) size / 4 else size / 2, spring(dampingRatio = 0.6f, stiffness = 500f), label = "corner")
    Box(
        modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape ?: RoundedCornerShape(corner))
            .background(container)
            .clickable(interactionSource = source, indication = null) {
                context.graph.haptics.play(Haptics.Kind.TAP)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** A picture clipped to [shape], or the fallback icon on a soft surface when there is none. */
@Composable
fun Picture(bitmap: Bitmap?, size: Dp, shape: Shape, fallback: Painter? = null, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(shape).background(IslandInk.SurfaceHigh), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(remember(bitmap) { bitmap.asImageBitmap() }, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else if (fallback != null) {
            Icon(fallback, contentDescription = null, tint = IslandInk.Secondary, modifier = Modifier.size(size * 0.5f))
        }
    }
}

/** The app an island card belongs to: its icon, small and round. */
@Composable
fun AppIcon(bitmap: Bitmap?, packageName: String?, size: Dp = 20.dp) {
    val context = LocalContext.current
    val icon = bitmap ?: remember(packageName) {
        packageName?.let { pkg ->
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(pkg)
                val px = (size.value * context.resources.displayMetrics.density).toInt().coerceAtLeast(16)
                drawable.toBitmap(px, px)
            }.getOrNull()
        }
    }
    if (icon != null) Picture(icon, size, CircleShape)
}

/**
 * Waits [ms] and then for the next frame. With the screen off no frames come ([nl.markmaaktmedia.drip.island.IslandWindow.setAwake]),
 * so a clock that ticks with this rests until the screen is back.
 */
suspend fun tickAfter(ms: Long) {
    delay(ms)
    withFrameMillis { }
}

/** The wall clock, ticking every [stepMs], for timers and call durations. */
@Composable
fun rememberNow(stepMs: Long = 1000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(stepMs) {
        while (true) {
            now = System.currentTimeMillis()
            tickAfter(stepMs - (now % stepMs).coerceAtMost(stepMs - 1))
        }
    }
    return now
}

/** 1:05 or 1:02:05. */
fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** 47 min or 1 h 20 min, for the time a battery needs. */
fun formatSpan(ms: Long): String {
    val minutes = (ms / 60_000).coerceAtLeast(1)
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}

@Composable
fun Title(text: String, modifier: Modifier = Modifier, size: Int = 17) =
    Text(text, modifier = modifier, color = IslandInk.Primary, fontSize = size.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)

@Composable
fun Subtitle(text: String, modifier: Modifier = Modifier, lines: Int = 1) =
    Text(text, modifier = modifier, color = IslandInk.Secondary, fontSize = 14.sp, maxLines = lines, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
