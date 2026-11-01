package nl.markmaaktmedia.drip.ui.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.data.IslandSettings
import nl.markmaaktmedia.drip.graph
import nl.markmaaktmedia.drip.ui.components.ContentRow
import nl.markmaaktmedia.drip.ui.components.GroupedRow
import nl.markmaaktmedia.drip.ui.components.RowIcon
import nl.markmaaktmedia.drip.ui.components.SegmentedPillRow
import kotlin.math.roundToInt

/**
 * The island settings as state, and a way to change them.
 *
 * The first value is read before the first frame, so a page does not open on the defaults and then flip every switch
 * to where it really is. Changes go through [IslandEdit] with a function of the newest settings, never a copy held by
 * the screen, so two quick changes on one page cannot undo each other.
 */
@Composable
internal fun rememberIsland(): Pair<IslandSettings, IslandEdit> {
    val graph = LocalContext.current.graph
    val initial = remember { runBlocking { graph.prefs.island.first() } }
    val settings by graph.prefs.island.collectAsState(initial = initial)
    val edit = remember(graph) {
        IslandEdit { change ->
            // On the main thread of the app's own scope: launched in order, written in order, and not cancelled when the
            // page closes halfway through a write.
            graph.scope.launch(Dispatchers.Main) { graph.prefs.editIsland(change) }
        }
    }
    return settings to edit
}

internal fun interface IslandEdit {
    operator fun invoke(change: (IslandSettings) -> IslandSettings)
}

/** A title over a row of choices with one travelling pill. */
@Composable
internal fun <T> SegmentedRow(
    index: Int,
    total: Int,
    icon: Painter,
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    val labels = options.associateWith { label(it) }
    ContentRow(index, total, icon, title) {
        SegmentedPillRow(
            options = options,
            selected = selected,
            label = { labels.getValue(it) },
            onSelect = onSelect,
            modifier = Modifier.fillMaxWidth(),
            equalWidth = true,
        )
    }
}

/**
 * A row with a title, the value on the right, a line of explanation and a slider that ticks at every step.
 *
 * The value is written on every step while the finger moves, so the island on top of the screen changes with it. The
 * thumb follows the finger from its own state in the meantime: the value that comes back from storage is a step behind
 * and would make it stutter. [onStart] runs once at the start of each drag.
 */
@Composable
internal fun SliderRow(
    index: Int,
    total: Int,
    icon: Painter,
    title: String,
    body: String?,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    label: @Composable (Float) -> String,
    onChange: (Float) -> Unit,
    onStart: () -> Unit = {},
) {
    SliderBody(index, total, icon, title, body, value, range, step, label, onChange, onStart, auto = null, onAuto = {})
}

/**
 * A [SliderRow] for a size the island can work out by itself. Auto greys the slider and shows the guess on it; switching
 * Auto off starts from that guess, so the island does not jump when the slider takes over.
 */
@Composable
internal fun AutoSliderRow(
    index: Int,
    total: Int,
    icon: Painter,
    title: String,
    body: String?,
    autoBody: String,
    value: Float?,
    guess: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    label: @Composable (Float) -> String,
    onChange: (Float?) -> Unit,
    onStart: () -> Unit = {},
) {
    val auto = value == null
    SliderBody(
        index, total, icon, title, if (auto) autoBody else body, value ?: guess.coerceIn(range), range, step, label,
        onChange = { onChange(it) }, onStart = onStart, auto = auto,
        onAuto = { on -> onChange(if (on) null else snapTo(guess, range, step)) },
    )
}

@Composable
private fun SliderBody(
    index: Int,
    total: Int,
    icon: Painter,
    title: String,
    body: String?,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    label: @Composable (Float) -> String,
    onChange: (Float) -> Unit,
    onStart: () -> Unit,
    auto: Boolean?,
    onAuto: (Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var dragging by remember { mutableStateOf(false) }
    var moved by remember { mutableFloatStateOf(value) }
    var lastStep by remember { mutableFloatStateOf(snapTo(value, range, step)) }
    LaunchedEffect(value, dragging) {
        if (!dragging) {
            moved = value
            lastStep = snapTo(value, range, step)
        }
    }
    val locked = auto == true

    GroupedRow(index, total) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                RowIcon(icon)
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (auto != null) {
                    ToggleButton(
                        checked = auto,
                        onCheckedChange = { onAuto(it) },
                        modifier = Modifier.height(36.dp),
                        // Unchecked it sits on the row's own colour, so it gets a step lighter fill to still read as a button.
                        colors = ToggleButtonDefaults.toggleButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                    ) {
                        Text(stringResource(R.string.value_auto), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    AnimatedContent(
                        targetState = body.orEmpty(),
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        modifier = Modifier.weight(1f),
                        label = "sliderBody",
                    ) { text ->
                        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Under Auto the guess stays readable, quieter, so it is clear where the slider starts. As wide as the
                    // widest value it can show, with digits of one width, so the text beside it never rewraps while dragging.
                    val valueStyle = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum")
                    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
                    val ends = listOf(label(range.start), label(range.endInclusive), label(snapTo((range.start + range.endInclusive) / 2f, range, step)))
                    val widest = with(androidx.compose.ui.platform.LocalDensity.current) {
                        remember(ends, valueStyle) { ends.maxOf { measurer.measure(it, valueStyle).size.width } }.toDp()
                    }
                    Text(
                        label(snapTo(moved, range, step)),
                        style = valueStyle,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        color = if (locked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp).widthIn(min = widest),
                    )
                }
                Slider(
                    value = moved,
                    enabled = !locked,
                    onValueChange = {
                        if (!dragging) {
                            dragging = true
                            onStart()
                        }
                        moved = it
                        val now = snapTo(it, range, step)
                        if (now != lastStep) {
                            lastStep = now
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            onChange(now)
                        }
                    },
                    onValueChangeFinished = {
                        dragging = false
                        onChange(snapTo(moved, range, step))
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    },
                    valueRange = range,
                )
            }
        }
    }
}

/** The nearest step inside the range, so the stored value is always one the slider can show. */
internal fun snapTo(raw: Float, range: ClosedFloatingPointRange<Float>, step: Float): Float {
    if (step <= 0f) return raw.coerceIn(range)
    val steps = ((raw - range.start) / step).roundToInt()
    return (range.start + steps * step).coerceIn(range)
}

@Composable
internal fun dpLabel(value: Float): String = stringResource(R.string.value_dp, value.roundToInt())

@Composable
internal fun percentLabel(value: Float): String = stringResource(R.string.value_percent, (value * 100).roundToInt())
