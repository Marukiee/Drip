package nl.markmaaktmedia.drip.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.data.IslandSettings
import nl.markmaaktmedia.drip.engine.Access
import nl.markmaaktmedia.drip.sources.SourceGrants
import nl.markmaaktmedia.drip.ui.components.ActionRow
import nl.markmaaktmedia.drip.ui.components.SectionHeader
import nl.markmaaktmedia.drip.ui.components.SettingsGroup
import nl.markmaaktmedia.drip.ui.components.SwitchRow
import nl.markmaaktmedia.drip.ui.screens.PermissionCard
import nl.markmaaktmedia.drip.ui.screens.PermissionLevel
import nl.markmaaktmedia.drip.ui.screens.PermissionNote
import nl.markmaaktmedia.drip.ui.screens.PermissionNoteAction
import nl.markmaaktmedia.drip.ui.screens.rememberAccess
import nl.markmaaktmedia.drip.ui.theme.DripIcons
import nl.markmaaktmedia.drip.ui.theme.DripMotion

private const val AutomationDocsUrl = "https://github.com/Marukiee/Drip/blob/main/docs/AUTOMATION.md"

/** The lead times on offer for an appointment, in minutes. */
private val LeadMinutes = listOf(5, 10, 15, 30)

/** A runtime permission as the page sees it, and how to ask for it. */
internal class RuntimeGrant(val level: PermissionLevel, val request: () -> Unit)

/**
 * Asks for [permissions] and reads [check] again every time the app comes back to the front, since they can change in
 * a system screen. After one no Android may not show its question again, so the next tap goes to app info, as for
 * notifications.
 */
@Composable
internal fun rememberRuntimeGrant(permissions: Array<String>, check: (Context) -> PermissionLevel): RuntimeGrant {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { version++ }
    val level = remember(version) { check(context) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        asked = true
        version++
    }
    // The island service runs in this process and registers what it could not without the permission.
    var last by remember { mutableStateOf<PermissionLevel?>(null) }
    LaunchedEffect(level) {
        if (level != PermissionLevel.Off || (last != null && last != level)) SourceGrants.changed()
        last = level
    }
    return RuntimeGrant(level) { if (asked) Access.openAppInfo(context) else launcher.launch(permissions) }
}

private fun allOrNothing(allowed: Boolean) = if (allowed) PermissionLevel.On else PermissionLevel.Off

/** A permission card under the switch it belongs to, there only while that switch is on and the permission missing. */
@Composable
private fun PermissionSlot(
    visible: Boolean,
    icon: Painter,
    title: String,
    why: String,
    grant: RuntimeGrant,
    note: PermissionNote? = null,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(DripMotion.sizeSpring()) + fadeIn(DripMotion.fadeSpec()),
        exit = shrinkVertically(DripMotion.sizeSpring()) + fadeOut(DripMotion.fadeSpec()),
    ) {
        Column {
            Spacer(Modifier.height(12.dp))
            PermissionCard(
                icon, title, why,
                granted = grant.level != PermissionLevel.Off,
                partly = grant.level == PermissionLevel.Partly,
                onGrant = grant.request,
                note = note,
            )
        }
    }
}

/** Precise location for the location dot, under the switch of the privacy dots. */
@Composable
internal fun LocationPermissionRow(island: IslandSettings) {
    val context = LocalContext.current
    val grant = rememberRuntimeGrant(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) {
        when {
            Access.preciseLocationAllowed(it) -> PermissionLevel.On
            Access.approximateLocationAllowed(it) -> PermissionLevel.Partly
            else -> PermissionLevel.Off
        }
    }
    val note = if (grant.level == PermissionLevel.Partly) {
        PermissionNote(
            stringResource(R.string.set2_perm_location_partly),
            listOf(PermissionNoteAction(stringResource(R.string.set2_perm_settings), { Access.openAppInfo(context) })),
        )
    } else {
        null
    }
    PermissionSlot(
        island.showPrivacy && grant.level != PermissionLevel.On, DripIcons.Location,
        stringResource(R.string.set2_perm_location), stringResource(R.string.set2_perm_location_why), grant, note,
    )
}

/** Nearby devices, for the battery in the announcement of Bluetooth headphones. */
@Composable
internal fun BluetoothPermissionRow(island: IslandSettings) {
    val grant = rememberRuntimeGrant(arrayOf(Manifest.permission.BLUETOOTH_CONNECT)) { allOrNothing(Access.bluetoothAllowed(it)) }
    PermissionSlot(
        island.alertHeadphones && grant.level != PermissionLevel.On, DripIcons.BluetoothConnected,
        stringResource(R.string.set2_perm_bluetooth), stringResource(R.string.set2_perm_bluetooth_why), grant,
    )
}

