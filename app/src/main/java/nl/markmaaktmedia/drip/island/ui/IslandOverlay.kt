package nl.markmaaktmedia.drip.island.ui

import android.graphics.Rect
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import nl.markmaaktmedia.drip.data.CardStyle
import nl.markmaaktmedia.drip.data.GestureAction
import nl.markmaaktmedia.drip.data.IslandSettings
import nl.markmaaktmedia.drip.graph
import nl.markmaaktmedia.drip.island.DeviceFit
import nl.markmaaktmedia.drip.island.Haptics
import nl.markmaaktmedia.drip.island.IslandContent
import nl.markmaaktmedia.drip.island.IslandController
import nl.markmaaktmedia.drip.island.IslandGeometry
import nl.markmaaktmedia.drip.island.IslandHub
import nl.markmaaktmedia.drip.island.IslandLayout
import nl.markmaaktmedia.drip.island.IslandWindow
import nl.markmaaktmedia.drip.island.Stage
import nl.markmaaktmedia.drip.ui.theme.resolve
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The island on screen: one shape that moves between the stages, and the content that fades in and out inside it.
 *
 * The shape is six animated numbers (see [IslandGeometry]) that are only read while drawing and placing, so a frame of
 * movement never recomposes anything. Width and height run on their own springs: opening, the island widens first and then
 * pours down, like a drop that swells before it lets go; closing, it pulls up first and then narrows.
 */
