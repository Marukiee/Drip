package nl.markmaaktmedia.drip.ui.screens.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
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
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.launch
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.data.AccentSource
import nl.markmaaktmedia.drip.data.CardStyle
import nl.markmaaktmedia.drip.graph
import nl.markmaaktmedia.drip.ui.Route
import nl.markmaaktmedia.drip.ui.components.ActionRow
import nl.markmaaktmedia.drip.ui.components.DripConfirmDialog
import nl.markmaaktmedia.drip.ui.components.SectionHeader
import nl.markmaaktmedia.drip.ui.components.SettingsGroup
import nl.markmaaktmedia.drip.ui.components.SwitchRow
import nl.markmaaktmedia.drip.ui.routeBounds
import nl.markmaaktmedia.drip.ui.routeKey
import nl.markmaaktmedia.drip.ui.theme.DripIcons
import nl.markmaaktmedia.drip.ui.theme.LocalAppearance
import nl.markmaaktmedia.drip.ui.theme.ThemeMode

/** The language of the app as stored: "system" for no choice, else a language tag like "nl". */
internal fun appLanguage(): String =
    AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore(',').substringBefore('-').ifBlank { "system" }

/**
 * Switches the language. AppCompat keeps it (and Android itself from version 13), and the settings keep a copy too, so
 * a backup carries it to the next phone.
 */
internal fun setAppLanguage(context: Context, language: String) {
    context.graph.scope.launch { context.graph.prefs.setLanguage(language.takeIf { it != "system" }) }
    AppCompatDelegate.setApplicationLocales(if (language == "system") LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(language))
}

internal val Languages = listOf("system", "en", "nl")

/** Look and language: the colours of the island, the theme of the app, and its language. */
@Composable
internal fun LookPage(onBack: () -> Unit, onOpen: (Route) -> Unit) {
    val context = LocalContext.current
    var pendingLanguage by remember { mutableStateOf<String?>(null) }
    val current = appLanguage()

    val prefs = context.graph.prefs
    val scope = rememberCoroutineScope()
    val appearance = LocalAppearance.current
    val (island, edit) = rememberIsland()

    SettingsPageFrame(stringResource(R.string.settings_look), onBack) {
        SectionHeader(stringResource(R.string.look_island), top = 20.dp)
        SettingsGroup {
            SettingsTarget(FocusKeys.LookCard, 0, 2) {
                SegmentedRow(
                    0, 2, DripIcons.Palette, stringResource(R.string.look_card),
                    CardStyle.entries, island.cardStyle,
                    { stringResource(if (it == CardStyle.BLACK) R.string.card_black else R.string.card_tinted) },
                ) { style -> edit { it.copy(cardStyle = style) } }
            }
            SettingsTarget(FocusKeys.LookAccent, 1, 2) {
                SegmentedRow(
                    1, 2, DripIcons.Music, stringResource(R.string.look_accent),
                    AccentSource.entries, island.accentSource,
                    { stringResource(if (it == AccentSource.ALBUM_ART) R.string.accent_album else R.string.accent_theme) },
                ) { source -> edit { it.copy(accentSource = source) } }
            }
        }

        // Light, dark and pure black are what people change most, so they are here and not a page further.
        SectionHeader(stringResource(R.string.appearance_theme), top = 20.dp)
        SettingsGroup {
            SettingsTarget(FocusKeys.AppearanceMode, 0, 2) {
                SegmentedRow(
                    0, 2, DripIcons.DarkMode, stringResource(R.string.appearance_mode),
                    ThemeMode.entries, appearance.mode,
                    {
                        stringResource(
                            when (it) {
                                ThemeMode.SYSTEM -> R.string.language_system
                                ThemeMode.LIGHT -> R.string.appearance_light
                                ThemeMode.DARK -> R.string.appearance_dark
                            },
                        )
                    },
                ) { mode -> scope.launch { prefs.setAppearance(appearance.copy(mode = mode)) } }
            }
            SettingsTarget(FocusKeys.AppearanceBlack, 1, 2) {
                SwitchRow(
                    1, 2, DripIcons.DarkMode, stringResource(R.string.appearance_black), stringResource(R.string.appearance_black_sub),
                    appearance.pureBlack, { on -> scope.launch { prefs.setAppearance(appearance.copy(pureBlack = on)) } },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        SettingsGroup {
            ActionRow(
                0, 2, DripIcons.Palette, stringResource(R.string.settings_appearance), stringResource(R.string.settings_appearance_sub),
                { onOpen(Route.Appearance) }, modifier = Modifier.routeBounds(routeKey(Route.Appearance)),
            )
            SettingsTarget(FocusKeys.Language, 1, 2) {
                SegmentedRow(
                    1, 2, DripIcons.Language, stringResource(R.string.settings_language),
                    Languages, if (current in Languages) current else "system",
                    {
                        stringResource(
                            when (it) {
                                "en" -> R.string.language_en
                                "nl" -> R.string.language_nl
                                else -> R.string.language_system
                            },
                        )
                    },
                    // Switching language recreates the app, so ask before doing it.
                ) { if (it != current) pendingLanguage = it }
            }
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
