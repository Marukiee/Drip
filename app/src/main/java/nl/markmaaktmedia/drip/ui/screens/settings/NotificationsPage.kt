package nl.markmaaktmedia.drip.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.engine.Access
import nl.markmaaktmedia.drip.engine.IslandKeeper
import nl.markmaaktmedia.drip.ui.AppPickerKind
import nl.markmaaktmedia.drip.ui.Route
import nl.markmaaktmedia.drip.ui.components.ActionRow
import nl.markmaaktmedia.drip.ui.components.SectionHeader
import nl.markmaaktmedia.drip.ui.components.SettingsGroup
import nl.markmaaktmedia.drip.ui.components.SwitchRow
import nl.markmaaktmedia.drip.ui.routeBounds
import nl.markmaaktmedia.drip.ui.routeKey
import nl.markmaaktmedia.drip.ui.screens.rememberAccess
import nl.markmaaktmedia.drip.ui.theme.DripIcons

/** Notifications: whether they open the island, from which apps, and how much of them it shows. */
@Composable
internal fun NotificationsPage(onBack: () -> Unit, onOpen: (Route) -> Unit) {
    val context = LocalContext.current
    val (island, edit) = rememberIsland()
    val access = rememberAccess()
    val blocked = if (access.listener) null else stringResource(R.string.needs_listener)
    val openListener = { Access.openListener(context) }

    SettingsPageFrame(stringResource(R.string.settings_cat_notifications), onBack) {
        AnnouncementTime(island, edit)

        SectionHeader(stringResource(R.string.shows_section), top = 20.dp)
        SettingsGroup {
            SettingsTarget(FocusKeys.NotifyOn, 0, 3) {
                SwitchRow(
                    0, 3, DripIcons.Notifications, stringResource(R.string.notif_on), stringResource(R.string.notif_on_sub),
                    island.showNotifications, { on -> edit { it.copy(showNotifications = on) } },
                    blocked = blocked, onBlocked = openListener,
                )
            }
            SettingsTarget(FocusKeys.NotifyText, 1, 3) {
                SwitchRow(
                    1, 3, DripIcons.File, stringResource(R.string.notif_text), stringResource(R.string.notif_text_sub),
                    island.notifyShowText, { on -> edit { it.copy(notifyShowText = on) } },
                    enabled = island.showNotifications, blocked = blocked, onBlocked = openListener,
                )
            }
            // Needs the permission of Keep the island on; without it the switch is grey and says where to get it.
            val canHide = IslandKeeper.canKeep(context)
            SettingsTarget(FocusKeys.NotifyHidePopups, 2, 3) {
                SwitchRow(
                    2, 3, DripIcons.NotificationsOff, stringResource(R.string.notif_hide_popups), stringResource(R.string.notif_hide_popups_sub),
                    island.hideSystemPopups && canHide, { on -> edit { it.copy(hideSystemPopups = on) } },
                    enabled = island.showNotifications,
                    blocked = blocked ?: if (canHide) null else stringResource(R.string.notif_hide_popups_blocked),
                    onBlocked = if (blocked != null) openListener else ({ onOpen(Route.SettingsPage(SettingsPageId.Keep)) }),
                )
            }
        }

        SectionHeader(stringResource(R.string.notif_section_apps), top = 20.dp)
        // Always the list, as in Tandem: with all apps on every app is ticked, and one switched off there stays quiet.
        SettingsGroup {
            SettingsTarget(FocusKeys.NotifyWhich, 0, 2) {
                SwitchRow(
                    0, 2, DripIcons.CheckCircle, stringResource(R.string.notif_all), stringResource(R.string.notif_all_sub),
                    island.notifyAllApps, { on -> edit { it.copy(notifyAllApps = on) } },
                )
            }
            val route = Route.AppPicker(AppPickerKind.NOTIFY)
            ActionRow(
                1, 2, DripIcons.Apps, stringResource(R.string.notif_apps), notifyAppsSummary(island),
                { onOpen(route) }, modifier = Modifier.routeBounds(routeKey(route)),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** All apps, all but a few, or the chosen few. */
@Composable
internal fun notifyAppsSummary(island: nl.markmaaktmedia.drip.data.IslandSettings): String = when {
    island.notifyAllApps && island.notifyExcluded.isEmpty() -> stringResource(R.string.notif_all)
    island.notifyAllApps -> pluralStringResource(R.plurals.apps_off_count, island.notifyExcluded.size, island.notifyExcluded.size)
    island.notifyApps.isEmpty() -> stringResource(R.string.apps_none)
    else -> pluralStringResource(R.plurals.apps_count, island.notifyApps.size, island.notifyApps.size)
}