/** The second round on the page of what the island shows: two at once, lyrics, appointments, installs and the rest. */
@Composable
internal fun MoreShows(island: IslandSettings, edit: IslandEdit) {
    val context = LocalContext.current
    val access = rememberAccess()
    val needs = stringResource(R.string.needs_listener)
    val calendar = rememberRuntimeGrant(arrayOf(Manifest.permission.READ_CALENDAR)) { allOrNothing(Access.calendarAllowed(it)) }

    SectionHeader(stringResource(R.string.set2_section_more), top = 20.dp)
    SettingsGroup {
        SettingsTarget(FocusKeys.ShowSecond, 0, 3) {
            SwitchRow(
                0, 3, DripIcons.Tab, stringResource(R.string.set2_second), stringResource(R.string.set2_second_sub),
                island.showSecond, { on -> edit { it.copy(showSecond = on) } },
            )
        }
        SettingsTarget(FocusKeys.ShowLyrics, 1, 3) {
            SwitchRow(
                1, 3, DripIcons.Lyrics, stringResource(R.string.set2_lyrics), stringResource(R.string.set2_lyrics_sub),
                island.showLyrics, { on -> edit { it.copy(showLyrics = on) } },
                blocked = if (!access.listener) needs else null,
                onBlocked = { Access.openListener(context) },
            )
        }
        // Off unless asked for: it is the one switch on this page that sends something off the phone.
        SettingsTarget(FocusKeys.LyricsOnline, 2, 3) {
            SwitchRow(
                2, 3, DripIcons.Language, stringResource(R.string.set2_lyrics_online), stringResource(R.string.set2_lyrics_online_sub),
                island.lyricsOnline, { on -> edit { it.copy(lyricsOnline = on) } },
                enabled = island.showLyrics && access.listener,
            )
        }
    }

    Spacer(Modifier.height(12.dp))
    SettingsGroup {
        SettingsTarget(FocusKeys.ShowCalendar, 0, 2) {
            SwitchRow(
                0, 2, DripIcons.Calendar, stringResource(R.string.set2_calendar), stringResource(R.string.set2_calendar_sub),
                island.showCalendar, { on -> edit { it.copy(showCalendar = on) } },
            )
        }
        SettingsTarget(FocusKeys.CalendarLead, 1, 2) {
            Box(Modifier.alpha(if (island.showCalendar) 1f else 0.6f)) {
                SegmentedRow(
                    1, 2, DripIcons.Hourglass, stringResource(R.string.set2_calendar_lead), LeadMinutes,
                    selected = LeadMinutes.minBy { kotlin.math.abs(it - island.calendarLeadMinutes) },
                    label = { stringResource(R.string.set2_minutes, it) },
                    onSelect = { m -> edit { it.copy(calendarLeadMinutes = m) } },
                )
            }
        }
    }
    PermissionSlot(
        island.showCalendar && calendar.level != PermissionLevel.On, DripIcons.Calendar,
        stringResource(R.string.set2_perm_calendar), stringResource(R.string.set2_perm_calendar_why), calendar,
    )

    Spacer(Modifier.height(12.dp))
    SettingsGroup {
        SettingsTarget(FocusKeys.ShowInstalls, 0, 3) {
            SwitchRow(
                0, 3, DripIcons.InstallMobile, stringResource(R.string.set2_installs), stringResource(R.string.set2_installs_sub),
                island.showInstalls, { on -> edit { it.copy(showInstalls = on) } },
            )
        }
        SettingsTarget(FocusKeys.ShowUnlock, 1, 3) {
            SwitchRow(
                1, 3, DripIcons.LockOpen, stringResource(R.string.set2_unlock), stringResource(R.string.set2_unlock_sub),
                island.showUnlock, { on -> edit { it.copy(showUnlock = on) } },
            )
        }
        SettingsTarget(FocusKeys.ShowTandem, 2, 3) {
            SwitchRow(
                2, 3, DripIcons.Devices, stringResource(R.string.set2_tandem), stringResource(R.string.set2_tandem_sub),
                island.showTandem, { on -> edit { it.copy(showTandem = on) } },
            )
        }
    }
}

/** Other apps putting things on the island, and where it is written down how. */
@Composable
internal fun OtherAppsSection(island: IslandSettings, edit: IslandEdit) {
    val context = LocalContext.current
    SectionHeader(stringResource(R.string.set2_section_other_apps), top = 20.dp)
    SettingsGroup {
        SettingsTarget(FocusKeys.AllowAutomation, 0, 2) {
            SwitchRow(
                0, 2, DripIcons.Hub, stringResource(R.string.set2_automation), stringResource(R.string.set2_automation_sub),
                island.allowAutomation, { on -> edit { it.copy(allowAutomation = on) } },
            )
        }
        ActionRow(
            1, 2, DripIcons.OpenInNew, stringResource(R.string.set2_automation_docs), stringResource(R.string.set2_automation_docs_sub),
            {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AutomationDocsUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
            modifier = Modifier.settingsTarget(FocusKeys.AutomationDocs, 1, 2),
        )
    }
}
