package nl.markmaaktmedia.drip.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.markmaaktmedia.drip.BuildConfig
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.graph
import nl.markmaaktmedia.drip.ui.Route
import nl.markmaaktmedia.drip.ui.components.ActionRow
import nl.markmaaktmedia.drip.ui.components.DripConfirmDialog
import nl.markmaaktmedia.drip.ui.components.InfoRow
import nl.markmaaktmedia.drip.ui.components.NoteRow
import nl.markmaaktmedia.drip.ui.components.SectionHeader
import nl.markmaaktmedia.drip.ui.components.SettingsGroup
import nl.markmaaktmedia.drip.ui.routeBounds
import nl.markmaaktmedia.drip.ui.routeKey
import nl.markmaaktmedia.drip.ui.theme.DripIcons

private const val RepositoryUrl = "https://github.com/Marukiee/Drip"

/** About: the version, where the code is, what happens to your data, and starting the island over. */
@Composable
internal fun AboutPage(onBack: () -> Unit, onOpen: (Route) -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val scope = rememberCoroutineScope()
    var confirmReset by remember { mutableStateOf(false) }

    SettingsPageFrame(stringResource(R.string.settings_cat_about), onBack) {
        SectionHeader(stringResource(R.string.settings_about), top = 20.dp)
        SettingsGroup {
            InfoRow(0, 3, DripIcons.Info, stringResource(R.string.settings_version), BuildConfig.VERSION_NAME)
            ActionRow(
                1, 3, DripIcons.OpenInNew, stringResource(R.string.about_github), stringResource(R.string.about_github_sub),
                { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RepositoryUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                modifier = Modifier.settingsTarget(FocusKeys.Source, 1, 3),
            )
            SettingsTarget(FocusKeys.Privacy, 2, 3) {
                NoteRow(2, 3, DripIcons.Shield, stringResource(R.string.about_privacy), stringResource(R.string.about_privacy_body))
            }
        }

        Spacer(Modifier.height(12.dp))
        SettingsGroup {
            ActionRow(
                0, 1, DripIcons.Home, stringResource(R.string.about_tour), stringResource(R.string.about_tour_sub),
                { onOpen(Route.OnboardingPreview) }, modifier = Modifier.routeBounds(routeKey(Route.OnboardingPreview)),
            )
        }

        // Alone at the bottom: the one thing on these pages that cannot be undone.
        Spacer(Modifier.height(24.dp))
        SettingsGroup {
            ActionRow(
                0, 1, DripIcons.Restart, stringResource(R.string.about_reset), stringResource(R.string.about_reset_sub),
                { confirmReset = true }, danger = true, modifier = Modifier.settingsTarget(FocusKeys.Reset, 0, 1),
            )
        }
    }

    if (confirmReset) {
        DripConfirmDialog(
            title = stringResource(R.string.about_reset),
            body = stringResource(R.string.about_reset_body),
            confirmLabel = stringResource(R.string.about_reset_confirm),
            cancelLabel = stringResource(R.string.action_cancel),
            destructive = true,
            onConfirm = { scope.launch { graph.prefs.resetIsland() } },
            onDismiss = { confirmReset = false },
        )
    }
}
