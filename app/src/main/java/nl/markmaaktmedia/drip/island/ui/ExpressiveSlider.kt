package nl.markmaaktmedia.drip.island.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.markmaaktmedia.drip.graph
import nl.markmaaktmedia.drip.island.Haptics
import kotlin.math.abs
import kotlin.math.min

/**
 * A Material 3 Expressive slider, the kind of the volume sliders of Android 16: a thick filled part, a slim upright
 * handle with a small gap on both sides, the lighter rest of the track and a stop dot at its end. The handle narrows
 * while it is held. The outer corners are round; with [concentric] the bottom ones follow the island instead.
 *
 * Nothing jumps: a tap glides to where it landed, and a drag that starts away from the handle glides over to the finger
 * first. From then on the fill keeps pace with the finger exactly. Let go, and it stays where it was left: while the
 * system catches up, and after that for as long as what [value] reports is that same step, so it never hops to the exact
 * value of the step. Only a slider with a few coarse steps (the torch) settles on its step, with a spring.
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
    // Where the finger is on the track while it drags.
    var finger by remember { mutableFloatStateOf(0f) }
    var held by remember { mutableStateOf<Pair<Float, Long>?>(null) }
    // A speaker with a hundred steps (a Sonos) moves two of them per button press: those must show, not only a run of
    // them. One step off is the speaker rounding what it was sent, and the bar stays where the finger left it.
    val sameStep = if (steps > 0) min(SAME_STEP, 1.5f / steps) else SAME_STEP
    // Let go of only when the system reports something else than where it was left (a volume button, another app).
    LaunchedEffect(held, value) {
        val (f, at) = held ?: return@LaunchedEffect
        val wait = HOLD_MS - (SystemClock.elapsedRealtime() - at)
        if (wait > 0) delay(wait)
        if (abs(f - value) > sameStep) held = null
    }
    val shown = remember { Animatable(value, SETTLE) }
    val target = held?.first ?: value
    LaunchedEffect(target, dragging) { if (!dragging) shown.animateTo(target, GLIDE) }
    val coarse = steps in 1..COARSE_STEPS
    val handle by animateDpAsState(if (dragging) 2.dp else 4.dp, spring(0.6f, 600f), label = "handle")
    var widthPx by remember { mutableStateOf(1f) }
    var lastStep by remember { mutableStateOf(-1) }

    fun set(x: Float, final: Boolean) {
        val f = (x / widthPx).coerceIn(0f, 1f)
        val snapped = if (steps > 0) Math.round(f * steps).toFloat() / steps else f
        val rest = if (final && coarse) snapped else f
        if (final) scope.launch { shown.animateTo(rest, if (coarse) spring(0.7f, 600f, SETTLE) else GLIDE) } else finger = rest
        if (steps > 0) {
            val step = Math.round(f * steps)
            if (step != lastStep) {
                lastStep = step
                context.graph.haptics.play(Haptics.Kind.TICK)
            }
        }
        onChange(snapped, final)
        if (final) held = rest to SystemClock.elapsedRealtime()
    }

    // The bar moves with the finger frame by frame; only the distance to where the finger took hold closes with a spring,
    // so the pace of the finger itself is never smoothed away.
    fun follow(from: Float) {
        finger = (from / widthPx).coerceIn(0f, 1f)
        scope.launch {
            val gap = Animatable(shown.value - finger, SETTLE)
            val closing = launch { gap.animateTo(0f, spring(1f, 600f, SETTLE)) }
            while (true) {
                withFrameNanos { }
                if (!dragging) break
                shown.snapTo((finger + gap.value).coerceIn(0f, 1f))
            }
            closing.cancel()
        }
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
                    onDragStart = {
                        dragging = true
                        lastStep = -1
                        context.graph.haptics.play(Haptics.Kind.TICK)
                        follow(it.x)
                    },
                    // Not dragging first, so the last frame of following cannot cut off the glide to where it was left.
                    onDragEnd = { dragging = false; set(finger * widthPx, final = true) },
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
            // Clear of the stop dot and the round end of the track.
            val endPx = with(density) { outer.toPx() }
            val iconPx = with(density) { if (icon != null) 20.dp.toPx() else 0f }
            val labelStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            val measurer = rememberTextMeasurer()
            // The whole icon and name, measured once: how much room they would like, whatever there is.
            val needPx = remember(label, iconPx, labelStyle) {
                iconPx + (label?.let { (if (icon != null) with(density) { 8.dp.toPx() } else 0f) + measurer.measure(it, labelStyle, maxLines = 1).size.width } ?: 0f)
            }
            // Where the handle is, the same sums as the drawing above, and the room on either side of it.
            fun hwPx() = with(density) { handle.toPx() }
            fun handleX(): Float =
                (shown.value.coerceIn(0f, 1f) * widthPx).coerceIn(hwPx() / 2, (widthPx - hwPx() / 2).coerceAtLeast(hwPx() / 2))
            fun roomInside() = handleX() - hwPx() / 2 - gapPx - start * 2
            fun afterX() = handleX() + hwPx() / 2 + gapPx + start * 0.85f
            fun roomAfter() = widthPx - endPx - afterX()

            // In the fill when it fits there, else after the handle; when neither fits, where there is more room. Once on a
            // side it stays a little past the point where it would just fit, so a bar resting there does not make it flit.
            val margin = with(density) { 12.dp.toPx() }
            var inside by remember { mutableStateOf<Boolean?>(null) }
            fun wantInside(): Boolean {
                val fits = roomInside() >= needPx + if (inside == true) -margin else margin
                return when {
                    fits -> true
                    roomAfter() >= needPx -> false
                    else -> roomInside() >= roomAfter()
                }
            }
            // 0 in the fill, 1 after the handle. Changing sides fades out at the one and back in at the other, so the name
            // never jumps across.
            val side = remember { Animatable(0f) }
            LaunchedEffect(needPx) {
                snapshotFlow { if (widthPx > 1f) wantInside() else null }.collect { want ->
                    if (want == null || want == inside) return@collect
                    val first = inside == null
                    inside = want
                    if (first) side.snapTo(if (want) 0f else 1f) else launch { side.animateTo(if (want) 0f else 1f, tween(220)) }
                }
            }
            val showInside by remember { derivedStateOf { side.value < 0.5f } }
            val tint = if (showInside) onActive else MaterialTheme.colorScheme.onSurfaceVariant
            androidx.compose.foundation.layout.Row(
                Modifier.layout { measurable, constraints ->
                    val inFill = side.value < 0.5f
                    val room = if (inFill) roomInside() else roomAfter()
                    // Never wider than the room: the name ends in an ellipsis instead of running off the track.
                    val p = measurable.measure(constraints.copy(minWidth = 0, maxWidth = room.toInt().coerceAtLeast(0)))
                    layout(p.width, p.height) {
                        val x = if (inFill) start else afterX()
                        p.placeRelativeWithLayer(x.toInt(), 0) {
                            val fade = kotlin.math.abs(1f - 2f * side.value)
                            // Too little room for even the icon: it fades away rather than being cut off.
                            val fits = if (iconPx > 0f) ((room - iconPx * 0.6f) / (iconPx * 0.4f)).coerceIn(0f, 1f) else 1f
                            alpha = fade * fits
                        }
                    }
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
                if (label != null) {
                    Text(
                        label, color = tint, style = labelStyle, modifier = Modifier.padding(start = if (icon != null) 8.dp else 0.dp),
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * How the fill moves to a new place: a tap, a volume button, another app. It does not overshoot, and it gets going at
 * once, so a volume button shows within a frame or two; a run of steps still flows as one movement.
 */
private val GLIDE = spring(1f, 700f, SETTLE)
private const val HOLD_MS = 1500L
/**
 * When a glide counts as arrived: half a pixel on a bar as wide as the phone. Each spring needs it itself; without, it
 * stops a hundredth of the way short, about ten pixels here, and the rest of every step jumped in one frame: most of a
 * Sonos step of two.
 */
private const val SETTLE = 0.0005f
/**
 * Further apart than this, [value] is a different step and no longer the one the finger left it at. Sliders that give
 * their steps use less than one of those.
 */
private const val SAME_STEP = 0.05f
private const val COARSE_STEPS = 8