@Composable
fun IslandOverlay(
    controller: IslandController,
    fit: StateFlow<DeviceFit>,
    settings: Flow<IslandSettings>,
    hub: IslandHub,
    window: IslandWindow,
    onGesture: (GestureAction, IslandContent?) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val ui by controller.ui.collectAsState()
    val deviceFit by fit.collectAsState()
    // The saved settings before the first frame, so the island does not start in the default shape and then jump.
    val initialSettings = remember { runBlocking { withTimeoutOrNull(300) { settings.first() } ?: IslandSettings() } }
    val s by settings.collectAsState(initialSettings)
    val layout = remember(deviceFit, s) { IslandLayout(deviceFit, s) }
    val origin = window.origin

    // Heights of opened cards as last measured, so the next opening aims for the right height at once.
    val measured = remember { mutableStateMapOf<String, Float>() }
    val content = ui.content
    val expandedHeight = content?.let { measured[it.key] } ?: estimateHeight(content, density)
    val target = layout.geometry(ui.stage, expandedHeight)

    val shape = remember { AnimatedGeometry(target) }
    val press = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // Moving between stages: grow the window to hold both shapes, run the springs, then fit the window to where it ended.
    LaunchedEffect(target, s.bounce, s.speed) {
        window.setTouchable(ui.stage != Stage.HIDDEN)
        window.setBounds(windowRect(shape.value.bounds, target.bounds, layout))
        window.setMoving(true)
        val opening = target.height > shape.value.height
        shape.animateTo(target, opening, s.bounce, s.speed)
        window.setMoving(false)
        window.setBounds(windowRect(target.bounds, target.bounds, layout))
    }

    val seed = remember(s) { nl.markmaaktmedia.drip.ui.theme.ColourSeed.WALLPAPER.resolve(context) }
    val accent = contentAccent(content, seed, s)
    val fill by animateColorAsState(
        if (s.cardStyle == CardStyle.TINTED && ui.stage == Stage.EXPANDED) islandScheme(accent).surfaceContainer else Color.Black,
        tween(320), label = "fill",
    )

    val metrics = CardMetrics(
        padding = with(density) { layout.cardPadding.toDp() },
        islandBottomRadius = with(density) { layout.expandedBottomRadius.toDp() },
        buttonOuterRadius = with(density) { layout.buttonOuterRadius.toDp() },
        buttonInnerRadius = with(density) { layout.buttonInnerRadius.toDp() },
        topInset = with(density) { layout.cardTopInset.toDp() },
    )

    val stage by rememberUpdatedState(ui.stage)
    val current by rememberUpdatedState(content)
    val gestures by rememberUpdatedState(s)
    val viewConfig = LocalViewConfiguration.current
    val path = remember { Path() }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                    val o = origin.value
                    val g = shape.value.bounds
                    val inside = g.contains(Offset(down.position.x + o.left, down.position.y + o.top))
                    if (!inside) {
                        // A touch beside an opened card closes it, the way a tap outside a sheet does.
                        if (stage == Stage.EXPANDED) controller.collapse()
                        return@awaitEachGesture
                    }
                    if (stage == Stage.EXPANDED) controller.poke()
                    if (stage != Stage.EXPANDED) scope.launch { press.animateTo(1f, spring(0.6f, 900f)) }
                    var travel = Offset.Zero
                    var handledByChild = down.isConsumed
                    var longPressed = false
                    val longPress = viewConfig.longPressTimeoutMillis
                    val swipe = 24.dp.toPx()
                    val start = down.uptimeMillis
                    while (true) {
                        val event = withTimeoutOrNull(if (longPressed || handledByChild) Long.MAX_VALUE else max(1L, longPress - (currentTime() - start))) {
                            awaitPointerEvent(PointerEventPass.Main)
                        }
                        if (event == null) {
                            // Held still long enough: a long press, unless the finger already moved off.
                            if (!handledByChild && travel.getDistance() < swipe && stage != Stage.EXPANDED) {
                                longPressed = true
                                context.graph.haptics.play(Haptics.Kind.LONG_PRESS)
                                onGesture(gestures.gestureLongPress, current)
                            }
                            continue
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) handledByChild = true
                        travel += change.positionChange()
                        if (!change.pressed) {
                            if (!handledByChild && !longPressed) {
                                when {
                                    travel.getDistance() < swipe -> if (stage == Stage.EXPANDED) Unit else tap(gestures, current, onGesture, controller, press, scope)
                                    abs(travel.y) > abs(travel.x) && travel.y > 0 -> if (stage != Stage.EXPANDED) swipeAction(gestures.gestureSwipeDown, current, onGesture, context)
                                    abs(travel.y) > abs(travel.x) -> if (stage == Stage.EXPANDED) controller.collapse() else Unit
                                    travel.x < 0 -> swipeAction(gestures.gestureSwipeLeft, current, onGesture, context)
                                    else -> swipeAction(gestures.gestureSwipeRight, current, onGesture, context)
                                }
                            }
                            break
                        }
                    }
                    scope.launch { press.animateTo(0f, spring(0.5f, 500f)) }
                }
            }
            .drawBehind {
                val o = origin.value
                val g = pressed(shape.value, press.value, this.density)
                g.buildPath(path, o.left.toFloat(), o.top.toFloat())
                drawPath(path, fill)
            },
    ) {
        IslandTheme(seed = accent, metrics = metrics) {
            AnimatedContent(
                targetState = Scene(ui.stage, content),
                contentKey = { it.stage to it.content?.key },
                transitionSpec = {
                    (fadeIn(tween(200, delayMillis = 70)) + scaleIn(spring(0.8f, 500f), initialScale = 0.92f))
                        .togetherWith(fadeOut(tween(90)) + scaleOut(tween(140), targetScale = 0.96f))
                        .using(SizeTransform(clip = false) { _, _ -> snap() })
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        clip = true
                        this.shape = GeometryShape(shape, press, origin, density)
                    },
                label = "content",
            ) { scene ->
                val sceneTarget = layout.geometry(scene.stage, scene.content?.let { measured[it.key] } ?: estimateHeight(scene.content, density))
                Box(
                    Modifier
                        .fillMaxSize()
                        .blurWhileMoving(this)
                        .placedIn(shape, origin, sceneTarget, scene.stage == Stage.EXPANDED) { height ->
                            val key = scene.content?.key
                            if (scene.stage == Stage.EXPANDED && key != null && measured[key] != height) measured[key] = height
                        },
                ) {
                    SceneContent(scene, ui.privacy, controller, hub, layout)
                }
            }
        }
    }
}

