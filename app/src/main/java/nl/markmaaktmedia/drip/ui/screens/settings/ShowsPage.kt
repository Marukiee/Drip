package nl.markmaaktmedia.drip.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.data.IslandSettings
import nl.markmaaktmedia.drip.engine.Access
import nl.markmaaktmedia.drip.ui.components.SectionHeader
import nl.markmaaktmedia.drip.ui.components.SettingsGroup
import nl.markmaaktmedia.drip.ui.components.SwitchRow
import nl.markmaaktmedia.drip.ui.screens.rememberAccess
import nl.markmaaktmedia.drip.ui.theme.DripIcons

/** One switch of the island, with the setting it reads and the change it makes. */
private class Toggle(
    val key: String,
    val icon: @Composable () -> Painter,
    val title: Int,
    val subtitle: Int?,
    /** Shows its content only with notification access. */
    val needsListener: Boolean,
    val read: (IslandSettings) -> Boolean,
    val write: (IslandSettings, Boolean) -> IslandSettings,
)

private val Shown = listOf(
    Toggle(FocusKeys.ShowMusic, { DripIcons.Music }, R.string.show_music, R.string.show_music_sub, true, { it.showMusic }, { s, v -> s.copy(showMusic = v) }),
    Toggle(FocusKeys.ShowCalls, { DripIcons.Call }, R.string.show_calls, R.string.show_calls_sub, true, { it.showCalls }, { s, v -> s.copy(showCalls = v) }),
    Toggle(FocusKeys.ShowLive, { DripIcons.Timer }, R.string.show_live, R.string.show_live_sub, true, { it.showLive }, { s, v -> s.copy(showLive = v) }),
    Toggle(FocusKeys.ShowNotifications, { DripIcons.Notifications }, R.string.show_notifications, R.string.show_notifications_sub, true, { it.showNotifications }, { s, v -> s.copy(showNotifications = v) }),
    Toggle(FocusKeys.ShowCharging, { DripIcons.BatteryCharging }, R.string.show_charging, R.string.show_charging_sub, false, { it.showCharging }, { s, v -> s.copy(showCharging = v) }),
    Toggle(FocusKeys.ShowTorch, { DripIcons.Flashlight }, R.string.show_torch, R.string.show_torch_sub, false, { it.showTorch }, { s, v -> s.copy(showTorch = v) }),
    Toggle(FocusKeys.ShowPrivacy, { DripIcons.Mic }, R.string.show_privacy, R.string.show_privacy_sub, false, { it.showPrivacy }, { s, v -> s.copy(showPrivacy = v) }),
)

private val Announced = listOf(
    Toggle(FocusKeys.AlertBatteryLow, { DripIcons.BatteryAlert }, R.string.alert_battery_low, null, false, { it.alertBatteryLow }, { s, v -> s.copy(alertBatteryLow = v) }),
    Toggle(FocusKeys.AlertBatteryFull, { DripIcons.Battery }, R.string.alert_battery_full, null, false, { it.alertBatteryFull }, { s, v -> s.copy(alertBatteryFull = v) }),
    Toggle(FocusKeys.AlertBatterySaver, { DripIcons.BatterySaver }, R.string.alert_battery_saver, null, false, { it.alertBatterySaver }, { s, v -> s.copy(alertBatterySaver = v) }),
    Toggle(FocusKeys.AlertDnd, { DripIcons.Dnd }, R.string.alert_dnd, null, false, { it.alertDnd }, { s, v -> s.copy(alertDnd = v) }),
    Toggle(FocusKeys.AlertRotation, { DripIcons.ScreenRotation }, R.string.alert_rotation, null, false, { it.alertRotation }, { s, v -> s.copy(alertRotation = v) }),
    Toggle(FocusKeys.AlertRinger, { DripIcons.Ring }, R.string.alert_ringer, null, false, { it.alertRinger }, { s, v -> s.copy(alertRinger = v) }),
    Toggle(FocusKeys.AlertVolume, { DripIcons.VolumeUp }, R.string.alert_volume, null, false, { it.alertVolume }, { s, v -> s.copy(alertVolume = v) }),
    Toggle(FocusKeys.AlertBrightness, { DripIcons.Brightness }, R.string.alert_brightness, null, false, { it.alertBrightness }, { s, v -> s.copy(alertBrightness = v) }),
    Toggle(FocusKeys.AlertHeadphones, { DripIcons.Headphones }, R.string.alert_headphones, null, false, { it.alertHeadphones }, { s, v -> s.copy(alertHeadphones = v) }),
)

/** What the island shows: what it follows while it runs, and the short announcements it makes. */
@Composable
internal fun ShowsPage(onBack: () -> Unit) {
    val (island, edit) = rememberIsland()

    SettingsPageFrame(stringResource(R.string.settings_cat_shows), onBack) {
        SectionHeader(stringResource(R.string.shows_section), top = 20.dp)
        Toggles(Shown, island, edit)
        LocationPermissionRow(island)

        MoreShows(island, edit)

        SectionHeader(stringResource(R.string.shows_section_alerts), top = 20.dp)
        Toggles(Announced, island, edit)
        BluetoothPermissionRow(island)

        OtherAppsSection(island, edit)
    }
}

@Composable
private fun Toggles(rows: List<Toggle>, island: IslandSettings, edit: IslandEdit) {
    val context = LocalContext.current
    val access = rememberAccess()
    val needs = stringResource(R.string.needs_listener)
    SettingsGroup {
        rows.forEachIndexed { index, row ->
            SettingsTarget(row.key, index, rows.size) {
                SwitchRow(
                    index, rows.size, row.icon(), stringResource(row.title), row.subtitle?.let { stringResource(it) },
                    row.read(island), { on -> edit { row.write(it, on) } },
                    blocked = if (row.needsListener && !access.listener) needs else null,
                    onBlocked = { Access.openListener(context) },
                )
            }
        }
    }
}
