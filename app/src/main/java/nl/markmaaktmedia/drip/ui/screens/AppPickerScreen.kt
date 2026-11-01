package nl.markmaaktmedia.drip.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.engine.LauncherApp
import nl.markmaaktmedia.drip.engine.LauncherApps
import nl.markmaaktmedia.drip.ui.AppPickerKind
import nl.markmaaktmedia.drip.ui.components.DripIconButton
import nl.markmaaktmedia.drip.ui.components.EmptyState
import nl.markmaaktmedia.drip.ui.components.GroupedRow
import nl.markmaaktmedia.drip.ui.components.PillSpinner
import nl.markmaaktmedia.drip.ui.screens.settings.SettingsSearchField
import nl.markmaaktmedia.drip.ui.screens.settings.rememberIsland
import nl.markmaaktmedia.drip.ui.theme.DripIcons

/**
 * The apps on the home screen with a switch each: the apps whose notifications may open the island, or the apps the
 * island steps aside in. The chosen ones are at the top when the page opens, so they can be found again; they stay
 * where they are while switches are flipped, because a list that reorders under the thumb loses its place.
 */
@Composable
fun AppPickerScreen(kind: AppPickerKind, onBack: () -> Unit) {
    val context = LocalContext.current
    val (island, edit) = rememberIsland()
    val chosen = when (kind) {
        AppPickerKind.NOTIFY -> island.notifyApps
        AppPickerKind.HIDE -> island.hiddenApps
    }
    val initialChosen = remember { chosen }
    val apps by produceState<List<LauncherApp>?>(null) {
        value = withContext(Dispatchers.IO) { LauncherApps.chosenFirst(LauncherApps.load(context), initialChosen) }
    }
    var query by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val shown = remember(apps, query) { apps?.let { LauncherApps.filter(it, query) } }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val fieldFocus = remember { FocusRequester() }

    fun toggle(pkg: String, on: Boolean) = edit { settings ->
        when (kind) {
            AppPickerKind.NOTIFY -> settings.copy(notifyApps = if (on) settings.notifyApps + pkg else settings.notifyApps - pkg)
            AppPickerKind.HIDE -> settings.copy(hiddenApps = if (on) settings.hiddenApps + pkg else settings.hiddenApps - pkg)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            DripIconButton(DripIcons.Back, stringResource(R.string.action_back), onBack)
        }
        Text(
            stringResource(if (kind == AppPickerKind.NOTIFY) R.string.picker_notify_title else R.string.picker_hide_title),
            style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 24.dp),
        )
        SettingsSearchField(
            query = query,
            onQueryChange = { query = it },
            active = focused || query.isNotEmpty(),
            onFocusChange = { focused = it },
            onBack = {
                query = ""
                keyboard?.hide()
                focusManager.clearFocus()
            },
            onSearch = { keyboard?.hide(); focusManager.clearFocus() },
            focusRequester = fieldFocus,
            hint = stringResource(R.string.picker_search),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
        )

        val list = shown
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { PillSpinner() }
            list.isEmpty() -> EmptyState(
                title = stringResource(R.string.picker_none, query.trim()), body = "", icon = DripIcons.Search,
            )
            else -> LazyColumn(
                Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(list, key = { _, app -> app.packageName }) { index, app ->
                    AppRow(index, list.size, app, app.packageName in chosen, { on -> toggle(app.packageName, on) }, Modifier.animateItem())
                }
            }
        }
    }
}

@Composable
private fun AppRow(index: Int, total: Int, app: LauncherApp, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier) {
    GroupedRow(index, total, modifier = modifier, onClick = { onChange(!checked) }) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppIcon(app.packageName)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(app.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

/** The app's own icon, loaded off the main thread the first time its row shows. */
@Composable
private fun AppIcon(packageName: String) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
    }
    Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        icon?.let { Image(it, null, Modifier.size(40.dp)) }
    }
}
