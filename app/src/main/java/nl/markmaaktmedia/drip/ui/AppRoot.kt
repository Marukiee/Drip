package nl.markmaaktmedia.drip.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.graph
import nl.markmaaktmedia.drip.ui.components.PillLoader
import nl.markmaaktmedia.drip.ui.screens.AppPickerScreen
import nl.markmaaktmedia.drip.ui.screens.AppearanceScreen
import nl.markmaaktmedia.drip.ui.screens.ChangelogScreen
import nl.markmaaktmedia.drip.ui.screens.HomeScreen
import nl.markmaaktmedia.drip.ui.screens.OnboardingScreen
import nl.markmaaktmedia.drip.ui.screens.settings.SettingsPageScreen
import nl.markmaaktmedia.drip.ui.theme.DripMotion
import nl.markmaaktmedia.drip.ui.update.UpdateBanner

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val graph = context.graph
    val onboarded by graph.prefs.onboarded.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    // A cold start: the answer to "has the person set up the app" comes a moment after the screen is up, which is
    // after the effect below has already seen the app come to the front, so that one skips this start.
    LaunchedEffect(onboarded) {
        if (onboarded == true) graph.updater.checkIfDue()
    }
    // Every time the app comes to the front, so a release from an hour ago is not missed
    // just because the process has been alive for days.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_START) {
        if (onboarded == true) scope.launch { graph.updater.checkIfDue() }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (onboarded) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { PillLoader(label = stringResource(R.string.app_name)) }
            false -> OnboardingScreen(onFinished = { scope.launch { graph.prefs.setOnboarded(true) } })
            true -> MainNavigation()
        }
    }
}

@Composable
private fun MainNavigation() {
    val nav = androidx.compose.runtime.saveable.rememberSaveable(saver = NavSaver) { Nav() }

    // One transition drives every page change, including the back gesture. While the finger
    // drags, the transition is *seeked*: the page beneath is composed once, at the start, and
    // the very same transition (the fade, the shrink, and the shared bounds that pull the page
    // back into the row it came from) plays out under the finger and carries on when it lets
    // go. Nothing is composed twice and nothing is swapped at the end.
    val seekable = remember { androidx.compose.animation.core.SeekableTransitionState(nav.top) }
    LaunchedEffect(nav.top) {
        if (seekable.targetState != nav.top) seekable.animateTo(nav.top)
    }
    val transition = androidx.compose.animation.core.rememberTransition(seekable, label = "route")

    PredictiveBackHandler(enabled = nav.stack.size > 1) { events ->
        val target = nav.stack[nav.stack.size - 2]
        try {
            events.collect { event -> seekable.seekTo(event.progress, target) }
            seekable.animateTo(target)
            // Last, so the handler switching itself off cannot cut the animation short.
            nav.pop()
        } catch (e: CancellationException) {
            withContext(NonCancellable) { seekable.snapTo(nav.top) }
            throw e
        }
    }

    // The update banner is drawn over the pages, so the pages move down by its height while it is there and nothing
    // sits under it. It already includes the status bar, which the pages add again themselves, so that part is taken off.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val statusBar = WindowInsets.statusBars.getTop(density)
    var bannerPx by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val below by androidx.compose.animation.core.animateDpAsState(
        with(density) { (bannerPx - statusBar).coerceAtLeast(0).toDp() },
        DripMotion.spatial(), label = "bannerSpace",
    )
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        transition.AnimatedContent(
                transitionSpec = {
                    // The Material shared axis that system apps use: the new page comes in from a
                    // third of the screen away and fades up, the old one slides a third the other
                    // way and shrinks a little behind it. Going back mirrors it, and the page that
                    // leaves goes all the way out. The same spec is what the back gesture seeks.
                    val forward = isForward(nav, initialState, targetState)
                    val ease = tween<Float>(SharedAxisMillis, easing = Emphasized)
                    val slide = tween<IntOffset>(SharedAxisMillis, easing = Emphasized)
                    if (forward) {
                        (slideInHorizontally(slide) { it / 3 } + fadeIn(ease)) togetherWith
                            (slideOutHorizontally(slide) { -it / 3 } + scaleOut(ease, targetScale = 0.92f))
                    } else {
                        ((slideInHorizontally(slide) { -it / 3 } + fadeIn(ease)) togetherWith
                            (slideOutHorizontally(tween(SharedAxisMillis, easing = Easing { f -> f * f * f })) { it } +
                                scaleOut(ease, targetScale = 0.85f)))
                            // The page going out stays on top of the one coming back.
                            .apply { targetContentZIndex = -1f }
                    }
                },
                // The spring that moves it overshoots, so it can dip below nothing for a moment, and a padding may not be negative.
                modifier = Modifier.fillMaxSize().padding(top = below.coerceAtLeast(0.dp)),
            ) { route ->
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { RouteContent(route, nav) }
            }
        // Over every screen: an update is worth seeing wherever you are in the app.
        UpdateBanner(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)
                .onSizeChanged { bannerPx = it.height + with(density) { 8.dp.roundToPx() } + statusBar },
        )
    }
}

private const val SharedAxisMillis = 350
private val Emphasized = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * Whether going from one page to the other is opening a page rather than going back. Judged by the place in the back
 * stack: a page that is no longer in it was just popped, so that is always the way back.
 */
private fun isForward(nav: Nav, from: Route, to: Route): Boolean {
    val fromAt = nav.stack.lastIndexOf(from)
    return fromAt >= 0 && nav.stack.lastIndexOf(to) > fromAt
}

@Composable
private fun RouteContent(route: Route, nav: Nav) {
    // Every page except home is the far end of a shared transition from its row.
    Box(if (route == Route.Home) Modifier else Modifier.routeBounds(routeKey(route))) { RouteBody(route, nav) }
}

@Composable
private fun RouteBody(route: Route, nav: Nav) {
    when (route) {
        Route.Home -> HomeScreen(onOpen = { nav.push(it) }, listState = nav.homeList)
        Route.Appearance -> AppearanceScreen(onBack = { nav.pop() })
        Route.Changelog -> ChangelogScreen(onBack = { nav.pop() })
        Route.OnboardingPreview -> OnboardingScreen(onFinished = { nav.pop() })
        is Route.AppPicker -> AppPickerScreen(route.kind, onBack = { nav.pop() })
        is Route.SettingsPage -> SettingsPageScreen(route.page, onBack = { nav.pop() }, onOpen = { nav.push(it) })
    }
}
