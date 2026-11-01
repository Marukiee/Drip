package nl.markmaaktmedia.drip.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.engine.Access
import nl.markmaaktmedia.drip.engine.IslandKeeper
import nl.markmaaktmedia.drip.graph
import nl.markmaaktmedia.drip.ui.components.RowIcon
import nl.markmaaktmedia.drip.ui.components.bouncyClickable
import nl.markmaaktmedia.drip.ui.theme.DripIcons
import nl.markmaaktmedia.drip.ui.theme.DripMotion
import nl.markmaaktmedia.drip.ui.theme.LocalDripExtraColors
import nl.markmaaktmedia.drip.ui.theme.PillShape
import nl.markmaaktmedia.drip.ui.theme.SquircleShape
import nl.markmaaktmedia.drip.ui.theme.groupedShape

/** Nothing yet, enough to work but not everything, or all of it. */
enum class PermissionLevel { Off, Partly, On }

/** A follow-up under a card: what is wrong and what the person can do about it. */
class PermissionNote(val text: String, val actions: List<PermissionNoteAction>)

class PermissionNoteAction(val label: String, val onClick: () -> Unit, val primary: Boolean = false)

/** What Drip is allowed right now. */
data class AccessState(
    /** The island service is switched on in Accessibility. */
    val island: Boolean,
    /** Notification access, for music, calls, timers and notifications. */
    val listener: Boolean,
    /** Posting a notification, only used for "an update is ready". */
    val post: Boolean,
    /** WRITE_SECURE_SETTINGS, so the island can be switched back on by Drip itself. */
    val keeper: Boolean,
)

/**
 * Live answers to "is Drip allowed this yet".
 *
 * Permissions change outside the app, in a system screen, so the answers are read again every time the app comes back
 * to the front. The hub says so as well the moment a service starts, which is when the answer flips while the app is
 * still on screen.
 */
@Composable
fun rememberAccess(): AccessState {
    val context = LocalContext.current
    val hub = context.graph.hub
    val running by hub.islandRunning.collectAsState()
    val connected by hub.listenerConnected.collectAsState()
    var version by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { version++ }
    val state = remember(version, running, connected) {
        AccessState(
            island = running || Access.islandAllowed(context),
            listener = connected || Access.listenerAllowed(context),
            post = Access.postAllowed(context),
            keeper = IslandKeeper.canKeep(context),
        )
    }
    // While something is missing it is looked at again now and then: a split screen, or a grant through Shizuku or adb,
    // changes it without the app ever leaving the front.
    LaunchedEffect(state) {
        if (!state.island || !state.listener) {
            delay(2_000)
            version++
        }
    }
    return state
}

/**
 * Asks for POST_NOTIFICATIONS. After one no, Android may not show its question again, so a second tap goes to the
 * app's notification settings instead, where it can always be switched on.
 */
