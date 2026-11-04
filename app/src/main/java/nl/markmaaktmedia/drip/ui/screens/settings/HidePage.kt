package nl.markmaaktmedia.drip.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.ui.AppPickerKind
import nl.markmaaktmedia.drip.ui.Route
import nl.markmaaktmedia.drip.ui.components.ActionRow
import nl.markmaaktmedia.drip.ui.components.SectionHeader
import nl.markmaaktmedia.drip.ui.components.SettingsGroup
import nl.markmaaktmedia.drip.ui.components.SwitchRow
import nl.markmaaktmedia.drip.ui.routeBounds
import nl.markmaaktmedia.drip.ui.routeKey
import nl.markmaaktmedia.drip.ui.theme.DripIcons

/** When the island steps aside: full screen, landscape, the lock screen, and the apps chosen for it. */
@Composable
internal fun HidePage(onBack: () -> Unit, onOpen: (Route) -> Unit) {
    val (island, edit) = rememberIsland()

    SettingsPageFrame(stringResource(R.string.settings_cat_hide), onBack) {
        SectionHeader(stringResource(R.string.hide_section), top = 20.dp)
        SettingsGroup {
            SettingsTarget(FocusKeys.HideFullscreen, 0, 4) {
                SwitchRow(
                    0, 4, DripIcons.OpenInFull, stringResource(R.string.hide_fullscreen), stringResource(R.string.hide_fullscreen_sub),
                    island.hideFullscreen, { on -> edit { it.copy(hideFullscreen = on) } },
                )
            }
            SettingsTarget(FocusKeys.HideLandscape, 1, 4) {
                SwitchRow(
                    1, 4, DripIcons.ScreenRotation, stringResource(R.string.hide_landscape), stringResource(R.string.hide_landscape_sub),
                    island.hideLandscape, { on -> edit { it.copy(hideLandscape = on) } },
                )
            }
            SettingsTarget(FocusKeys.HideLock, 2, 4) {
                SwitchRow(
                    2, 4, DripIcons.Lock, stringResource(R.string.hide_lock), stringResource(R.string.hide_lock_sub),
                    island.hideOnLockscreen, { on -> edit { it.copy(hideOnLockscreen = on) } },
                )
            }
            SettingsTarget(FocusKeys.HidePlayer, 3, 4) {
                SwitchRow(
                    3, 4, DripIcons.Music, stringResource(R.string.hide_player), stringResource(R.string.hide_player_sub),
                    island.hideInPlayer, { on -> edit { it.copy(hideInPlayer = on) } },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        val route = Route.AppPicker(AppPickerKind.HIDE)
        SettingsGroup {
            ActionRow(
                0, 1, DripIcons.Apps, stringResource(R.string.hide_apps),
                if (island.hiddenApps.isEmpty()) stringResource(R.string.apps_none)
                else pluralStringResource(R.plurals.apps_count, island.hiddenApps.size, island.hiddenApps.size),
                { onOpen(route) }, modifier = Modifier.routeBounds(routeKey(route)),
            )
        }
    }
}
