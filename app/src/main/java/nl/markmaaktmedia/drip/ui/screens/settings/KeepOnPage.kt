package nl.markmaaktmedia.drip.ui.screens.settings

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.engine.IslandKeeper
import nl.markmaaktmedia.drip.engine.ShizukuGrant
import nl.markmaaktmedia.drip.engine.ShizukuState
import nl.markmaaktmedia.drip.ui.components.ActionRow
import nl.markmaaktmedia.drip.ui.components.GroupedRow
import nl.markmaaktmedia.drip.ui.components.RowIcon
import nl.markmaaktmedia.drip.ui.components.SecondaryPillButton
import nl.markmaaktmedia.drip.ui.components.SectionHeader
import nl.markmaaktmedia.drip.ui.components.SettingsGroup
import nl.markmaaktmedia.drip.ui.theme.DripIcons
import nl.markmaaktmedia.drip.ui.theme.LocalDripExtraColors
import nl.markmaaktmedia.drip.ui.theme.SquircleShape

/**
 * Keep the island on: why Android may switch it off, and the two ways to let Drip switch it back on by itself, Shizuku
 * on the phone or one command from a computer.
 */
@Composable
internal fun KeepOnPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by ShizukuGrant.state.collectAsState()
    var allowed by remember { mutableStateOf(IslandKeeper.canKeep(context)) }
    var problem by remember { mutableStateOf<String?>(null) }
    var granting by remember { mutableStateOf(false) }

    // Shizuku sends no word when it is started or allowed in its own app, and adb gives the permission from outside,
    // so the page looks again while it is open.
    LaunchedEffect(Unit) {
        while (true) {
            ShizukuGrant.register(context)
            ShizukuGrant.refresh()
            allowed = IslandKeeper.canKeep(context)
            delay(2_000)
        }
    }

    val ready = state == ShizukuState.Ready
    // The next step for Shizuku itself, from its own row and from the grant that waits for it.
    val nextStep: () -> Unit = {
        when (state) {
            ShizukuState.NeedsPermission -> ShizukuGrant.requestPermission()
            ShizukuState.NotRunning, ShizukuState.Denied, ShizukuState.Ready -> ShizukuGrant.launch(context)
            ShizukuState.NotInstalled, ShizukuState.TooOld -> context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
    val done: @Composable () -> Unit = {
        Icon(DripIcons.Check, null, tint = LocalDripExtraColors.current.online, modifier = Modifier.size(24.dp))
    }

    SettingsPageFrame(stringResource(R.string.settings_cat_keep), onBack) {
        Text(
            stringResource(R.string.keep_intro),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )

        SectionHeader(stringResource(R.string.keep_section_state), top = 20.dp)
        SettingsGroup {
            ActionRow(
                0, 1, DripIcons.Shield,
                stringResource(if (allowed) R.string.keep_state_on else R.string.keep_state_off),
                stringResource(if (allowed) R.string.keep_state_on_sub else R.string.keep_state_off_sub),
                onClick = { if (allowed) scope.launch { IslandKeeper.ensure(context) } },
                trailing = if (allowed) done else ({}),
            )
        }

        SectionHeader(stringResource(R.string.keep_section_shizuku), top = 20.dp)
        SettingsGroup {
            val (title, sub) = when (state) {
                ShizukuState.NotInstalled -> R.string.shizuku_not_installed to R.string.shizuku_not_installed_sub
                ShizukuState.NotRunning -> R.string.shizuku_not_running to R.string.shizuku_not_running_sub
                ShizukuState.NeedsPermission -> R.string.shizuku_needs_permission to R.string.shizuku_needs_permission_sub
                ShizukuState.Denied -> R.string.shizuku_denied to R.string.shizuku_denied_sub
                ShizukuState.TooOld -> R.string.shizuku_too_old to R.string.shizuku_too_old_sub
                ShizukuState.Ready -> R.string.shizuku_ready to R.string.shizuku_ready_sub
            }
            SettingsTarget(FocusKeys.KeepShizuku, 0, 2) {
                ActionRow(0, 2, DripIcons.Terminal, stringResource(title), stringResource(sub), onClick = nextStep, trailing = if (ready) done else null)
            }
            ActionRow(
                1, 2, DripIcons.Key, stringResource(R.string.keep_grant),
                when {
                    allowed -> stringResource(R.string.access_allowed)
                    problem != null -> stringResource(R.string.keep_grant_failed, problem!!)
                    !ready -> stringResource(R.string.keep_grant_wait)
                    else -> stringResource(R.string.keep_grant_sub)
                },
                onClick = {
                    when {
                        allowed -> Unit
                        !ready -> nextStep()
                        !granting -> scope.launch {
                            granting = true
                            problem = ShizukuGrant.grantSecureSettings(context)
                            allowed = IslandKeeper.canKeep(context)
                            if (allowed) IslandKeeper.ensure(context)
                            granting = false
                        }
                    }
                },
                trailing = if (allowed) done else null,
            )
        }

        SectionHeader(stringResource(R.string.keep_section_adb), top = 20.dp)
        SettingsTarget(FocusKeys.KeepAdb, 0, 1) { AdbCommand() }
    }
}

/** The one command for a computer, readable whole, with a button that copies it. */
@Composable
private fun AdbCommand() {
    val context = LocalContext.current
    // The package of this very build, so the debug build gets its own name and not the one of the release.
    val command = "adb shell pm grant ${context.packageName} ${Manifest.permission.WRITE_SECURE_SETTINGS}"
    GroupedRow(0, 1) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                RowIcon(DripIcons.Desktop)
                Text(
                    stringResource(R.string.keep_adb_body),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            SelectionContainer {
                Text(
                    command,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().clip(SquircleShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(12.dp),
                )
            }
            val copied = stringResource(R.string.keep_copied)
            SecondaryPillButton(
                stringResource(R.string.keep_adb_copy),
                {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("adb", command))
                    Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                },
                icon = DripIcons.Copy,
            )
        }
    }
}
