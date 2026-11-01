package nl.markmaaktmedia.drip.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Size
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.engine.Access
import nl.markmaaktmedia.drip.graph
import nl.markmaaktmedia.drip.island.Demo
import nl.markmaaktmedia.drip.ui.Route
import nl.markmaaktmedia.drip.ui.components.GroupedRow
import nl.markmaaktmedia.drip.ui.components.RowIcon
import nl.markmaaktmedia.drip.ui.components.SectionHeader
import nl.markmaaktmedia.drip.ui.routeBounds
import nl.markmaaktmedia.drip.ui.routeKey
import nl.markmaaktmedia.drip.ui.screens.settings.SettingsCatalog
import nl.markmaaktmedia.drip.ui.screens.settings.SettingsCategory
import nl.markmaaktmedia.drip.ui.screens.settings.SettingsEntry
import nl.markmaaktmedia.drip.ui.screens.settings.SettingsResults
import nl.markmaaktmedia.drip.ui.screens.settings.SettingsSearch
import nl.markmaaktmedia.drip.ui.screens.settings.SettingsSearchField
import nl.markmaaktmedia.drip.ui.screens.settings.SettingsViewModel
import nl.markmaaktmedia.drip.ui.screens.settings.rememberCategorySummaries
import nl.markmaaktmedia.drip.ui.screens.settings.rememberIsland
import nl.markmaaktmedia.drip.ui.theme.DripIcons
import nl.markmaaktmedia.drip.ui.theme.DripMotion
import nl.markmaaktmedia.drip.ui.theme.GroupedSpacing

/**
 * The one screen of Drip: the island's switch, what it still needs, a few buttons that make the real island act
 * something out, and the settings, each category a page of its own. Typing in the search field swaps everything under
 * it for the results, and a result opens the page that holds the setting and lights the row up.
 */
@Composable
fun HomeScreen(onOpen: (Route) -> Unit, modifier: Modifier = Modifier, listState: LazyListState = rememberLazyListState()) {
    val state = viewModel<SettingsViewModel>()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val language = LocalConfiguration.current.locales[0]
    // Built again when the language changes, because the words searched are the words shown.
    val index = remember(language) { SettingsCatalog.index(context) }
    val hits = remember(index, state.query) { SettingsSearch.search(index.entries, state.query) }
    val summaries = rememberCategorySummaries()

    var focused by remember { mutableStateOf(false) }
    val searching = state.query.isNotBlank()
    val active = focused || state.query.isNotEmpty()
    val fieldFocus = remember { FocusRequester() }

    fun endSearch() {
        state.query = ""
        keyboard?.hide()
        focusManager.clearFocus()
    }

    // Back clears the search before it leaves the app.
    BackHandler(enabled = active) { endSearch() }

    // The keyboard going away by itself (the Back gesture closes it first) also ends the focus on the field.
    val imeVisible = WindowInsets.isImeVisible
    var imeWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            imeWasVisible = true
        } else if (imeWasVisible) {
            imeWasVisible = false
            focusManager.clearFocus()
        }
    }

    // A request left over from an earlier search has nothing to light up on this page.
    LaunchedEffect(Unit) { state.focus.clear() }

    fun open(entry: SettingsEntry) {
        keyboard?.hide()
        focusManager.clearFocus()
        state.focus.request(entry.focus)
        // The page that normally leads to the screen goes underneath, so Back climbs the same way the person would.
        entry.via?.let(onOpen)
        onOpen(entry.route)
    }

    // The title and the search field scroll away with the list like the top of one page, and come back at the top.
    // While searching the field stays put, since it is what is being typed in.
    val header = remember { mutableIntStateOf(0) }
    val pinned = searching || active
    val scrolledAway by remember {
        derivedStateOf { if (listState.firstVisibleItemIndex > 0) Int.MAX_VALUE else listState.firstVisibleItemScrollOffset }
    }
    val density = LocalDensity.current
    // Clipped at the status bar, so the title does not slide on underneath the clock.
    Box(modifier.fillMaxSize().statusBarsPadding().clipToBounds()) {
        Box(Modifier.fillMaxSize().imePadding()) {
            AnimatedContent(
                targetState = searching,
                transitionSpec = { fadeIn(DripMotion.fadeSpec()) togetherWith fadeOut(DripMotion.fadeSpec()) },
                label = "homeContent",
            ) { showResults ->
                if (showResults) {
                    Box(Modifier.padding(top = with(density) { header.intValue.toDp() })) {
                        SettingsResults(
                            hits = hits,
                            index = index,
                            summaries = summaries,
                            query = state.query,
                            bottomPadding = if (imeVisible) 16.dp else 40.dp,
                            onDragStart = { keyboard?.hide() },
                            onPick = ::open,
                        )
                    }
                } else {
                    Overview(summaries, listState, onOpen, top = with(density) { header.intValue.toDp() })
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { header.intValue = it.height }
                .offset { IntOffset(0, if (pinned) 0 else -minOf(scrolledAway, header.intValue)) }
                .background(MaterialTheme.colorScheme.background),
        ) {
            Text(
                stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp),
            )
            SettingsSearchField(
                query = state.query,
                onQueryChange = { state.query = it },
                active = active,
                onFocusChange = { focused = it },
                onBack = ::endSearch,
                onSearch = { keyboard?.hide(); focusManager.clearFocus() },
                focusRequester = fieldFocus,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            )
        }
    }
}

