package nl.markmaaktmedia.drip.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.markmaaktmedia.drip.BuildConfig
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.ui.routeBounds
import nl.markmaaktmedia.drip.graph
import nl.markmaaktmedia.drip.ui.components.ActionRow
import nl.markmaaktmedia.drip.ui.components.InfoRow
import nl.markmaaktmedia.drip.ui.components.SectionHeader
import nl.markmaaktmedia.drip.ui.components.SettingsGroup
import nl.markmaaktmedia.drip.ui.components.SwitchRow
import nl.markmaaktmedia.drip.ui.components.DripConfirmDialog
import nl.markmaaktmedia.drip.ui.theme.DripIcons
import nl.markmaaktmedia.drip.update.UpdateState

/** Updates and backup: the version, checking for a new one, what is new, and saving or restoring the settings in a file. */
@Composable
internal fun UpdatesPage(onBack: () -> Unit, onOpen: (nl.markmaaktmedia.drip.ui.Route) -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val prefs = graph.prefs
    val scope = rememberCoroutineScope()

    val autoUpdate by prefs.autoUpdateCheck.collectAsState(initial = true)
    val updateState by graph.updater.state.collectAsState()
    var pendingLanguage by remember { mutableStateOf<String?>(null) }
    val currentLanguage = appLanguage()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = prefs.exportJson()
            val saved = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) } }.isSuccess
            }
            Toast.makeText(context, if (saved) R.string.backup_saved else R.string.backup_failed, Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }.getOrNull()
            }
            val restored = text?.let { prefs.importJson(it) }
            when {
                text == null -> Toast.makeText(context, R.string.backup_failed, Toast.LENGTH_SHORT).show()
                restored != true -> Toast.makeText(context, R.string.backup_invalid, Toast.LENGTH_SHORT).show()
                else -> {
                    Toast.makeText(context, R.string.backup_restored, Toast.LENGTH_SHORT).show()
                    // The backup carries the language too; it only takes effect with a restart, so ask first.
                    val language = prefs.language.first() ?: "system"
                    if (language != currentLanguage) pendingLanguage = language
                }
            }
        }
    }

    SettingsPageFrame(stringResource(R.string.settings_cat_updates), onBack) {
        SectionHeader(stringResource(R.string.settings_updates))
        SettingsGroup {
            SettingsTarget(FocusKeys.Version, 0, 4) {
                InfoRow(0, 4, DripIcons.Info, stringResource(R.string.settings_version), BuildConfig.VERSION_NAME)
            }
            SettingsTarget(FocusKeys.AutoUpdate, 1, 4) {
                SwitchRow(1, 4, DripIcons.Update, stringResource(R.string.settings_auto_update), stringResource(R.string.settings_auto_update_sub), autoUpdate, { scope.launch { prefs.setAutoUpdateCheck(it) } })
            }
            ActionRow(
                2, 4, DripIcons.Refresh, stringResource(R.string.settings_check_update),
                when (val state = updateState) {
                    is UpdateState.Checking -> stringResource(R.string.settings_checking)
                    is UpdateState.UpToDate -> stringResource(R.string.settings_up_to_date)
                    is UpdateState.Available -> stringResource(R.string.update_available, state.release.versionName)
                    is UpdateState.Failed -> state.reason
                    is UpdateState.Downloading -> stringResource(R.string.update_downloading, (state.progress * 100).toInt())
                    is UpdateState.NeedsPermission -> stringResource(R.string.update_needs_permission)
                    else -> null
                },
                { scope.launch { graph.updater.check() } },
                modifier = Modifier.settingsTarget(FocusKeys.CheckUpdate, 2, 4),
            )
            // What is new in each version lives with the update, not under About.
            ActionRow(
                3, 4, DripIcons.Update, stringResource(R.string.changelog_title), stringResource(R.string.changelog_sub),
                { onOpen(nl.markmaaktmedia.drip.ui.Route.Changelog) },
                modifier = Modifier.routeBounds(nl.markmaaktmedia.drip.ui.routeKey(nl.markmaaktmedia.drip.ui.Route.Changelog)),
            )
        }

        SectionHeader(stringResource(R.string.settings_backup))
        SettingsGroup {
            ActionRow(
                0, 2, DripIcons.Upload, stringResource(R.string.backup_export), stringResource(R.string.backup_export_sub),
                { exportLauncher.launch("drip-settings.json") }, modifier = Modifier.settingsTarget(FocusKeys.BackupExport, 0, 2),
            )
            ActionRow(
                1, 2, DripIcons.Download, stringResource(R.string.backup_import), stringResource(R.string.backup_import_sub),
                { importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.settingsTarget(FocusKeys.BackupImport, 1, 2),
            )
        }
    }

    pendingLanguage?.let { language ->
        DripConfirmDialog(
            title = stringResource(R.string.language_restart_title),
            body = stringResource(R.string.language_restart_body),
            confirmLabel = stringResource(R.string.language_restart_confirm),
            cancelLabel = stringResource(R.string.language_restart_cancel),
            destructive = false,
            onConfirm = {
                pendingLanguage = null
                setAppLanguage(context, language)
            },
            onDismiss = { pendingLanguage = null },
        )
    }
}