@Composable
fun rememberPostRequest(): () -> Unit {
    val context = LocalContext.current
    var asked by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { asked = true }
    return {
        if (asked && !Access.postAllowed(context)) Access.openNotificationSettings(context)
        else ask.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

/** The line under the island card for a phone that greys the switch out for a sideloaded app. */
@Composable
fun restrictedNote(): PermissionNote {
    val context = LocalContext.current
    return PermissionNote(
        stringResource(R.string.perm_restricted),
        listOf(PermissionNoteAction(stringResource(R.string.perm_app_info), { Access.openAppInfo(context) })),
    )
}

/**
 * One thing Drip may ask for: what it is, why, and whether it has it. Used in the
 * first-run flow and on the home screen, so the wording is the same in both.
 *
 * The answer sits in a slot of fixed size at the end of the title row. Allow, Partly
 * and the checkmark are the same pill changing colour and content, never three
 * different widgets, so nothing beside it moves when a permission comes through.
 */
@Composable
fun PermissionCard(
    icon: Painter,
    title: String,
    why: String,
    granted: Boolean,
    onGrant: () -> Unit,
    modifier: Modifier = Modifier,
    testLabel: String? = null,
    onTest: (() -> Unit)? = null,
    testResult: String? = null,
    /** With [granted]: the essentials work but an extra is still off. */
    partly: Boolean = false,
    note: PermissionNote? = null,
    /** Position in a slab of cards, for the corner treatment. */
    index: Int = 0,
    total: Int = 1,
) {
    val level = when {
        !granted -> PermissionLevel.Off
        partly -> PermissionLevel.Partly
        else -> PermissionLevel.On
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(groupedShape(index, total))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
    ) {
        // The pill is a column of its own, so the small print wraps beside it instead of
        // running on underneath it.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RowIcon(icon)
            // Title and small print are one block, level with the top of the icon. Centring the
            // title on the icon pushed the two lines apart for no reason.
            Column(Modifier.weight(1f).padding(top = 2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    why,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) { PermissionPill(level, onGrant) }
        }
        AnimatedVisibility(
            visible = note != null,
            enter = expandVertically(DripMotion.sizeSpring()) + fadeIn(DripMotion.fadeSpec()),
            exit = shrinkVertically(DripMotion.sizeSpring()) + fadeOut(DripMotion.fadeSpec()),
        ) {
            // Kept while it animates out, so the box does not empty itself before it closes.
            val shown = remember { mutableStateOf(note) }
            if (note != null) shown.value = note
            shown.value?.let { NoteBox(it, Modifier.padding(top = 12.dp)) }
        }
        AnimatedVisibility(
            visible = granted && onTest != null && testLabel != null,
            enter = expandVertically(DripMotion.sizeSpring()) + fadeIn(DripMotion.fadeSpec()),
            exit = shrinkVertically(DripMotion.sizeSpring()) + fadeOut(DripMotion.fadeSpec()),
        ) {
            Column(Modifier.padding(start = IconColumn, top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (testLabel != null && onTest != null) SmallPill(testLabel, onTest, primary = false, icon = DripIcons.Send)
                AnimatedVisibility(
                    visible = testResult != null,
                    enter = expandVertically(DripMotion.sizeSpring()) + fadeIn(DripMotion.fadeSpec()),
                    exit = shrinkVertically(DripMotion.sizeSpring()) + fadeOut(DripMotion.fadeSpec()),
                ) {
                    val last = remember { mutableStateOf(testResult) }
                    if (testResult != null) last.value = testResult
                    Text(
                        last.value.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/**
 * Lifts an item into place a moment after the one before it.
 *
 * Position rides a spring and opacity a tween, so the item arrives with a little life
 * and never flashes. The stagger is what makes a list read as one thing being laid
 * down instead of everything appearing at once.
 */
fun Modifier.staggeredEntry(index: Int, startDelayMillis: Int = 0, distance: Dp = 28.dp): Modifier = composed {
    val lift = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(startDelayMillis + index * 55L)
        launch { alpha.animateTo(1f, DripMotion.fadeSpec()) }
        lift.animateTo(1f, DripMotion.spatial())
    }
    graphicsLayer {
        translationY = (1f - lift.value) * distance.toPx()
        this.alpha = alpha.value
    }
}

/** Icon width plus the gap after it, so the small print lines up with the title. */
private val IconColumn = 54.dp

private val PillWidth = 92.dp
private val PillHeight = 40.dp

/**
 * The Allow pill, and what it turns into.
 *
 * Colour is a tween and the checkmark arrives on a spring, the same split as the rest of
 * the app. The pill keeps its size in every state on purpose: the title next to it is
 * laid out once and never has to make room.
 */
@Composable
private fun PermissionPill(level: PermissionLevel, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val online = LocalDripExtraColors.current.online
    val container by animateColorAsState(
        when (level) {
            PermissionLevel.Off -> scheme.primary
            PermissionLevel.Partly -> scheme.tertiaryContainer
            PermissionLevel.On -> online
        },
        DripMotion.colourSpec(), label = "pillContainer",
    )
    val content by animateColorAsState(
        when (level) {
            PermissionLevel.Off -> scheme.onPrimary
            PermissionLevel.Partly -> scheme.onTertiaryContainer
            PermissionLevel.On -> online.shade(0.2f)
        },
        DripMotion.colourSpec(), label = "pillContent",
    )

    // A small pop when a permission comes through while looking at it, not on every visit.
    val pop = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(level) }
    LaunchedEffect(level) {
        if (level == PermissionLevel.On && previous != PermissionLevel.On) {
            pop.animateTo(1.1f, spring(dampingRatio = 0.6f, stiffness = 900f))
            pop.animateTo(1f, DripMotion.springy())
        }
        previous = level
    }

    Box(
        Modifier
            .size(PillWidth, PillHeight)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .clip(PillShape)
            .background(container)
            .bouncyClickable(enabled = level != PermissionLevel.On, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = level,
            transitionSpec = {
                (fadeIn(DripMotion.fadeSpec()) + scaleIn(DripMotion.springy(), 0.5f)) togetherWith
                    (fadeOut(DripMotion.fadeSpec()) + scaleOut(DripMotion.springy(), 0.7f))
            },
            contentAlignment = Alignment.Center,
            label = "pillContent",
        ) { shown ->
            when (shown) {
                PermissionLevel.On -> Icon(DripIcons.Check, stringResource(R.string.access_allowed), tint = content, modifier = Modifier.size(22.dp))
                PermissionLevel.Partly -> PillLabel(stringResource(R.string.perm_partly), content)
                PermissionLevel.Off -> PillLabel(stringResource(R.string.action_allow), content)
            }
        }
    }
}

@Composable
private fun PillLabel(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
}

/** A darker take on a theme colour, for a mark that sits on top of it. */
private fun Color.shade(factor: Float) = Color(red * factor, green * factor, blue * factor, alpha)

@Composable
private fun NoteBox(note: PermissionNote, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(SquircleShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(DripIcons.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 1.dp).size(18.dp))
            Text(note.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            note.actions.forEach { SmallPill(it.label, it.onClick, it.primary) }
        }
    }
}

/** The compact button used inside a card, where the full-size pill would outweigh the card. */
@Composable
private fun SmallPill(label: String, onClick: () -> Unit, primary: Boolean, icon: Painter? = null) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .height(38.dp)
            .clip(PillShape)
            .background(if (primary) scheme.primary else scheme.surfaceContainerHighest)
            .bouncyClickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = if (primary) scheme.onPrimary else scheme.onSurface, modifier = Modifier.size(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (primary) scheme.onPrimary else scheme.onSurface,
            maxLines = 1,
            softWrap = false,
        )
    }
}
