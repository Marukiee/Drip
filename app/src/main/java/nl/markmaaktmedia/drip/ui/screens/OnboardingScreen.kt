package nl.markmaaktmedia.drip.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.engine.Access
import nl.markmaaktmedia.drip.ui.components.bouncyClickable
import nl.markmaaktmedia.drip.ui.theme.DripIcons
import nl.markmaaktmedia.drip.ui.theme.DripMotion
import nl.markmaaktmedia.drip.ui.theme.GroupedSpacing
import nl.markmaaktmedia.drip.ui.theme.PillShape
import nl.markmaaktmedia.drip.ui.theme.SquircleShape
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private const val PageCount = 2
private const val LastPage = PageCount - 1

/**
 * First run: two small steps, each one thing to allow. Both can be skipped; the home screen asks again for whatever is
 * still missing, and nothing breaks without it.
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { PageCount }
    val page = pager.currentPage

    fun goTo(target: Int) {
        scope.launch { pager.animateScrollToPage(target.coerceIn(0, LastPage), animationSpec = DripMotion.spatial()) }
    }

    // Back walks the pages first, so leaving the app is what a back press on page one does.
    BackHandler(enabled = page > 0) { goTo(page - 1) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        HorizontalPager(pager, Modifier.weight(1f)) { index ->
            Box(Modifier.fillMaxSize().pageEffect(pager, index)) {
                when (index) {
                    0 -> IslandStep()
                    else -> NotificationsStep()
                }
            }
        }

        PageDots(pager, Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp, bottom = 20.dp))

        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(
                visible = page > 0,
                enter = expandHorizontally(DripMotion.spatial()) + fadeIn(DripMotion.fadeSpec()),
                exit = shrinkHorizontally(DripMotion.spatial()) + fadeOut(DripMotion.fadeSpec()),
            ) {
                Row {
                    Box(
                        Modifier
                            .size(ButtonHeight)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .bouncyClickable { goTo(page - 1) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(DripIcons.Back, stringResource(R.string.action_back), tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                }
            }
            AnimatedVisibility(
                visible = page == 0,
                enter = expandHorizontally(DripMotion.spatial()) + fadeIn(DripMotion.fadeSpec()),
                exit = shrinkHorizontally(DripMotion.spatial()) + fadeOut(DripMotion.fadeSpec()),
            ) {
                Row {
                    OnboardingButton(onClick = onFinished, primary = false) {
                        Text(stringResource(R.string.onb_later), style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
                    }
                    Spacer(Modifier.width(12.dp))
                }
            }
            OnboardingButton(
                onClick = { if (page < LastPage) goTo(page + 1) else onFinished() },
                primary = true,
                modifier = Modifier.weight(1f),
            ) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        (fadeIn(DripMotion.fadeSpec()) + slideInVertically(DripMotion.spatial()) { it / 2 }) togetherWith
                            (fadeOut(DripMotion.fadeSpec()) + slideOutVertically(DripMotion.spatial()) { -it / 2 })
                    },
                    contentAlignment = Alignment.Center,
                    label = "primaryLabel",
                ) { shown ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            stringResource(if (shown == LastPage) R.string.onb_done else R.string.onb_continue),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            softWrap = false,
                        )
                        Icon(if (shown == LastPage) DripIcons.Check else DripIcons.ChevronRight, null, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

private val ButtonHeight = 56.dp

/** One size and one shape for every button on the bottom row, so the row reads as a single system. */
@Composable
private fun OnboardingButton(
    onClick: () -> Unit,
    primary: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    CompositionLocalProvider(
        LocalContentColor provides if (primary) scheme.onPrimary else scheme.onSurface,
    ) {
        Row(
            modifier
                .height(ButtonHeight)
                .clip(PillShape)
                .background(if (primary) scheme.primary else scheme.surfaceContainerHigh)
                .bouncyClickable(onClick = onClick)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

/**
 * Pages recede as they leave: a little smaller and fainter the further they are from
 * the middle. Driven by the scroll position itself, so it follows a finger exactly and
 * the same on a button press.
 */
private fun Modifier.pageEffect(pager: PagerState, page: Int): Modifier = graphicsLayer {
    val distance = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
    alpha = 1f - distance * 0.75f
    val s = 1f - distance * 0.06f
    scaleX = s
    scaleY = s
}

/**
 * Round dots. One primary dot rides over the grey ones with the scroll, so it is a
 * single thing travelling rather than the dots each changing colour.
 */
@Composable
private fun PageDots(pager: PagerState, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val slot = 20.dp
    val idle = 8.dp
    val active = 10.dp
    val description = stringResource(R.string.onboarding_page, pager.currentPage + 1, PageCount)
    Box(modifier) {
        Row(Modifier.semantics { contentDescription = description }) {
            repeat(PageCount) {
                Box(Modifier.size(slot), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(idle).clip(CircleShape).background(scheme.outlineVariant))
                }
            }
        }
        Box(
            Modifier
                .offset { IntOffset(((pager.currentPage + pager.currentPageOffsetFraction) * slot.toPx()).roundToInt(), 0) }
                .size(slot),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(active).clip(CircleShape).background(scheme.primary))
        }
    }
}

// ---- The two steps --------------------------------------------------------------------

/** A step page: the words at the top, the card to allow underneath, scrolling when the screen is short. */
@Composable
private fun Step(number: Int, title: String, body: String, top: @Composable () -> Unit = {}, cards: @Composable () -> Unit) {
    val background = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            top()
            Text(
                stringResource(R.string.onb_step, number, PageCount),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp).staggeredEntry(1),
            )
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 4.dp).staggeredEntry(2))
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp).staggeredEntry(3),
            )
            Spacer(Modifier.height(20.dp))
            Column(Modifier.staggeredEntry(4), verticalArrangement = Arrangement.spacedBy(GroupedSpacing)) { cards() }
            Spacer(Modifier.height(28.dp))
        }
        // The page runs on under the dots, so it fades out instead of being cut off.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(28.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, background))),
        )
    }
}