/** What is inside the island at one moment: the stage and the thing shown. */
data class Scene(val stage: Stage, val content: IslandContent?)

private fun currentTime() = android.os.SystemClock.uptimeMillis()

private fun tap(
    s: IslandSettings,
    content: IslandContent?,
    onGesture: (GestureAction, IslandContent?) -> Unit,
    controller: IslandController,
    press: Animatable<Float, AnimationVector1D>,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    if (s.gestureTap == GestureAction.EXPAND && content == null) {
        // Nothing to open: the island gives a little bounce so the tap is not lost.
        scope.launch {
            press.animateTo(1.6f, spring(0.4f, 1200f))
            press.animateTo(0f, spring(0.4f, 600f))
        }
        return
    }
    onGesture(s.gestureTap, content)
}

private fun swipeAction(action: GestureAction, content: IslandContent?, onGesture: (GestureAction, IslandContent?) -> Unit, context: android.content.Context) {
    if (action == GestureAction.NONE) return
    // Next and previous only mean something while music is the thing shown.
    if ((action == GestureAction.NEXT_TRACK || action == GestureAction.PREVIOUS_TRACK) && content !is IslandContent.Music) return
    context.graph.haptics.play(Haptics.Kind.SWIPE)
    onGesture(action, content)
}

/** A pressed island swells a touch, from the top, the way a finger presses into something soft. */
private fun pressed(g: IslandGeometry, press: Float, density: Float): IslandGeometry {
    if (press == 0f) return g
    val grow = 3f * density * press
    return g.copy(width = g.width + grow * 2, height = g.height + grow)
}

private fun estimateHeight(content: IslandContent?, density: Density): Float = with(density) {
    when (content) {
        is IslandContent.Music -> 252.dp
        is IslandContent.Call -> 178.dp
        is IslandContent.Live -> 168.dp
        is IslandContent.Charging -> 150.dp
        is IslandContent.Torch -> 156.dp
        is IslandContent.Notice -> 150.dp
        null -> 120.dp
    }.toPx()
}

/** The window: the union of where the island is and where it goes, with a margin for the press and the springs. */
private fun windowRect(a: androidx.compose.ui.geometry.Rect, b: androidx.compose.ui.geometry.Rect, layout: IslandLayout): Rect {
    val slack = layout.fit.dp(10f)
    val left = (min(a.left, b.left) - slack).coerceAtLeast(0f)
    val right = (max(a.right, b.right) + slack).coerceAtMost(layout.fit.screenWidth.toFloat())
    val bottom = max(a.bottom, b.bottom) + slack
    return Rect(left.roundToInt(), 0, right.roundToInt(), bottom.roundToInt().coerceAtLeast(1))
}

@Composable
private fun contentAccent(content: IslandContent?, seed: Color, s: IslandSettings): Color {
    val art = (content as? IslandContent.Music)?.playing?.art
    val fromArt = rememberAccentOf(if (s.accentSource == nl.markmaaktmedia.drip.data.AccentSource.ALBUM_ART) art else null, seed)
    return when (content) {
        is IslandContent.Music -> fromArt
        is IslandContent.Live -> content.live.accent?.let { Color(it) } ?: seed
        is IslandContent.Notice -> content.alert.accent?.let { Color(it) } ?: seed
        is IslandContent.Call -> IslandInk.Green
        is IslandContent.Charging -> IslandInk.Green
        is IslandContent.Torch -> IslandInk.Yellow
        null -> seed
    }
}

/**
 * The six numbers of the shape, each on its own spring. Read [value] only while drawing or placing.
 */
class AnimatedGeometry(initial: IslandGeometry) {
    private val cx = Animatable(initial.centerX)
    private val top = Animatable(initial.top)
    private val width = Animatable(initial.width)
    private val height = Animatable(initial.height)
    private val corner = Animatable(initial.topCorner)
    private val bottom = Animatable(initial.bottomRadius)

