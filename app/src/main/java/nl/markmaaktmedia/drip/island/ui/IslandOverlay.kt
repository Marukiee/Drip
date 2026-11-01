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
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
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
import nl.markmaaktmedia.drip.data.Gesture
import nl.markmaaktmedia.drip.data.GestureAction
import nl.markmaaktmedia.drip.data.GestureScope
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

    // Heights of opened cards as last measured, so the next opening aims for the right height at once.
    val measured = remember { mutableStateMapOf<String, Float>() }
    val content = ui.content
    val expandedHeight = content?.let { measured[it.key] } ?: estimateHeight(content, density)
    val target = layout.geometry(ui.stage, expandedHeight)

    val shape = remember { AnimatedGeometry(target) }
    val press = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // The small drop of a second thing. Without one it waits tucked inside the island; with one it swells out of the
    // island's right end and lets go, the shoulders of the two meeting along the top edge like a neck of liquid.
    // While a card or an announcement covers the island the drop does not follow it down: it draws back up into the
    // edge where it hangs, quicker than the island grows, and comes down there again when the island closes.
    val second = ui.second.takeIf { ui.stage == Stage.COMPACT }
    val aside = ui.second != null && second == null
    val secondTarget = when {
        second != null -> layout.secondGeometry(target)
        aside -> layout.retractedSecond()
        ui.stage == Stage.COMPACT || ui.stage == Stage.IDLE -> layout.tuckedSecond(target)
        // Nothing to show and the island opening or gone: no size at all, so it cannot poke out of the moving card.
        else -> layout.tuckedSecond(target).copy(width = 0f, height = 0f)
    }
    val secondShape = remember { AnimatedGeometry(secondTarget) }
    var shownSecond by remember { mutableStateOf<IslandContent?>(null) }
    if (ui.second != null) shownSecond = ui.second
    LaunchedEffect(secondTarget, s.bounce, s.speed) {
        secondShape.animateTo(secondTarget, second != null, s.bounce, s.speed * if (aside) 1.4f else 0.9f)
        if (ui.second == null) shownSecond = null
    }
    val secondPath = remember { Path() }

    // Moving between stages: grow the window to hold both shapes, run the springs, then fit the window to where it ended.
    LaunchedEffect(target, s.bounce, s.speed) {
        window.setTouchable(ui.stage != Stage.HIDDEN)
        val from = shape.value.bounds
        // While something is on the island the window is already as tall as its opened card, so a tap opens it at once
        // instead of waiting for a taller window. The window lets every touch through, so its size costs nothing else.
        val ready = if (ui.stage == Stage.HIDDEN || ui.stage == Stage.IDLE) target.bounds else layout.geometry(Stage.EXPANDED, expandedHeight).bounds
        val tall = max(drawHeight(from, target.bounds, layout), drawHeight(ready, ready, layout))
        window.setHeight(tall)
        window.setTouchBounds(touchRect(from, withSecond(target.bounds, secondTarget, second != null), layout))
        // A taller window lands a frame or two later. Starting before it does would cut the growing shape off at the
        // old bottom edge for those frames, so wait for the window to be laid out at its new height first.
        withTimeoutOrNull(150) { snapshotFlow { window.laidOutHeight.intValue }.first { it >= tall } }
        window.setMoving(true)
        val opening = target.height > shape.value.height
        // A card that closes with nothing left behind (the torch switched off from it) first draws back into the closed
        // island and only then into the edge, the way it came, instead of dropping straight into a flat bar. The second
        // spring takes over mid-way with the speed the first one had, so it is one movement.
        if ((ui.stage == Stage.HIDDEN || ui.stage == Stage.IDLE) && shape.value.height > layout.compactHeight * 1.5f) {
            withTimeoutOrNull(220) { shape.animateTo(layout.geometry(Stage.COMPACT), false, s.bounce, s.speed) }
        }
        shape.animateTo(target, opening, s.bounce, s.speed)
        window.setMoving(false)
        window.setHeight(max(drawHeight(target.bounds, target.bounds, layout), drawHeight(ready, ready, layout)))
        val end = withSecond(target.bounds, secondTarget, second != null)
        window.setTouchBounds(touchRect(end, end, layout))
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
                // One handler for every touch on the island, so a tap, a double tap, a hold and the swipes never fight.
                // Buttons inside an opened card consume their own touches first; what they leave comes here.
                var lastTapAt = 0L
                var pendingTap: kotlinx.coroutines.Job? = null
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                    val o = window.originNow()
                    val at = Offset(down.position.x + o.x, down.position.y + o.y)
                    val onSecond = ui.second != null && stage == Stage.COMPACT && secondShape.value.bounds.contains(at) && !shape.value.bounds.contains(at)
                    if (onSecond) {
                        // The small drop: a tap opens what it shows; anything else on it is left alone.
                        val up = waitForUpOrCancellation()
                        if (up != null) controller.expandSecond()
                        return@awaitEachGesture
                    }
                    val inside = shape.value.bounds.contains(at)
                    if (!inside) {
                        // A touch beside an opened card closes it, the way a tap outside a sheet does.
                        if (stage == Stage.EXPANDED) controller.collapse()
                        return@awaitEachGesture
                    }
                    val s = gestures
                    val scopeOf = gestureScope(current)
                    if (stage == Stage.EXPANDED) controller.poke()
                    if (stage != Stage.EXPANDED) scope.launch { press.animateTo(1f, spring(0.6f, 900f)) }
                    var travel = Offset.Zero
                    var handled = down.isConsumed
                    var longPressed = false
                    val longPress = viewConfig.longPressTimeoutMillis
                    val swipe = 24.dp.toPx()
                    // Pulling the shade down must feel as if the island were not there: it opens as soon as the finger
                    // heads down, not when it lets go.
                    val pull = 10.dp.toPx()
                    val start = down.uptimeMillis
                    while (true) {
                        val event = withTimeoutOrNull(if (longPressed || handled) Long.MAX_VALUE else max(1L, longPress - (currentTime() - start))) {
                            awaitPointerEvent(PointerEventPass.Main)
                        }
                        if (event == null) {
                            if (!handled && travel.getDistance() < swipe && stage != Stage.EXPANDED) {
                                longPressed = true
                                context.graph.haptics.play(Haptics.Kind.LONG_PRESS)
                                onGesture(s.actionFor(scopeOf, Gesture.LONG_PRESS), current)
                            }
                            continue
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) handled = true
                        travel += change.positionChange()
                        val downAction = s.actionFor(scopeOf, Gesture.SWIPE_DOWN)
                        if (!handled && !longPressed && stage != Stage.EXPANDED && downAction == GestureAction.NOTIFICATIONS &&
                            travel.y > pull && travel.y > abs(travel.x)
                        ) {
                            handled = true
                            onGesture(GestureAction.NOTIFICATIONS, current)
                        }
                        if (!change.pressed) {
                            if (!handled && !longPressed) {
                                when {
                                    travel.getDistance() < swipe -> when (stage) {
                                        // Tapping where the closed island sits closes the opened one again.
                                        Stage.EXPANDED -> if (layout.geometry(Stage.COMPACT).bounds.inflate(8.dp.toPx()).contains(at)) controller.collapse()
                                        // A slim announcement opens into the card of what it is about (the volume, the battery).
                                        Stage.MINI -> controller.expand()
                                        else -> {
                                            val double = s.actionFor(scopeOf, Gesture.DOUBLE_TAP)
                                            val now = currentTime()
                                            if (double != GestureAction.NONE && now - lastTapAt < viewConfig.doubleTapTimeoutMillis) {
                                                pendingTap?.cancel()
                                                lastTapAt = 0L
                                                context.graph.haptics.play(Haptics.Kind.TAP)
                                                onGesture(double, current)
                                            } else if (double != GestureAction.NONE) {
                                                // Wait a moment to see whether a second tap follows.
                                                lastTapAt = now
                                                val content = current
                                                pendingTap = scope.launch {
                                                    kotlinx.coroutines.delay(viewConfig.doubleTapTimeoutMillis)
                                                    tap(s.actionFor(scopeOf, Gesture.TAP), content, onGesture, press, scope)
                                                }
                                            } else {
                                                tap(s.actionFor(scopeOf, Gesture.TAP), current, onGesture, press, scope)
                                            }
                                        }
                                    }
                                    abs(travel.y) > abs(travel.x) && travel.y > 0 -> if (stage != Stage.EXPANDED) swipeAction(downAction, current, onGesture, context)
                                    abs(travel.y) > abs(travel.x) -> if (stage == Stage.EXPANDED) controller.collapse() else Unit
                                    travel.x < 0 -> swipeAction(s.actionFor(scopeOf, Gesture.SWIPE_LEFT), current, onGesture, context)
                                    else -> swipeAction(s.actionFor(scopeOf, Gesture.SWIPE_RIGHT), current, onGesture, context)
                                }
                            }
                            break
                        }
                    }
                    scope.launch { press.animateTo(0f, spring(0.5f, 500f)) }
                }
            }
            ,
    ) {
        IslandTheme(seed = accent, metrics = metrics) {
            // The small drop first, under the island, so an island that grows over it covers it, its icon too.
            Box(
                Modifier.fillMaxSize().drawBehind {
                    val o = window.originNow()
                    val g2 = secondShape.value
                    if (g2.width > 0.5f && g2.height > 0.5f) {
                        g2.buildPath(secondPath, o.x, o.y)
                        drawPath(secondPath, fill)
                    }
                },
            ) {
                shownSecond?.let { content -> SecondDrop(content, secondShape, shape, layout, window) }
            }
            // The opened card of what is showing, measured once out of sight, so the first tap knows how tall to open.
            val unmeasured = content?.takeIf { (ui.stage == Stage.COMPACT || ui.stage == Stage.ALERT) && measured[it.key] == null }
            if (unmeasured != null) {
                val width = layout.geometry(Stage.EXPANDED).width.roundToInt()
                Box(
                    Modifier.layout { measurable, _ ->
                        val p = measurable.measure(Constraints(minWidth = width, maxWidth = width, minHeight = 0, maxHeight = Constraints.Infinity))
                        measured[unmeasured.key] = p.height.toFloat()
                        layout(0, 0) {}
                    },
                ) {
                    SceneContent(Scene(Stage.EXPANDED, unmeasured), ui.privacy, controller, hub, layout)
                }
            }
            Box(
                Modifier.fillMaxSize().drawBehind {
                    val o = window.originNow()
                    val g = pressed(shape.value, press.value, this.density)
                    g.buildPath(path, o.x, o.y)
                    drawPath(path, fill)
                },
            ) {
                AnimatedContent(
                    targetState = Scene(ui.stage, content),
                    contentKey = { it.stage to it.content?.key },
                    transitionSpec = {
                        if (initialState.content?.key != null && initialState.content?.key == targetState.content?.key) {
                            // The same thing growing or shrinking (a message opening into its card): one crossfade that
                            // runs with the shape, so there is never a moment of empty black in between.
                            fadeIn(tween(260, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
                                .togetherWith(fadeOut(tween(200)))
                                .using(SizeTransform(clip = false) { _, _ -> snap() })
                        } else {
                            (fadeIn(tween(200, delayMillis = 70)) + scaleIn(spring(0.8f, 500f), initialScale = 0.92f))
                                .togetherWith(fadeOut(tween(90)) + scaleOut(tween(140), targetScale = 0.96f))
                                .using(SizeTransform(clip = false) { _, _ -> snap() })
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Read here, so this block runs again every frame the shape moves and the clip keeps up.
                            val o = window.originNow()
                            val g = pressed(shape.value, press.value, this.density)
                            clip = true
                            this.shape = GeometryShape(g, o)
                        },
                    label = "content",
                ) { scene ->
                    val sceneTarget = layout.geometry(scene.stage, scene.content?.let { measured[it.key] } ?: estimateHeight(scene.content, density))
                    Box(
                        Modifier
                            .fillMaxSize()
                            .blurWhileMoving(this)
                            .placedIn(shape, press, window, sceneTarget, scene.stage == Stage.EXPANDED) { height ->
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
}

/** What the small drop shows, placed where the drop is and as big as it is, so it shrinks and fades with it. */
@Composable
private fun SecondDrop(content: IslandContent, drop: AnimatedGeometry, main: AnimatedGeometry, layout: IslandLayout, window: IslandWindow) {
    val slot = with(LocalDensity.current) { (layout.compactHeight * 0.7f).toDp() }
    Box(
        Modifier.layout { measurable, constraints ->
            val p = measurable.measure(Constraints())
            layout(constraints.maxWidth, constraints.maxHeight) {
                val o = window.originNow()
                val g2 = drop.value
                val home = layout.tuckedSecond(main.value).centerX
                val away = layout.secondGeometry(main.value).centerX
                // Fades in as the drop lets go of the island, and out as it melts back in or draws up into the edge.
                val apart = ((g2.centerX - home) / (away - home).coerceAtLeast(1f)).coerceIn(0f, 1f)
                val size = (g2.height / layout.compactHeight.coerceAtLeast(1f)).coerceIn(0f, 1f)
                val shown = apart * size
                p.placeWithLayer((g2.centerX - o.x - p.width / 2f).roundToInt(), (g2.top - o.y + (g2.height - p.height) / 2f).roundToInt()) {
                    alpha = shown * shown
                    scaleX = 0.6f + 0.4f * shown
                    scaleY = 0.6f + 0.4f * shown
                }
            }
        },
    ) {
        SecondContent(content, slot)
    }
}

/** The touch window also covers the small drop while it is out. */
private fun withSecond(main: androidx.compose.ui.geometry.Rect, second: IslandGeometry, out: Boolean): androidx.compose.ui.geometry.Rect =
    if (!out) main else androidx.compose.ui.geometry.Rect(
        min(main.left, second.bounds.left), min(main.top, second.bounds.top),
        max(main.right, second.bounds.right), max(main.bottom, second.bounds.bottom),
    )

/** What is inside the island at one moment: the stage and the thing shown. */
data class Scene(val stage: Stage, val content: IslandContent?)

private fun currentTime() = android.os.SystemClock.uptimeMillis()

private fun tap(
    action: GestureAction,
    content: IslandContent?,
    onGesture: (GestureAction, IslandContent?) -> Unit,
    press: Animatable<Float, AnimationVector1D>,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    if (action == GestureAction.EXPAND && content == null) {
        // Nothing to open: the island gives a little bounce so the tap is not lost.
        scope.launch {
            press.animateTo(1.6f, spring(0.4f, 1200f))
            press.animateTo(0f, spring(0.4f, 600f))
        }
        return
    }
    onGesture(action, content)
}

/** Which set of gestures applies to what is showing. */
private fun gestureScope(content: IslandContent?): GestureScope = when (content) {
    is IslandContent.Music -> GestureScope.MUSIC
    is IslandContent.Call -> GestureScope.CALL
    is IslandContent.Live -> when (content.live.kind) {
        nl.markmaaktmedia.drip.island.LiveKind.TIMER, nl.markmaaktmedia.drip.island.LiveKind.STOPWATCH -> GestureScope.TIMER
        nl.markmaaktmedia.drip.island.LiveKind.NAVIGATION -> GestureScope.ROUTE
        else -> GestureScope.DEFAULT
    }
    is IslandContent.Charging -> GestureScope.CHARGING
    is IslandContent.Notice -> GestureScope.NOTICE
    else -> GestureScope.DEFAULT
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
        is IslandContent.Privacy -> (70 + 64 * listOf(content.privacy.camera, content.privacy.microphone, content.privacy.location).count { it }).dp
        is IslandContent.Notice -> when (content.alert.kind) {
            nl.markmaaktmedia.drip.island.AlertKind.VOLUME, nl.markmaaktmedia.drip.island.AlertKind.RINGER -> 250.dp
            else -> 150.dp
        }
        null -> 120.dp
    }.toPx()
}

/** The island window reaches down to the lower of where the island is and where it goes, with room for the springs. */
private fun drawHeight(a: androidx.compose.ui.geometry.Rect, b: androidx.compose.ui.geometry.Rect, layout: IslandLayout): Int =
    (max(a.bottom, b.bottom) + layout.fit.dp(16f)).roundToInt()

/**
 * The touch window covers where the island is and where it goes. A closed island gets a little extra room around it,
 * because it is small and sits at the very top where a thumb does not land precisely.
 */
private fun touchRect(a: androidx.compose.ui.geometry.Rect, b: androidx.compose.ui.geometry.Rect, layout: IslandLayout): Rect {
    val opened = b.height > layout.fit.dp(120f)
    val slack = if (opened) 0f else layout.fit.dp(8f)
    val left = (min(a.left, b.left) - slack).coerceAtLeast(0f)
    val right = (max(a.right, b.right) + slack).coerceAtMost(layout.fit.screenWidth.toFloat())
    // No slack above: the strip along the top edge belongs to the status bar and its shade.
    val top = min(a.top, b.top).coerceAtLeast(0f)
    val bottom = max(a.bottom, b.bottom) + slack
    return Rect(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt().coerceAtLeast(top.roundToInt() + 1))
}

@Composable
private fun contentAccent(content: IslandContent?, seed: Color, s: IslandSettings): Color {
    val art = (content as? IslandContent.Music)?.playing?.art
    val fromArt = rememberAccentOf(if (s.accentSource == nl.markmaaktmedia.drip.data.AccentSource.ALBUM_ART) art else null, seed)
    return when (content) {
        is IslandContent.Music -> fromArt
        is IslandContent.Live -> content.live.accent?.let { Color(it) } ?: seed
        is IslandContent.Notice -> when (content.alert.kind) {
            nl.markmaaktmedia.drip.island.AlertKind.CHARGING, nl.markmaaktmedia.drip.island.AlertKind.BATTERY_FULL -> IslandInk.Green
            else -> content.alert.accent?.let { Color(it) } ?: seed
        }
        is IslandContent.Call -> IslandInk.Green
        is IslandContent.Charging -> IslandInk.Green
        is IslandContent.Torch -> IslandInk.Yellow
        is IslandContent.Privacy -> privacyInk(content.privacy)
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

/** The island at one moment as a clip. A new one is made every frame of a movement, in the layer block. */
private class GeometryShape(private val g: IslandGeometry, private val origin: Offset) : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(g.buildPath(Path(), origin.x, origin.y))
}

/**
 * Lays the content out at the size of its own stage and places it where the moving shape is, so the content is measured
 * once per stage and only moves (in the placement phase) while the shape slides. An opened card is measured at the full
 * width and as tall as it wants; [onHeight] reports that height so the shape can aim for it.
 */
private fun Modifier.placedIn(
    shape: AnimatedGeometry,
    press: Animatable<Float, AnimationVector1D>,
    window: IslandWindow,
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
            val o = window.originNow()
            val g = shape.value
            // Opened content keeps to the top of the island and grows with it; closed content stays centred in it.
            val x = g.centerX - o.x - w / 2f
            // Content leaving or arriving keeps its own place under the top of the island instead of sliding to the
            // middle of a shape that is growing into something else.
            val y = if (wrapHeight) g.top - o.y else g.top - o.y + (target.height - placeable.height) / 2f
            val grow = if (wrapHeight) (g.width / target.width).coerceIn(0.7f, 1f) else 1f
            // Held down, the closed island swells a little; what is in it swells with it, from the top, by the same share.
            val swell = if (wrapHeight) 1f else 1f + 2f * 3f * density * press.value / g.width.coerceAtLeast(1f)
            placeable.placeWithLayer(x.roundToInt(), y.roundToInt()) {
                scaleX = grow * swell
                scaleY = grow * swell
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
        }
    }

/** Content that comes or goes is a little out of focus while it does, like the island of the iPhone. */
@Composable
private fun Modifier.blurWhileMoving(scope: AnimatedVisibilityScope): Modifier {
    val blur by scope.transition.animateFloat(transitionSpec = { tween(200) }, label = "blur") { state ->
        if (state == EnterExitState.Visible) 0f else 6f
    }
    return graphicsLayer {
        val r = blur * density
        renderEffect = if (r > 0.5f) BlurEffect(r, r) else null
    }
}
