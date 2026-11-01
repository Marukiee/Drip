package nl.markmaaktmedia.drip.island.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import nl.markmaaktmedia.drip.graph
import nl.markmaaktmedia.drip.island.Haptics
import kotlin.math.abs

/**
 * A Material 3 Expressive slider, the kind of the volume sliders of Android 16: a thick filled part, a slim upright
 * handle with a small gap on both sides, the lighter rest of the track and a stop dot at its end. The handle narrows
 * while it is held. The outer corners are round; with [concentric] the bottom ones follow the island instead.
 *
 * Under the finger the fill follows it exactly. Let go, it stays where it was left until [value] (what the system
 * reports) catches up or a second and a half has passed, so it never jumps back for a moment.
 *
 * The icon and label sit at the start of the filled part. When that is too short to hold them they move to just after
 * the handle, on the track, as the Material 3 slider does, so the handle never runs through them.
 */
@Composable
fun ExpressiveSlider(
    value: Float,
    onChange: (fraction: Float, final: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    label: String? = null,
    concentric: Boolean = false,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
    onActive: Color = MaterialTheme.colorScheme.onPrimary,
    height: Dp = 44.dp,
    steps: Int = 0,
) {
    val m = LocalCardMetrics.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf<Pair<Float, Long>?>(null) }
    val hold = held?.takeIf { (f, at) -> SystemClock.elapsedRealtime() - at < 1500 && abs(f - value) > 0.04f }
    val shown = remember { Animatable(value) }
    LaunchedEffect(hold?.first ?: value, dragging) { if (!dragging) shown.animateTo(hold?.first ?: value, spring(0.9f, 500f)) }
    val handle by animateDpAsState(if (dragging) 2.dp else 4.dp, spring(0.6f, 600f), label = "handle")
    var widthPx by remember { mutableStateOf(1f) }
    var contentPx by remember { mutableStateOf(0f) }
    var lastStep by remember { mutableStateOf(-1) }

    fun set(x: Float, final: Boolean) {
        val f = (x / widthPx).coerceIn(0f, 1f)
        val snapped = if (steps > 0) Math.round(f * steps).toFloat() / steps else f
        scope.launch { shown.snapTo(if (final && steps > 0) snapped else f) }
        if (steps > 0) {
            val step = Math.round(f * steps)
            if (step != lastStep) {
                lastStep = step
                context.graph.haptics.play(Haptics.Kind.TICK)
            }
        }
        onChange(snapped, final)
        if (final) held = snapped to SystemClock.elapsedRealtime()
    }

    val outer = height / 2
    val bottomOuter = if (concentric) m.buttonOuterRadius.coerceAtMost(height) else outer
    val path = remember { Path() }
    Box(
        modifier
            .fillMaxWidth()
            .height(height + 8.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) { detectTapGestures { set(it.x, final = true) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true; lastStep = -1; context.graph.haptics.play(Haptics.Kind.TICK) },
                    onDragEnd = { set(shown.value * widthPx, final = true); dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    change.consume()
                    set(change.position.x, final = false)
                }
            }
            .drawBehind {
                val trackH = height.toPx()
                val top = (size.height - trackH) / 2
                val gap = 4.dp.toPx()
                val hw = handle.toPx()
                val inner = 4.dp.toPx()
                val o = outer.toPx()
                val bo = bottomOuter.toPx()
                val x = (shown.value.coerceIn(0f, 1f) * size.width).coerceIn(hw / 2, size.width - hw / 2)

                // The rest of the track, from just after the handle to the end.
                val restFrom = x + hw / 2 + gap
                if (restFrom < size.width) {
                    path.reset()
                    path.addRoundRect(
                        RoundRect(
                            restFrom, top, size.width, top + trackH,
                            topLeftCornerRadius = CornerRadius(inner), topRightCornerRadius = CornerRadius(o),
                            bottomRightCornerRadius = CornerRadius(bo), bottomLeftCornerRadius = CornerRadius(inner),
                        ),
                    )
                    drawPath(path, trackColor)
                    // The stop dot at the end of the track.
                    val dot = 2.dp.toPx()
                    if (size.width - restFrom > o) drawCircle(activeColor, dot, Offset(size.width - o * 0.75f, top + trackH / 2))
                }
                // The filled part, up to just before the handle.
                val fillTo = x - hw / 2 - gap
                if (fillTo > 0f) {
                    path.reset()
                    path.addRoundRect(
                        RoundRect(
                            0f, top, fillTo, top + trackH,
                            topLeftCornerRadius = CornerRadius(o), topRightCornerRadius = CornerRadius(inner),
                            bottomRightCornerRadius = CornerRadius(inner), bottomLeftCornerRadius = CornerRadius(bo),
                        ),
                    )
                    drawPath(path, activeColor)
                }
                // The handle, a little taller than the track.
                drawRoundRect(activeColor, Offset(x - hw / 2, 0f), Size(hw, size.height), CornerRadius(hw / 2))
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        if (icon != null || label != null) {
            val start = with(density) { 14.dp.toPx() }
            val gapPx = with(density) { 4.dp.toPx() }
            // Where the handle is and whether the icon and label fit before it, the same sums as the drawing below.
            fun handleX(): Float {
                val hw = with(density) { handle.toPx() }
                return (shown.value.coerceIn(0f, 1f) * widthPx).coerceIn(hw / 2, (widthPx - hw / 2).coerceAtLeast(hw / 2))
            }
            val inside by remember { derivedStateOf { handleX() - with(density) { handle.toPx() } / 2 - gapPx >= start * 2 + contentPx } }
            val tint = if (inside) onActive else MaterialTheme.colorScheme.onSurfaceVariant
            androidx.compose.foundation.layout.Row(
                Modifier
                    .onSizeChanged { contentPx = it.width.toFloat() }
                    .offset {
                        val x = if (inside) start else handleX() + handle.toPx() / 2 + gapPx + start * 0.85f
                        androidx.compose.ui.unit.IntOffset(x.toInt(), 0)
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
                if (label != null) Text(label, color = tint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp), maxLines = 1)
            }
        }
    }
}
