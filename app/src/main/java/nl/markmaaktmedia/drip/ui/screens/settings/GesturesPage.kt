package nl.markmaaktmedia.drip.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.data.GestureAction
import nl.markmaaktmedia.drip.data.IslandSettings
import nl.markmaaktmedia.drip.ui.components.DropdownRow
import nl.markmaaktmedia.drip.ui.components.SectionHeader
import nl.markmaaktmedia.drip.ui.components.SettingsGroup
import nl.markmaaktmedia.drip.ui.theme.DripIcons

private class Gesture(
    val key: String,
    @param:StringRes val title: Int,
    val icon: @Composable () -> Painter,
    val read: (IslandSettings) -> GestureAction,
    val write: (IslandSettings, GestureAction) -> IslandSettings,
)

private val Gestures = listOf(
    Gesture(FocusKeys.GestureTap, R.string.gesture_tap, { DripIcons.Touch }, { it.gestureTap }, { s, a -> s.copy(gestureTap = a) }),
    Gesture(FocusKeys.GestureHold, R.string.gesture_hold, { DripIcons.Touch }, { it.gestureLongPress }, { s, a -> s.copy(gestureLongPress = a) }),
    Gesture(FocusKeys.GestureDown, R.string.gesture_swipe_down, { DripIcons.KeyDown }, { it.gestureSwipeDown }, { s, a -> s.copy(gestureSwipeDown = a) }),
    Gesture(FocusKeys.GestureLeft, R.string.gesture_swipe_left, { DripIcons.KeyLeft }, { it.gestureSwipeLeft }, { s, a -> s.copy(gestureSwipeLeft = a) }),
    Gesture(FocusKeys.GestureRight, R.string.gesture_swipe_right, { DripIcons.KeyRight }, { it.gestureSwipeRight }, { s, a -> s.copy(gestureSwipeRight = a) }),
)

/** Gestures: what each touch on the island does. The menu drops down under the row, so the choice can be read whole. */
@Composable
internal fun GesturesPage(onBack: () -> Unit) {
    val (island, edit) = rememberIsland()
    val options = GestureAction.entries.map { it to stringResource(gestureLabel(it)) }

    SettingsPageFrame(stringResource(R.string.settings_cat_gestures), onBack) {
        SectionHeader(stringResource(R.string.gestures_section), top = 20.dp)
        SettingsGroup {
            Gestures.forEachIndexed { index, gesture ->
                val current = gesture.read(island)
                SettingsTarget(gesture.key, index, Gestures.size) {
                    DropdownRow(
                        index, Gestures.size, gesture.icon(), stringResource(gesture.title), null,
                        value = stringResource(gestureLabel(current)),
                        options = options,
                        onSelect = { action -> edit { gesture.write(it, action) } },
                    )
                }
            }
        }
    }
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
}
