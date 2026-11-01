package nl.markmaaktmedia.drip.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.data.Gesture
import nl.markmaaktmedia.drip.data.GestureAction
import nl.markmaaktmedia.drip.data.GestureScope
import nl.markmaaktmedia.drip.data.IslandSettings
import nl.markmaaktmedia.drip.ui.components.DropdownRow
import nl.markmaaktmedia.drip.ui.components.GroupedRow
import nl.markmaaktmedia.drip.ui.components.SectionHeader
import nl.markmaaktmedia.drip.ui.components.SegmentedPillRow
import nl.markmaaktmedia.drip.ui.components.SettingsGroup
import nl.markmaaktmedia.drip.ui.theme.DripIcons

private class GestureRow(
    val gesture: Gesture,
    val key: String,
    @param:StringRes val title: Int,
    val icon: @Composable () -> Painter,
)

private val Rows = listOf(
    GestureRow(Gesture.TAP, FocusKeys.GestureTap, R.string.gesture_tap) { DripIcons.Touch },
    GestureRow(Gesture.DOUBLE_TAP, FocusKeys.GestureDoubleTap, R.string.set2_gesture_double_tap) { DripIcons.Touch },
    GestureRow(Gesture.LONG_PRESS, FocusKeys.GestureHold, R.string.gesture_hold) { DripIcons.Touch },
    GestureRow(Gesture.SWIPE_DOWN, FocusKeys.GestureDown, R.string.gesture_swipe_down) { DripIcons.KeyDown },
    GestureRow(Gesture.SWIPE_LEFT, FocusKeys.GestureLeft, R.string.gesture_swipe_left) { DripIcons.KeyLeft },
    GestureRow(Gesture.SWIPE_RIGHT, FocusKeys.GestureRight, R.string.gesture_swipe_right) { DripIcons.KeyRight },
)

/** True when [gesture] has an action of its own on [scope] instead of following the default. */
internal fun IslandSettings.overrides(scope: GestureScope, gesture: Gesture): Boolean =
    scope != GestureScope.DEFAULT && gestureOverrides.any { it.startsWith("${scope.name}:${gesture.name}=") }

/**
 * Gestures: what each touch on the island does, by default and for one kind of thing on it. On a kind, a row that
 * follows the default shows that action in the quiet colour, and "Same as default" in its menu puts it back.
 */
@Composable
internal fun GesturesPage(onBack: () -> Unit) {
    val (island, edit) = rememberIsland()
    var scope by rememberSaveable { mutableStateOf(GestureScope.DEFAULT) }
    val actions: List<Pair<GestureAction?, String>> = GestureAction.entries.map { it to stringResource(gestureLabel(it)) }
    val same = stringResource(R.string.set2_gesture_same)
    val scopeNames = GestureScope.entries.associateWith { stringResource(scopeLabel(it)) }

    SettingsPageFrame(stringResource(R.string.settings_cat_gestures), onBack) {
        SectionHeader(stringResource(R.string.set2_gesture_scope), top = 20.dp)
        // Seven kinds do not fit side by side on a phone, and a cut off label is worse than a row that scrolls.
        SettingsTarget(FocusKeys.GestureScope, 0, 1) {
            GroupedRow(0, 1) {
                Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp)) {
                    SegmentedPillRow(
                        options = GestureScope.entries,
                        selected = scope,
                        label = { scopeNames.getValue(it) },
                        onSelect = { scope = it },
                    )
                }
            }
        }

        val header = if (scope == GestureScope.DEFAULT) {
            stringResource(R.string.gestures_section)
        } else {
            stringResource(R.string.set2_gesture_scope_note, scopeNames.getValue(scope))
        }
        SectionHeader(header, top = 20.dp)
        SettingsGroup {
            Rows.forEachIndexed { index, row ->
                val inherited = scope != GestureScope.DEFAULT && !island.overrides(scope, row.gesture)
                // On the default there is nothing to follow: null there would mean the factory setting.
                val options = if (scope == GestureScope.DEFAULT) actions else listOf<Pair<GestureAction?, String>>(null to same) + actions
                SettingsTarget(row.key, index, Rows.size) {
                    DropdownRow(
                        index, Rows.size, row.icon(), stringResource(row.title), null,
                        value = stringResource(gestureLabel(island.actionFor(scope, row.gesture))),
                        options = options,
                        onSelect = { action -> edit { it.withGesture(scope, row.gesture, action) } },
                        valueMuted = inherited,
                    )
                }
            }
        }
    }
}

@StringRes
internal fun scopeLabel(scope: GestureScope): Int = when (scope) {
    GestureScope.DEFAULT -> R.string.set2_scope_default
    GestureScope.MUSIC -> R.string.set2_scope_music
    GestureScope.CALL -> R.string.set2_scope_call
    GestureScope.TIMER -> R.string.set2_scope_timer
    GestureScope.ROUTE -> R.string.set2_scope_route
    GestureScope.CHARGING -> R.string.set2_scope_charging
    GestureScope.NOTICE -> R.string.set2_scope_notice
}

@StringRes
internal fun gestureLabel(action: GestureAction): Int = when (action) {
    GestureAction.NONE -> R.string.gesture_none
    GestureAction.EXPAND -> R.string.gesture_expand
    GestureAction.OPEN_APP -> R.string.gesture_open_app
    GestureAction.PLAY_PAUSE -> R.string.gesture_play_pause
    GestureAction.NEXT_TRACK -> R.string.gesture_next
    GestureAction.PREVIOUS_TRACK -> R.string.gesture_previous
    GestureAction.BACK -> R.string.gesture_back
    GestureAction.HOME -> R.string.gesture_home
    GestureAction.RECENTS -> R.string.gesture_recents
    GestureAction.NOTIFICATIONS -> R.string.gesture_notifications
    GestureAction.QUICK_SETTINGS -> R.string.gesture_quick_settings
    GestureAction.LOCK_SCREEN -> R.string.gesture_lock
    GestureAction.SCREENSHOT -> R.string.gesture_screenshot
    GestureAction.TORCH -> R.string.gesture_torch
    GestureAction.OPEN_DRIP -> R.string.gesture_open_drip
    GestureAction.OUTPUT -> R.string.gesture_output
    GestureAction.DISMISS -> R.string.gesture_dismiss
}
