package nl.markmaaktmedia.drip.ui.screens.settings

import nl.markmaaktmedia.drip.island.IslandLayout
import nl.markmaaktmedia.drip.island.DeviceFit
import android.view.RoundedCorner
import android.view.WindowInsets
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.data.FitMode
import nl.markmaaktmedia.drip.data.IslandSettings
import nl.markmaaktmedia.drip.data.withFit
import nl.markmaaktmedia.drip.graph
import nl.markmaaktmedia.drip.island.Demo
import nl.markmaaktmedia.drip.island.ui.IslandPreview
import nl.markmaaktmedia.drip.ui.components.ActionRow
import nl.markmaaktmedia.drip.ui.components.SectionHeader
import nl.markmaaktmedia.drip.ui.components.SegmentedPillRow
import nl.markmaaktmedia.drip.ui.components.SettingsGroup
import nl.markmaaktmedia.drip.ui.components.SwitchRow
import nl.markmaaktmedia.drip.ui.theme.CardSquircle
import nl.markmaaktmedia.drip.ui.theme.DripIcons
import nl.markmaaktmedia.drip.ui.theme.DripMotion

/**
 * Shape and fit: how the island sits on this phone, and every size and corner of it. Each change is written at once, so
 * the real island at the top of the screen follows the slider, and the preview above the rows does the same.
 */
