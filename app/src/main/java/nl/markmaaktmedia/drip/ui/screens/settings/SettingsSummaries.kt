package nl.markmaaktmedia.drip.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import nl.markmaaktmedia.drip.BuildConfig
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.data.CardStyle
import nl.markmaaktmedia.drip.data.FitMode
import nl.markmaaktmedia.drip.data.IslandSettings
import nl.markmaaktmedia.drip.engine.IslandKeeper
import nl.markmaaktmedia.drip.graph
import nl.markmaaktmedia.drip.ui.screens.rememberAccess
import nl.markmaaktmedia.drip.ui.screens.seedLabel
import nl.markmaaktmedia.drip.ui.theme.LocalAppearance
import nl.markmaaktmedia.drip.ui.theme.ThemeMode
import nl.markmaaktmedia.drip.update.UpdateState
import kotlin.math.roundToInt

/**
 * The line under each category on the overview, read live from where the settings are kept. Every line says
 * something a person can act on without opening the page: what is chosen, how much is on, or what is missing.
 */
@Composable
internal fun rememberCategorySummaries(): Map<SettingsCategory, String> {
    val context = LocalContext.current
    val graph = context.graph
    val island by graph.prefs.island.collectAsState(initial = IslandSettings())
    val access = rememberAccess()

    // Appearance and language.
    val appearance = LocalAppearance.current
    val locale = LocalConfiguration.current.locales[0]
    val languageName = when (locale.language) {
        "nl" -> stringResource(R.string.language_nl)
        "en" -> stringResource(R.string.language_en)
        else -> locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
    }
    // Following the system is the default and says nothing, so only a chosen theme is named.
    val mode = when (appearance.mode) {
        ThemeMode.LIGHT -> stringResource(R.string.appearance_light)
        ThemeMode.DARK -> stringResource(R.string.appearance_dark)
        ThemeMode.SYSTEM -> null
    }
    val card = stringResource(if (island.cardStyle == CardStyle.BLACK) R.string.card_black else R.string.card_tinted)
    val look = listOfNotNull(card, mode, stringResource(seedLabel(appearance.seed)), languageName).joinToString(", ")

    val defaults = IslandSettings()
    val handMade = listOf(
        island.idleWidthDp, island.idleHeightDp, island.compactWidthDp, island.expandedTopRadiusDp, island.buttonOuterRadiusDp,
    ).any { it != null } || island.offsetXDp != defaults.offsetXDp || island.offsetYDp != defaults.offsetYDp
    val fit = stringResource(fitLabel(island.fitMode))
    val shape = "$fit, " + stringResource(if (handMade) R.string.settings_sum_sizes_custom else R.string.settings_sum_sizes_auto)

    val motion = stringResource(
        R.string.settings_sum_motion,
        stringResource(R.string.value_percent, (island.bounce * 200).roundToInt()),
        stringResource(R.string.value_speed, speedText(island.speed)),
    ) + if (island.hapticsEnabled) "" else ", " + stringResource(R.string.settings_sum_haptics_off)

    val shown = listOf(
        island.showMusic, island.showCharging, island.showCalls, island.showLive, island.showTorch,
        island.showNotifications, island.showPrivacy, island.showSecond, island.showLyrics, island.showCalendar,
        island.showInstalls, island.showUnlock, island.showTandem,
    )

    val notifications = when {
        !access.listener -> stringResource(R.string.settings_sum_needs_listener)
        !island.showNotifications -> stringResource(R.string.settings_sum_all_off)
        else -> notifyAppsSummary(island)
    } + ", " + stringResource(R.string.set2_sum_duration, secondsText(island.alertDurationMs))

    val gestures = listOf(
        stringResource(R.string.gesture_tap) + ": " + stringResource(gestureLabel(island.gestureTap)).lowercase(locale),
        stringResource(R.string.gesture_hold).lowercase(locale) + ": " + stringResource(gestureLabel(island.gestureLongPress)).lowercase(locale),
    ).joinToString(", ")

    val hideSwitches = listOf(island.hideFullscreen, island.hideLandscape, island.hideOnLockscreen, island.hideInPlayer)
    val hide = when {
        hideSwitches.none { it } && island.hiddenApps.isEmpty() -> stringResource(R.string.settings_sum_never)
        island.hiddenApps.isEmpty() -> summaryOnOf(hideSwitches.count { it }, hideSwitches.size)
        else -> summaryOnOf(hideSwitches.count { it }, hideSwitches.size) + ", " +
            pluralStringResource(R.plurals.apps_count, island.hiddenApps.size, island.hiddenApps.size)
    }

    val keep = stringResource(if (access.keeper) R.string.settings_sum_keep_on else R.string.settings_sum_keep_off)

    val updateState by graph.updater.state.collectAsState()
    val version = BuildConfig.VERSION_NAME

    return mapOf(
        SettingsCategory.Shape to shape,
        SettingsCategory.Motion to motion,
        SettingsCategory.Look to look,
        SettingsCategory.Shows to summaryOnOf(shown.count { it }, shown.size),
        SettingsCategory.Notifications to notifications,
        SettingsCategory.Gestures to gestures,
        SettingsCategory.Hide to hide,
        SettingsCategory.Keep to keep,
        SettingsCategory.Updates to when (val state = updateState) {
            is UpdateState.Available -> stringResource(R.string.update_available, state.release.versionName)
            is UpdateState.UpToDate -> stringResource(R.string.settings_sum_up_to_date, version)
            else -> stringResource(R.string.settings_sum_version, version)
        },
        SettingsCategory.About to stringResource(R.string.about_privacy),
    )
}

@Composable
private fun summaryOnOf(done: Int, of: Int): String = when (done) {
    of -> stringResource(R.string.settings_sum_all_on)
    0 -> stringResource(R.string.settings_sum_all_off)
    else -> stringResource(R.string.settings_sum_on_of, done, of)
}

internal fun fitLabel(mode: FitMode): Int = when (mode) {
    FitMode.AUTO -> R.string.fit_auto
    FitMode.NOTCH -> R.string.fit_notch
    FitMode.CUTOUT -> R.string.fit_cutout
}

/** "1", "1.5", "0.75": no trailing zeros, so the speed reads like a multiplier. */
internal fun speedText(speed: Float): String {
    val rounded = (speed * 100).roundToInt() / 100.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString().trimEnd('0')
}
