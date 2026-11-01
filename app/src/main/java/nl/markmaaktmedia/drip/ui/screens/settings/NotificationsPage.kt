package nl.markmaaktmedia.drip.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import nl.markmaaktmedia.drip.ui.theme.DripMotion

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
        SettingsGroup {
            SettingsTarget(FocusKeys.NotifyWhich, 0, 1) {
                SegmentedRow(
                    0, 1, DripIcons.Apps, stringResource(R.string.notif_which),
                    listOf(true, false), island.notifyAllApps,
                    { stringResource(if (it) R.string.notif_all else R.string.notif_chosen) },
                ) { all -> edit { it.copy(notifyAllApps = all) } }
            }
        }
        AnimatedVisibility(
            visible = !island.notifyAllApps,
            enter = expandVertically(DripMotion.sizeSpring()) + fadeIn(DripMotion.fadeSpec()),
            exit = shrinkVertically(DripMotion.sizeSpring()) + fadeOut(DripMotion.fadeSpec()),
        ) {
            val route = Route.AppPicker(AppPickerKind.NOTIFY)
            SettingsGroup(Modifier.padding(top = 12.dp)) {
                ActionRow(
                    0, 1, DripIcons.Apps, stringResource(R.string.notif_apps),
                    if (island.notifyApps.isEmpty()) stringResource(R.string.apps_none)
                    else pluralStringResource(R.plurals.apps_count, island.notifyApps.size, island.notifyApps.size),
                    { onOpen(route) }, modifier = Modifier.routeBounds(routeKey(route)),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