@Composable
internal fun ShapePage(onBack: () -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val (island, edit) = rememberIsland()
    val guess = rememberAutoGuess(island)
    val hasCutout = remember { DeviceFit.read(context).cutout != null }
    var showOpened by remember { mutableStateOf(false) }

    // The opened island is only on screen when something opens it, so each drag of one of its sliders opens it (it may
    // have closed by itself since the last one), and a slider of the closed island closes it again.
    val opened: () -> Unit = {
        graph.hub.demo(Demo.EXPAND)
        showOpened = true
    }
    val closed: () -> Unit = {
        if (showOpened) graph.hub.demo(Demo.COLLAPSE)
        showOpened = false
    }

    // The preview stays in view while the rows scroll, so every slider can be watched at work.
    SettingsPageFrame(stringResource(R.string.settings_cat_shape), onBack, pinned = {
        Column(
            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp).clip(CardSquircle).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IslandPreview(island, Modifier.clip(CardSquircle), expanded = showOpened)
            val closedLabel = stringResource(R.string.shape_closed)
            val openedLabel = stringResource(R.string.shape_opened)
            SegmentedPillRow(
                options = listOf(false, true),
                selected = showOpened,
                label = { if (it) openedLabel else closedLabel },
                onSelect = { if (it) opened() else closed() },
                modifier = Modifier.fillMaxWidth(),
                equalWidth = true,
            )
        }
    }) {
        SectionHeader(stringResource(R.string.shape_section_fit), top = 8.dp)
        SettingsGroup {
            SettingsTarget(FocusKeys.ShapeFit, 0, 2) {
                SegmentedRow(
                    0, 2, DripIcons.FitScreen, stringResource(R.string.shape_fit),
                    FitMode.entries, island.fitMode, { stringResource(fitLabel(it)) },
                ) { mode -> edit { it.withFit(mode, hasCutout) } }
            }
            SettingsTarget(FocusKeys.ShapeIdleVisible, 1, 2) {
                SwitchRow(
                    1, 2, DripIcons.Visibility, stringResource(R.string.shape_idle_visible), stringResource(R.string.shape_idle_visible_sub),
                    island.idleVisible, { on -> edit { it.copy(idleVisible = on) } },
                )
            }
        }
        AnimatedVisibility(
            visible = island.fitMode == FitMode.AUTO,
            enter = expandVertically(DripMotion.sizeSpring()) + fadeIn(DripMotion.fadeSpec()),
            exit = shrinkVertically(DripMotion.sizeSpring()) + fadeOut(DripMotion.fadeSpec()),
        ) {
            Text(
                stringResource(R.string.fit_auto_note),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
            )
        }
        AnimatedVisibility(
            visible = island.fitMode == FitMode.CUTOUT && !hasCutout,
            enter = expandVertically(DripMotion.sizeSpring()) + fadeIn(DripMotion.fadeSpec()),
            exit = shrinkVertically(DripMotion.sizeSpring()) + fadeOut(DripMotion.fadeSpec()),
        ) {
            Text(
                stringResource(R.string.fit_ring_note),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
            )
        }

        SectionHeader(stringResource(R.string.shape_section_closed), top = 20.dp)
        SettingsGroup {
            val sizeAuto = stringResource(R.string.shape_size_auto)
            SettingsTarget(FocusKeys.ShapeIdleWidth, 0, 5) {
                AutoSliderRow(
                    0, 5, DripIcons.Straighten, stringResource(R.string.shape_idle_width), null, sizeAuto,
                    island.idleWidthDp, guess.idleWidth, 20f..260f, 1f, { dpLabel(it) },
                    onChange = { v -> edit { it.copy(idleWidthDp = v) } }, onStart = closed,
                )
            }
            SettingsTarget(FocusKeys.ShapeIdleHeight, 1, 5) {
                AutoSliderRow(
                    1, 5, DripIcons.Straighten, stringResource(R.string.shape_idle_height), null, sizeAuto,
                    island.idleHeightDp, guess.idleHeight, 12f..64f, 1f, { dpLabel(it) },
                    onChange = { v -> edit { it.copy(idleHeightDp = v) } }, onStart = closed,
                )
            }
            SettingsTarget(FocusKeys.ShapeCompactWidth, 2, 5) {
                AutoSliderRow(
                    2, 5, DripIcons.Straighten, stringResource(R.string.shape_compact_width),
                    stringResource(R.string.shape_compact_width_sub), sizeAuto,
                    island.compactWidthDp, guess.compactWidth, 100f..400f, 2f, { dpLabel(it) },
                    onChange = { v -> edit { it.copy(compactWidthDp = v) } },
                    onStart = { closed(); graph.hub.demo(Demo.CHARGING) },
                )
            }
            SettingsTarget(FocusKeys.ShapeShoulder, 3, 5) {
                SliderRow(
                    3, 5, DripIcons.Contrast, stringResource(R.string.shape_shoulder), stringResource(R.string.shape_shoulder_sub),
                    island.shoulderDp, 0f..24f, 1f, { dpLabel(it) },
                    onChange = { v -> edit { it.copy(shoulderDp = v) } }, onStart = closed,
                )
            }
            SettingsTarget(FocusKeys.ShapeRoundness, 4, 5) {
                SliderRow(
                    4, 5, DripIcons.Contrast, stringResource(R.string.shape_roundness), stringResource(R.string.shape_roundness_sub),
                    island.collapsedRoundness, 0f..1f, 0.05f, { percentLabel(it) },
                    onChange = { v -> edit { it.copy(collapsedRoundness = v) } }, onStart = closed,
                )
            }
        }

        SectionHeader(stringResource(R.string.shape_section_opened), top = 20.dp)
        SettingsGroup {
            SettingsTarget(FocusKeys.ShapeMargin, 0, 6) {
                SliderRow(
                    0, 6, DripIcons.OpenInFull, stringResource(R.string.shape_margin), stringResource(R.string.shape_margin_sub),
                    island.expandedMarginDp, 0f..24f, 1f, { dpLabel(it) },
                    onChange = { v -> edit { it.copy(expandedMarginDp = v) } }, onStart = opened,
                )
            }
            SettingsTarget(FocusKeys.ShapeTop, 1, 6) {
                AutoSliderRow(
                    1, 6, DripIcons.Contrast, stringResource(R.string.shape_top_corners), null,
                    stringResource(R.string.shape_top_corners_auto),
                    island.expandedTopRadiusDp, guess.topRadius, 0f..80f, 1f, { dpLabel(it) },
                    onChange = { v -> edit { it.copy(expandedTopRadiusDp = v) } }, onStart = opened,
                )
            }
            SettingsTarget(FocusKeys.ShapeBottom, 2, 6) {
                SliderRow(
                    2, 6, DripIcons.Contrast, stringResource(R.string.shape_bottom_corners), stringResource(R.string.shape_bottom_corners_sub),
                    island.expandedBottomRadiusDp, 0f..80f, 1f, { dpLabel(it) },
                    onChange = { v -> edit { it.copy(expandedBottomRadiusDp = v) } }, onStart = opened,
                )
            }
            SettingsTarget(FocusKeys.ShapeButtonOuter, 3, 6) {
                AutoSliderRow(
                    3, 6, DripIcons.Touch, stringResource(R.string.shape_button_outer), null,
                    stringResource(R.string.shape_button_outer_auto),
                    island.buttonOuterRadiusDp, guess.buttonOuter, 0f..60f, 1f, { dpLabel(it) },
                    onChange = { v -> edit { it.copy(buttonOuterRadiusDp = v) } }, onStart = opened,
                )
            }
            SettingsTarget(FocusKeys.ShapeButtonInner, 4, 6) {
                SliderRow(
                    4, 6, DripIcons.Touch, stringResource(R.string.shape_button_inner), stringResource(R.string.shape_button_inner_sub),
                    island.buttonInnerRadiusDp, 0f..32f, 1f, { dpLabel(it) },
                    onChange = { v -> edit { it.copy(buttonInnerRadiusDp = v) } }, onStart = opened,
                )
            }
            SettingsTarget(FocusKeys.ShapeAttached, 5, 6) {
                SwitchRow(
                    5, 6, DripIcons.Pin, stringResource(R.string.shape_attached), stringResource(R.string.shape_attached_sub),
                    island.expandedAttached,
                    { on ->
                        opened()
                        edit { it.copy(expandedAttached = on) }
                    },
                )
            }
        }

        // Back to how the place came, at the top of the section: the sliders here are the easiest to lose.
        val placeMoved = island.offsetXDp != 0f || island.offsetYDp != 0f || island.cutoutPaddingDp != IslandSettings().cutoutPaddingDp
        // The button keeps its room while hidden, so the rows below never shift when it comes or goes.
        val resetAlpha by androidx.compose.animation.core.animateFloatAsState(if (placeMoved) 1f else 0f, DripMotion.fadeSpec(), label = "placeReset")
        SectionHeader(stringResource(R.string.shape_section_place), top = 20.dp, trailing = {
            androidx.compose.material3.TextButton(
                onClick = { edit { it.copy(offsetXDp = 0f, offsetYDp = 0f, cutoutPaddingDp = IslandSettings().cutoutPaddingDp) } },
                enabled = placeMoved,
                modifier = Modifier.graphicsLayer { alpha = resetAlpha },
            ) {
                androidx.compose.material3.Icon(DripIcons.Restart, null, Modifier.padding(end = 6.dp).height(18.dp))
                Text(stringResource(R.string.shape_place_reset))
            }
        })
        SettingsGroup {
            val nudge = stringResource(R.string.shape_nudge_sub)
            SettingsTarget(FocusKeys.ShapeNudgeX, 0, 3) {
                SliderRow(
                    0, 3, DripIcons.Drag, stringResource(R.string.shape_nudge_x), nudge,
                    island.offsetXDp, -40f..40f, 1f, { dpLabel(it) },
                    onChange = { v -> edit { it.copy(offsetXDp = v) } }, onStart = closed,
                )
            }
            SettingsTarget(FocusKeys.ShapeNudgeY, 1, 3) {
                SliderRow(
                    1, 3, DripIcons.Drag, stringResource(R.string.shape_nudge_y), nudge,
                    island.offsetYDp, -20f..20f, 1f, { dpLabel(it) },
                    onChange = { v -> edit { it.copy(offsetYDp = v) } }, onStart = closed,
                )
            }
            SettingsTarget(FocusKeys.ShapeCutout, 2, 3) {
                SliderRow(
                    2, 3, DripIcons.Camera, stringResource(R.string.shape_cutout_padding), stringResource(R.string.shape_cutout_padding_sub),
                    island.cutoutPaddingDp, 0f..16f, 1f, { dpLabel(it) },
                    onChange = { v -> edit { it.copy(cutoutPaddingDp = v) } }, onStart = closed,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        SettingsGroup {
            ActionRow(
                0, 1, DripIcons.Restart, stringResource(R.string.shape_reset), stringResource(R.string.shape_reset_sub),
                { scope.launch { graph.prefs.resetFit() } },
                modifier = Modifier.settingsTarget(FocusKeys.ShapeReset, 0, 1),
            )
        }
    }
}

/** Where an Auto slider starts when Auto is switched off: close to what the island works out, in dp. */
internal class AutoGuess(
    val idleWidth: Float,
    val idleHeight: Float,
    val compactWidth: Float,
    val topRadius: Float,
    val buttonOuter: Float,
)

/**
 * What Auto means on this phone, worked out by the island's own arithmetic, so the number next to an Auto slider is the
 * size the island really has and switching Auto off does not move it at all.
 */
@Composable
private fun rememberAutoGuess(island: IslandSettings): AutoGuess {
    val context = LocalContext.current
    return remember(island) {
        val fit = DeviceFit.read(context)
        val auto = island.copy(idleWidthDp = null, idleHeightDp = null, compactWidthDp = null, expandedTopRadiusDp = null, buttonOuterRadiusDp = null)
        val l = IslandLayout(fit, auto)
        AutoGuess(
            idleWidth = l.idleWidth / fit.density,
            idleHeight = l.idleHeight / fit.density,
            compactWidth = l.compactWidth / fit.density,
            topRadius = l.expandedTopRadius / fit.density,
            buttonOuter = l.buttonOuterRadius / fit.density,
        )
    }
}
