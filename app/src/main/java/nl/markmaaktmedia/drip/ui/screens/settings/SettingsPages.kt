package nl.markmaaktmedia.drip.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.ui.Route
import nl.markmaaktmedia.drip.ui.components.DripIconButton
import nl.markmaaktmedia.drip.ui.theme.DripIcons

/** Opens the page for one of the categories of the overview. */
@Composable
fun SettingsPageScreen(page: SettingsPageId, onBack: () -> Unit, onOpen: (Route) -> Unit) {
    when (page) {
        SettingsPageId.Shape -> ShapePage(onBack)
        SettingsPageId.Motion -> MotionPage(onBack)
        SettingsPageId.Shows -> ShowsPage(onBack)
        SettingsPageId.Notifications -> NotificationsPage(onBack, onOpen)
        SettingsPageId.Gestures -> GesturesPage(onBack)
        SettingsPageId.Hide -> HidePage(onBack, onOpen)
        SettingsPageId.Look -> LookPage(onBack, onOpen)
        SettingsPageId.Keep -> KeepOnPage(onBack)
        SettingsPageId.Updates -> UpdatesPage(onBack, onOpen)
        SettingsPageId.About -> AboutPage(onBack, onOpen)
    }
}

/**
 * The frame every Settings page shares: a back button, the title, then the groups. It scrolls as one column so that a
 * search result can always find its row, which a lazy list would not have composed yet.
 */
@Composable
internal fun SettingsPageFrame(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            DripIconButton(DripIcons.Back, stringResource(R.string.action_back), onBack)
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 8.dp))
        content()
        Spacer(Modifier.height(24.dp))
    }
}