    val value: IslandGeometry
        get() = IslandGeometry(cx.value, top.value, width.value, height.value, corner.value, bottom.value)

    suspend fun animateTo(target: IslandGeometry, opening: Boolean, bounce: Float, speed: Float) = coroutineScope {
        val b = bounce.coerceIn(0f, 1f)
        val v = speed.coerceIn(0.4f, 2.5f)
        // Opening is allowed some overshoot, closing barely any: a drop swells and wobbles, then snaps back up.
        val damping = if (opening) 0.92f - 0.34f * b else 0.98f - 0.16f * b
        val base = (if (opening) 300f else 420f) * v * v
        val wide = spring<Float>(damping, base)
        val tall = spring<Float>(min(1f, damping + 0.04f), if (opening) base * 0.72f else base * 1.25f)
        val calm = spring<Float>(1f, base)
        launch { cx.animateTo(target.centerX, calm) }
        launch { top.animateTo(target.top, calm) }
        launch { width.animateTo(target.width, wide) }
        launch { height.animateTo(target.height, tall) }
        launch { corner.animateTo(target.topCorner, calm) }
        launch { bottom.animateTo(target.bottomRadius, wide) }
    }
}

/** The animated island as a clip, read in the drawing phase only. */
private class GeometryShape(
    private val shape: AnimatedGeometry,
    private val press: Animatable<Float, AnimationVector1D>,
    private val origin: State<Rect>,
    private val density: Density,
) : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val o = origin.value
        return Outline.Generic(pressed(shape.value, press.value, density.density).buildPath(Path(), o.left.toFloat(), o.top.toFloat()))
    }
    override fun equals(other: Any?) = false
    override fun hashCode() = System.identityHashCode(this)
}

/**
 * Lays the content out at the size of its own stage and places it where the moving shape is, so the content is measured
 * once per stage and only moves (in the placement phase) while the shape slides. An opened card is measured at the full
 * width and as tall as it wants; [onHeight] reports that height so the shape can aim for it.
 */
private fun Modifier.placedIn(
    shape: AnimatedGeometry,
    origin: State<Rect>,
    target: IslandGeometry,
    wrapHeight: Boolean,
    onHeight: (Float) -> Unit,
) = this
    .layout { measurable, constraints ->
        val w = target.width.roundToInt().coerceAtLeast(1)
        val placeable = if (wrapHeight) {
            measurable.measure(Constraints(minWidth = w, maxWidth = w, minHeight = 0, maxHeight = Constraints.Infinity))
        } else {
            measurable.measure(Constraints.fixed(w, target.height.roundToInt().coerceAtLeast(1)))
        }
        if (wrapHeight) onHeight(placeable.height.toFloat())
        layout(constraints.maxWidth, constraints.maxHeight) {
            val o = origin.value
            val g = shape.value
            // Opened content keeps to the top of the island and grows with it; closed content stays centred in it.
            val x = g.centerX - o.left - w / 2f
            val y = if (wrapHeight) g.top - o.top else g.top - o.top + (g.height - placeable.height) / 2f
            val grow = if (wrapHeight) (g.width / target.width).coerceIn(0.7f, 1f) else 1f
            placeable.placeWithLayer(x.roundToInt(), y.roundToInt()) {
                scaleX = grow
                scaleY = grow
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
        }
    }

/** Content that comes or goes is a little out of focus while it does, like the island of the iPhone. */
@Composable
private fun Modifier.blurWhileMoving(scope: AnimatedVisibilityScope): Modifier {
    val blur by scope.transition.animateFloat(transitionSpec = { tween(200) }, label = "blur") { state ->
        if (state == EnterExitState.Visible) 0f else 10f
    }
    return graphicsLayer {
        val r = blur * density
        renderEffect = if (r > 0.5f) BlurEffect(r, r) else null
    }
}