@Composable
private fun IslandStep() {
    val context = LocalContext.current
    val access = rememberAccess()
    // The hint about restricted settings only once a try came back without the permission.
    var asked by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    Step(
        1, stringResource(R.string.onb_island_title), stringResource(R.string.onb_island_body),
        top = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                DripMark(size = 112.dp)
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(R.string.onb_title),
                    style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center, modifier = Modifier.staggeredEntry(0, startDelayMillis = 120),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.onb_body),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.staggeredEntry(1, startDelayMillis = 120),
                )
                Spacer(Modifier.height(32.dp))
            }
        },
    ) {
        PermissionCard(
            DripIcons.Touch, stringResource(R.string.perm_island), stringResource(R.string.perm_island_why),
            granted = access.island, onGrant = { asked = true; Access.openAccessibility(context) },
            note = if (access.island || !asked) null else restrictedNote(),
        )
    }
}

@Composable
private fun NotificationsStep() {
    val context = LocalContext.current
    val access = rememberAccess()
    val askPost = rememberPostRequest()
    var asked by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    Step(2, stringResource(R.string.onb_notif_title), stringResource(R.string.onb_notif_body)) {
        PermissionCard(
            DripIcons.Notifications, stringResource(R.string.perm_listener), stringResource(R.string.perm_listener_why),
            granted = access.listener, onGrant = { asked = true; Access.openListener(context) },
            note = if (access.listener || !asked) null else restrictedNote(),
            index = 0, total = 2,
        )
        PermissionCard(
            DripIcons.Update, stringResource(R.string.perm_post), stringResource(R.string.perm_post_why),
            granted = access.post, onGrant = askPost,
            index = 1, total = 2,
        )
    }
}

/**
 * The launcher icon itself: the same two layers the home screen draws, so the first page shows exactly what is on the
 * phone. The layers are 108 wide with 72 of them visible, so they are drawn at one and a half times the tile and
 * cropped by it. The drop settles in on a spring.
 */
@Composable
private fun DripMark(size: Dp) {
    val enter = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { fade.animateTo(1f, DripMotion.fadeSpec()) }
        enter.animateTo(1f, DripMotion.bouncy())
    }
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                val s = 0.7f + 0.3f * enter.value
                scaleX = s
                scaleY = s
                alpha = fade.value
            }
            .clip(SquircleShape(size * 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.requiredSize(size * 1.5f))
        Image(
            painterResource(R.drawable.ic_launcher_foreground), null,
            Modifier.requiredSize(size * 1.5f).graphicsLayer {
                translationY = -(1f - enter.value) * 24.dp.toPx()
            },
        )
    }
}