/** The groups of categories, in the order of the list in [SettingsCategory]. */
private val Groups = listOf(
    listOf(SettingsCategory.Shape, SettingsCategory.Motion, SettingsCategory.Look),
    listOf(SettingsCategory.Shows, SettingsCategory.Notifications, SettingsCategory.Gestures, SettingsCategory.Hide),
    listOf(SettingsCategory.Keep),
    listOf(SettingsCategory.Updates, SettingsCategory.About),
)

@Composable
private fun Overview(summaries: Map<SettingsCategory, String>, listState: LazyListState, onOpen: (Route) -> Unit, top: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val access = rememberAccess()
    val restricted = restrictedNote()
    // The hint about restricted settings only after a first try came back without the permission, not as a warning up front.
    var askedIsland by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var askedListener by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val (island, _) = rememberIsland()
    val missing = listOfNotNull(
        Missing.Island.takeIf { !access.island },
        Missing.Listener.takeIf { !access.listener },
    )

    // The list runs on under the gesture bar and stops above it at the end.
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 4.dp, bottom = bottom),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "master") { MasterSwitch(allowed = access.island, modifier = Modifier.animateItem()) }

        // Only what is missing, and gone the moment it is allowed: nothing here stays as a warning.
        if (missing.isNotEmpty()) {
            item(key = "todo") {
                Column(Modifier.animateItem()) {
                    SectionHeader(stringResource(R.string.home_todo), top = 4.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(GroupedSpacing)) {
                        missing.forEachIndexed { position, item ->
                            when (item) {
                                Missing.Island -> PermissionCard(
                                    DripIcons.Touch, stringResource(R.string.perm_island), stringResource(R.string.perm_island_why),
                                    granted = false, onGrant = { askedIsland = true; Access.openAccessibility(context) },
                                    note = restricted.takeIf { askedIsland }, index = position, total = missing.size,
                                )
                                Missing.Listener -> PermissionCard(
                                    DripIcons.Notifications, stringResource(R.string.perm_listener), stringResource(R.string.perm_listener_why),
                                    granted = false, onGrant = { askedListener = true; Access.openListener(context) },
                                    note = restricted.takeIf { askedListener }, index = position, total = missing.size,
                                )
                            }
                        }
                    }
                }
            }
        }

        item(key = "try") {
            Column(Modifier.animateItem()) {
                SectionHeader(stringResource(R.string.home_try), top = 4.dp)
                TryButtons(blocked = if (!access.island) R.string.home_try_blocked else if (!island.enabled) R.string.home_try_off else null)
            }
        }

        item(key = "settings") { SectionHeader(stringResource(R.string.home_settings), top = 4.dp, bottom = 0.dp, modifier = Modifier.animateItem()) }
        items(Groups.size, key = { "group$it" }) { group ->
            Column(Modifier.fillMaxWidth().animateItem(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Groups[group].forEachIndexed { position, category ->
                    CategoryRow(
                        position, Groups[group].size, category, summaries[category].orEmpty(),
                        onClick = { onOpen(category.route) },
                        modifier = Modifier.routeBounds(routeKey(category.route)),
                    )
                }
            }
        }
    }
}

// Update notices are not here: they are optional, and a card for something declined on purpose would never go away.
private enum class Missing { Island, Listener }

/** One category: its icon, its name, and a line that says how it is set right now. */
@Composable
internal fun CategoryRow(index: Int, total: Int, category: SettingsCategory, summary: String, onClick: () -> Unit, modifier: Modifier) {
    GroupedRow(index, total, modifier = modifier, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RowIcon(category.icon())
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(category.title), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(DripIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * The island's own switch, at the top where it is found first. The badge is one of Material's shapes that morphs
 * from a plain circle into a scalloped cookie when the island comes on and turns a little as it does, so the state
 * reads at a glance and the change is something seen happen.
 */
@Composable
private fun MasterSwitch(allowed: Boolean, modifier: Modifier = Modifier) {
    val (island, edit) = rememberIsland()
    val on = island.enabled
    val scheme = MaterialTheme.colorScheme
    val progress by animateFloatAsState(if (on) 1f else 0f, DripMotion.springy(), label = "masterMorph")
    val container by animateColorAsState(if (on) scheme.primaryContainer else scheme.surfaceContainer, DripMotion.colourSpec(), label = "masterContainer")
    val badge by animateColorAsState(if (on) scheme.primary else scheme.surfaceContainerHighest, DripMotion.colourSpec(), label = "masterBadge")
    val ink by animateColorAsState(if (on) scheme.onPrimary else scheme.onSurfaceVariant, DripMotion.colourSpec(), label = "masterInk")
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }

    GroupedRow(0, 1, modifier = modifier, color = container, onClick = { edit { it.copy(enabled = !it.enabled) } }) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                Modifier.size(72.dp)
                    .graphicsLayer { rotationZ = progress * 40f }
                    .background(badge, MorphShape(morph, progress)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_stat_drip), null, tint = ink,
                    modifier = Modifier.size(32.dp).graphicsLayer { rotationZ = -progress * 40f },
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(if (on) R.string.home_island_on else R.string.home_island_off),
                    style = MaterialTheme.typography.titleLarge, color = if (on) scheme.onPrimaryContainer else scheme.onSurface,
                )
                Text(
                    stringResource(
                        when {
                            on && !allowed -> R.string.home_island_needs
                            on -> R.string.home_island_on_sub
                            else -> R.string.home_island_off_sub
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (on) scheme.onPrimaryContainer.copy(alpha = 0.8f) else scheme.onSurfaceVariant,
                )
            }
            Switch(checked = on, onCheckedChange = null)
        }
    }
}

/** A [Morph] at one point of its way, scaled from the unit square of Material's shapes to the size it is drawn at. */
private class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress).asComposePath()
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }
}

private class TryItem(val demo: Demo, val label: Int, val icon: @Composable () -> Painter)

private val TryRows = listOf(
    listOf(
        TryItem(Demo.MUSIC, R.string.try_music) { DripIcons.Music },
        TryItem(Demo.CALL, R.string.try_call) { DripIcons.Call },
        TryItem(Demo.CHARGING, R.string.try_charging) { DripIcons.BatteryCharging },
    ),
    listOf(
        TryItem(Demo.ALERT, R.string.try_notification) { DripIcons.Notifications },
        TryItem(Demo.TIMER, R.string.try_timer) { DripIcons.Timer },
        TryItem(Demo.EXPAND, R.string.try_expand) { DripIcons.OpenInFull },
    ),
)

/**
 * Buttons that make the real island act something out. Without the island allowed, or with it switched off, nothing
 * would answer them, so they are grey then and a tap says why.
 */
@Suppress("DEPRECATION")
@Composable
private fun TryButtons(blocked: Int?) {
    val context = LocalContext.current
    val hub = context.graph.hub
    val enabled = blocked == null
    val reason = blocked?.let { stringResource(it) }
    Column(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TryRows.forEach { row ->
            ButtonGroup(Modifier.fillMaxWidth()) {
                row.forEach { item ->
                    val interaction = remember { MutableInteractionSource() }
                    FilledTonalButton(
                        onClick = {
                            if (reason == null) hub.demo(item.demo) else Toast.makeText(context, reason, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).animateWidth(interaction),
                        interactionSource = interaction,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                    ) {
                        // The icon over the word, so even the longest word has the whole width of its third.
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(item.icon(), null, modifier = Modifier.size(20.dp))
                            Text(stringResource(item.label), maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

