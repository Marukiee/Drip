package nl.markmaaktmedia.drip.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import nl.markmaaktmedia.drip.ui.theme.Appearance
import nl.markmaaktmedia.drip.ui.theme.ColourSeed
import nl.markmaaktmedia.drip.ui.theme.PaletteStyle
import nl.markmaaktmedia.drip.ui.theme.ThemeMode
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "drip")

/** What a tap, a hold or a swipe on the island does. */
enum class GestureAction {
    NONE, EXPAND, OPEN_APP, PLAY_PAUSE, NEXT_TRACK, PREVIOUS_TRACK, BACK, HOME, RECENTS,
    NOTIFICATIONS, QUICK_SETTINGS, LOCK_SCREEN, SCREENSHOT, TORCH, OPEN_DRIP, OUTPUT, DISMISS;

    companion object {
        fun from(value: String?, fallback: GestureAction) = entries.firstOrNull { it.name == value } ?: fallback
    }
}

/** What a set of gestures belongs to: the default, or one kind of thing on the island. */
enum class GestureScope { DEFAULT, MUSIC, CALL, TIMER, ROUTE, CHARGING, NOTICE }

/** The five gestures a scope can set, plus the double tap. */
enum class Gesture { TAP, DOUBLE_TAP, LONG_PRESS, SWIPE_DOWN, SWIPE_LEFT, SWIPE_RIGHT }

/** How the island sits on this phone. Auto picks notch without a camera hole and a pill around one with it. */
enum class FitMode { AUTO, NOTCH, CUTOUT }

/**
 * The closed island's sizes and place for one way of sitting: as a notch, or as a pill around the camera. Each way keeps
 * its own, so trying the other and coming back changes nothing.
 */
data class FitProfile(
    val idleWidthDp: Float? = null,
    val idleHeightDp: Float? = null,
    val compactWidthDp: Float? = null,
    val shoulderDp: Float = 10f,
    val collapsedRoundness: Float = 1f,
    val offsetXDp: Float = 0f,
    val offsetYDp: Float = 0f,
    val cutoutPaddingDp: Float = 3f,
)

/** Whether [mode] makes a notch on a phone that has a camera hole or not; Auto follows the phone. */
fun FitMode.isNotch(hasCutout: Boolean): Boolean = when (this) {
    FitMode.NOTCH -> true
    FitMode.CUTOUT -> false
    FitMode.AUTO -> !hasCutout
}

/**
 * Another fit. When it turns the island from a notch into a pill or back, what was set for the old way is put aside and
 * what was set for the new way, or how it came, takes its place.
 */
fun IslandSettings.withFit(mode: FitMode, hasCutout: Boolean): IslandSettings {
    if (fitMode.isNotch(hasCutout) == mode.isNotch(hasCutout)) return copy(fitMode = mode)
    val current = FitProfile(idleWidthDp, idleHeightDp, compactWidthDp, shoulderDp, collapsedRoundness, offsetXDp, offsetYDp, cutoutPaddingDp)
    val next = otherFit ?: FitProfile()
    return copy(
        fitMode = mode, otherFit = current,
        idleWidthDp = next.idleWidthDp, idleHeightDp = next.idleHeightDp, compactWidthDp = next.compactWidthDp,
        shoulderDp = next.shoulderDp, collapsedRoundness = next.collapsedRoundness,
        offsetXDp = next.offsetXDp, offsetYDp = next.offsetYDp, cutoutPaddingDp = next.cutoutPaddingDp,
    )
}

/** The colour of an opened island: always black, or a dark tint of the accent. */
enum class CardStyle { BLACK, TINTED }

/** Where the accent of the music card comes from. */
enum class AccentSource { ALBUM_ART, THEME }

/**
 * Every setting of the island in one value, so the overlay reads one flow and a settings screen writes with one call.
 *
 * Sizes are in dp. A null size means automatic: worked out from this phone's status bar, camera hole and screen corners,
 * which is what makes the island fit a Pixel and an Xperia without anyone measuring.
 */
data class IslandSettings(
    val enabled: Boolean = true,
    val fitMode: FitMode = FitMode.AUTO,
    /**
     * Shows the small notch or the ring around the camera while nothing is going on. Off by default: with nothing to
     * show, the island pulls back into the top edge (or the camera) and is gone.
     */
    val idleVisible: Boolean = false,
    val idleWidthDp: Float? = null,
    val idleHeightDp: Float? = null,
    /** Width of the island with something on both sides of it, like a song or a charger. */
    val compactWidthDp: Float? = null,
    /** The inward curve where a notch meets the top edge of the screen. */
    val shoulderDp: Float = 10f,
    /** Bottom corners of the closed island, as a share of half its height. 1 is fully round. */
    val collapsedRoundness: Float = 1f,
    /** Room between an opened island and the edges of the screen. */
    val expandedMarginDp: Float = 8f,
    /** Top corners of an opened island. Null follows the screen corners, so both curves run side by side. */
    val expandedTopRadiusDp: Float? = null,
    val expandedBottomRadiusDp: Float = 40f,
    /** Outer bottom corners of the buttons on the bottom row. Null keeps them concentric with the island. */
    val buttonOuterRadiusDp: Float? = null,
    /** The corners of a button that face another button or the content above. */
    val buttonInnerRadiusDp: Float = 10f,
    /** On a phone with a notch: an opened island stays on the top edge instead of floating free of it. */
    val expandedAttached: Boolean = false,
    /** Nudges for a camera hole the automatic fit does not quite hug. */
    val offsetXDp: Float = 0f,
    val offsetYDp: Float = 0f,
    /** Room around the camera hole in cutout mode. */
    val cutoutPaddingDp: Float = 3f,
    /** The closed island's sizes and place for the other way of sitting, kept while this one is in use. */
    val otherFit: FitProfile? = null,
    /** 0 is calm, 1 is lively. Sets how much the island overshoots when it opens. */
    val bounce: Float = 0.5f,
    /** 0.5 is half as fast, 2 is twice as fast. */
    val speed: Float = 1f,
    val hapticsEnabled: Boolean = true,
    val hapticsStrength: Float = 0.7f,
    val cardStyle: CardStyle = CardStyle.BLACK,
    val accentSource: AccentSource = AccentSource.ALBUM_ART,

    // What the island shows.
    val showMusic: Boolean = true,
    val showCharging: Boolean = true,
    /** Charging stays on the island for as long as the phone charges. Off it shows for a few seconds when plugged in. */
    val chargingStays: Boolean = false,
    val showCalls: Boolean = true,
    val showLive: Boolean = true,
    val showTorch: Boolean = true,
    val showNotifications: Boolean = true,
    val showPrivacy: Boolean = false,
    val alertBatteryLow: Boolean = true,
    val alertBatteryFull: Boolean = true,
    val alertBatterySaver: Boolean = true,
    val alertDnd: Boolean = true,
    val alertRotation: Boolean = true,
    val alertRinger: Boolean = true,
    val alertVolume: Boolean = true,
    val alertBrightness: Boolean = false,
    val alertHeadphones: Boolean = true,
    /** How long a short announcement stays, in milliseconds. */
    val alertDurationMs: Long = 3500,
    /** Notifications of every app but [notifyExcluded], or only of [notifyApps]. */
    val notifyAllApps: Boolean = true,
    val notifyApps: Set<String> = emptySet(),
    val notifyExcluded: Set<String> = emptySet(),
    /** Shows the text of a notification. Off keeps it to the app and the title. */
    val notifyShowText: Boolean = true,
    /** How long paused music stays on the island before it goes. */
    val pausedLingerMs: Long = 20_000L,
    /** Switches Android's own pop-ups off while the island runs, so a new message shows once, in the island. */
    val hideSystemPopups: Boolean = false,

    // Gestures on the island.
    val gestureTap: GestureAction = GestureAction.EXPAND,
    val gestureLongPress: GestureAction = GestureAction.OPEN_APP,
    /** Pulling down from the island opens the shade, as from anywhere else along the top. */
    val gestureSwipeDown: GestureAction = GestureAction.NOTIFICATIONS,
    val gestureSwipeLeft: GestureAction = GestureAction.NEXT_TRACK,
    val gestureSwipeRight: GestureAction = GestureAction.PREVIOUS_TRACK,
    val gestureDoubleTap: GestureAction = GestureAction.NONE,
    /**
     * Gestures set for one kind of thing, over the defaults above, written "SCOPE:GESTURE=ACTION", like
     * "TIMER:SWIPE_LEFT=DISMISS". Read through [actionFor].
     */
    val gestureOverrides: Set<String> = emptySet(),

    // When the island steps aside.
    val hideFullscreen: Boolean = true,
    val hideLandscape: Boolean = true,
    val hideOnLockscreen: Boolean = false,
    val hideInPlayer: Boolean = true,
    val hiddenApps: Set<String> = emptySet(),

    // The second round.
    /** A second thing at once gets its own small drop next to the island (or a bubble next to a pill). */
    val showSecond: Boolean = true,
    /** Synced lyrics on the music card when the player gives them. */
    val showLyrics: Boolean = true,
    /** Looks lyrics up at lrclib.net when the player has none. Sends the title and artist there, so off by default. */
    val lyricsOnline: Boolean = false,
    val showUnlock: Boolean = false,
    val showCalendar: Boolean = true,
    /** Minutes before an appointment that its countdown appears. */
    val calendarLeadMinutes: Int = 10,
    val showInstalls: Boolean = true,
    /** Lets other apps, like Tasker, put their own things on the island through an intent. */
    val allowAutomation: Boolean = false,
    /** Shows Tandem's transfers, clipboard and connected computer. */
    val showTandem: Boolean = true,
) {
    /** What [gesture] does on [scope]: an override for that kind of thing if there is one, else the default. */
    fun actionFor(scope: GestureScope, gesture: Gesture): GestureAction {
        if (scope != GestureScope.DEFAULT) {
            val prefix = "${scope.name}:${gesture.name}="
            gestureOverrides.firstOrNull { it.startsWith(prefix) }?.let { return GestureAction.from(it.removePrefix(prefix), GestureAction.NONE) }
        }
        // A message that comes in opens when pulled down, the notch becoming its card. Everywhere else that opens the shade.
        if (scope == GestureScope.NOTICE && gesture == Gesture.SWIPE_DOWN && gestureSwipeDown == GestureAction.NOTIFICATIONS) return GestureAction.EXPAND
        return when (gesture) {
            Gesture.TAP -> gestureTap
            Gesture.DOUBLE_TAP -> gestureDoubleTap
            Gesture.LONG_PRESS -> gestureLongPress
            Gesture.SWIPE_DOWN -> gestureSwipeDown
            Gesture.SWIPE_LEFT -> gestureSwipeLeft
            Gesture.SWIPE_RIGHT -> gestureSwipeRight
        }
    }

    /** These settings with [gesture] on [scope] set to [action], or back to the default when [action] is null. */
    fun withGesture(scope: GestureScope, gesture: Gesture, action: GestureAction?): IslandSettings {
        if (scope == GestureScope.DEFAULT) {
            val a = action ?: IslandSettings().actionFor(GestureScope.DEFAULT, gesture)
            return when (gesture) {
                Gesture.TAP -> copy(gestureTap = a)
                Gesture.DOUBLE_TAP -> copy(gestureDoubleTap = a)
                Gesture.LONG_PRESS -> copy(gestureLongPress = a)
                Gesture.SWIPE_DOWN -> copy(gestureSwipeDown = a)
                Gesture.SWIPE_LEFT -> copy(gestureSwipeLeft = a)
                Gesture.SWIPE_RIGHT -> copy(gestureSwipeRight = a)
            }
        }
        val prefix = "${scope.name}:${gesture.name}="
        val kept = gestureOverrides.filterNot { it.startsWith(prefix) }.toSet()
        return copy(gestureOverrides = if (action == null) kept else kept + (prefix + action.name))
    }
}

class DripPrefs(private val context: Context) {

    private val data: Flow<Preferences> = context.dataStore.data

    private object Keys {
        val onboarded = booleanPreferencesKey("onboarded")
        val autoUpdateCheck = booleanPreferencesKey("auto_update_check")
        val dismissedUpdate = stringPreferencesKey("dismissed_update")
        val lastUpdateCheck = longPreferencesKey("last_update_check")

        val themeMode = stringPreferencesKey("theme_mode")
        val colourSeed = stringPreferencesKey("colour_seed")
        val paletteStyle = stringPreferencesKey("palette_style")
        val pureBlack = booleanPreferencesKey("pure_black")
        val language = stringPreferencesKey("language")
    }

    /** Set by the app itself, not by a person, so a restored backup leaves them alone. */
    private val notBackedUp = setOf("onboarded", "last_update_check", "dismissed_update")

    val onboarded: Flow<Boolean> = data.map { it[Keys.onboarded] ?: false }
    suspend fun setOnboarded(value: Boolean) = set(Keys.onboarded, value)

    val autoUpdateCheck: Flow<Boolean> = data.map { it[Keys.autoUpdateCheck] ?: true }
    val dismissedUpdate: Flow<String?> = data.map { it[Keys.dismissedUpdate] }
    val lastUpdateCheck: Flow<Long> = data.map { it[Keys.lastUpdateCheck] ?: 0L }
    suspend fun setAutoUpdateCheck(value: Boolean) = set(Keys.autoUpdateCheck, value)
    suspend fun setLastUpdateCheck(value: Long) = set(Keys.lastUpdateCheck, value)
    suspend fun setDismissedUpdate(value: String) = set(Keys.dismissedUpdate, value)

    val appearance: Flow<Appearance> = data.map { prefs ->
        Appearance(
            mode = ThemeMode.entries.firstOrNull { it.name == prefs[Keys.themeMode] } ?: ThemeMode.SYSTEM,
            seed = ColourSeed.fromKey(prefs[Keys.colourSeed]),
            style = PaletteStyle.fromKey(prefs[Keys.paletteStyle]),
            pureBlack = prefs[Keys.pureBlack] ?: false,
        )
    }.distinctUntilChanged()

    suspend fun setAppearance(value: Appearance) {
        context.dataStore.edit {
            it[Keys.themeMode] = value.mode.name
            it[Keys.colourSeed] = value.seed.storageKey
            it[Keys.paletteStyle] = value.style.storageKey
            it[Keys.pureBlack] = value.pureBlack
        }
    }

    /** "nl", "en" or null for the language of the phone. */
    val language: Flow<String?> = data.map { it[Keys.language] }
    suspend fun setLanguage(value: String?) {
        context.dataStore.edit { if (value == null) it.remove(Keys.language) else it[Keys.language] = value }
    }

    val island: Flow<IslandSettings> = data.map { it.toIsland() }.distinctUntilChanged()

    /** Reads the settings, lets [change] return new ones, and writes only what differs. */
    suspend fun editIsland(change: (IslandSettings) -> IslandSettings) {
        context.dataStore.edit { prefs ->
            val before = prefs.toIsland()
            val after = change(before)
            if (after != before) prefs.writeIsland(after)
        }
    }

    /** Puts every island setting back to how it came. Theme and updates stay. */
    suspend fun resetIsland() = editIsland { IslandSettings() }

    /** The fit only: sizes, corners and nudges. For the reset button on the shape page. */
    suspend fun resetFit() = editIsland {
        val d = IslandSettings()
        it.copy(
            idleWidthDp = d.idleWidthDp, idleHeightDp = d.idleHeightDp, compactWidthDp = d.compactWidthDp,
            shoulderDp = d.shoulderDp, collapsedRoundness = d.collapsedRoundness, expandedMarginDp = d.expandedMarginDp,
            expandedTopRadiusDp = d.expandedTopRadiusDp, expandedBottomRadiusDp = d.expandedBottomRadiusDp,
            buttonOuterRadiusDp = d.buttonOuterRadiusDp, buttonInnerRadiusDp = d.buttonInnerRadiusDp,
            offsetXDp = d.offsetXDp, offsetYDp = d.offsetYDp, cutoutPaddingDp = d.cutoutPaddingDp,
        )
    }

    private fun Preferences.toIsland(): IslandSettings {
        val d = IslandSettings()
        fun b(name: String, fallback: Boolean) = this[booleanPreferencesKey(name)] ?: fallback
        fun f(name: String, fallback: Float) = this[floatPreferencesKey(name)] ?: fallback
        fun fn(name: String) = this[floatPreferencesKey(name)]
        fun s(name: String) = this[stringPreferencesKey(name)]
        fun set(name: String) = this[stringSetPreferencesKey(name)] ?: emptySet()
        return IslandSettings(
            enabled = b("island_enabled", d.enabled),
            fitMode = FitMode.entries.firstOrNull { it.name == s("fit_mode") } ?: d.fitMode,
            idleVisible = b("idle_visible", d.idleVisible),
            idleWidthDp = fn("idle_width"),
            idleHeightDp = fn("idle_height"),
            compactWidthDp = fn("compact_width"),
            shoulderDp = f("shoulder", d.shoulderDp),
            collapsedRoundness = f("collapsed_roundness", d.collapsedRoundness),
            expandedMarginDp = f("expanded_margin", d.expandedMarginDp),
            expandedTopRadiusDp = fn("expanded_top_radius"),
            expandedBottomRadiusDp = f("expanded_bottom_radius", d.expandedBottomRadiusDp),
            buttonOuterRadiusDp = fn("button_outer_radius"),
            buttonInnerRadiusDp = f("button_inner_radius", d.buttonInnerRadiusDp),
            expandedAttached = b("expanded_attached", d.expandedAttached),
            offsetXDp = f("offset_x", d.offsetXDp),
            offsetYDp = f("offset_y", d.offsetYDp),
            cutoutPaddingDp = f("cutout_padding", d.cutoutPaddingDp),
            otherFit = if (b("other_fit", false)) {
                FitProfile(
                    fn("other_idle_width"), fn("other_idle_height"), fn("other_compact_width"),
                    f("other_shoulder", d.shoulderDp), f("other_roundness", d.collapsedRoundness),
                    f("other_offset_x", d.offsetXDp), f("other_offset_y", d.offsetYDp), f("other_cutout_padding", d.cutoutPaddingDp),
                )
            } else {
                null
            },
            bounce = f("bounce", d.bounce),
            speed = f("speed", d.speed),
            hapticsEnabled = b("haptics", d.hapticsEnabled),
            hapticsStrength = f("haptics_strength", d.hapticsStrength),
            cardStyle = CardStyle.entries.firstOrNull { it.name == s("card_style") } ?: d.cardStyle,
            accentSource = AccentSource.entries.firstOrNull { it.name == s("accent_source") } ?: d.accentSource,
            showMusic = b("show_music", d.showMusic),
            showCharging = b("show_charging", d.showCharging),
            chargingStays = b("charging_stays", d.chargingStays),
            showCalls = b("show_calls", d.showCalls),
            showLive = b("show_live", d.showLive),
            showTorch = b("show_torch", d.showTorch),
            showNotifications = b("show_notifications", d.showNotifications),
            showPrivacy = b("show_privacy", d.showPrivacy),
            alertBatteryLow = b("alert_battery_low", d.alertBatteryLow),
            alertBatteryFull = b("alert_battery_full", d.alertBatteryFull),
            alertBatterySaver = b("alert_battery_saver", d.alertBatterySaver),
            alertDnd = b("alert_dnd", d.alertDnd),
            alertRotation = b("alert_rotation", d.alertRotation),
            alertRinger = b("alert_ringer", d.alertRinger),
            alertVolume = b("alert_volume", d.alertVolume),
            alertBrightness = b("alert_brightness", d.alertBrightness),
            alertHeadphones = b("alert_headphones", d.alertHeadphones),
            alertDurationMs = this[longPreferencesKey("alert_duration")] ?: d.alertDurationMs,
            notifyAllApps = b("notify_all_apps", d.notifyAllApps),
            notifyApps = set("notify_apps"),
            notifyExcluded = set("notify_excluded"),
            notifyShowText = b("notify_show_text", d.notifyShowText),
            hideSystemPopups = b("hide_system_popups", d.hideSystemPopups),
            pausedLingerMs = this[longPreferencesKey("paused_linger")] ?: d.pausedLingerMs,
            gestureTap = GestureAction.from(s("gesture_tap"), d.gestureTap),
            gestureLongPress = GestureAction.from(s("gesture_long_press"), d.gestureLongPress),
            gestureSwipeDown = GestureAction.from(s("gesture_swipe_down"), d.gestureSwipeDown),
            gestureSwipeLeft = GestureAction.from(s("gesture_swipe_left"), d.gestureSwipeLeft),
            gestureSwipeRight = GestureAction.from(s("gesture_swipe_right"), d.gestureSwipeRight),
            hideFullscreen = b("hide_fullscreen", d.hideFullscreen),
            hideLandscape = b("hide_landscape", d.hideLandscape),
            hideOnLockscreen = b("hide_on_lockscreen", d.hideOnLockscreen),
            hideInPlayer = b("hide_in_player", d.hideInPlayer),
            hiddenApps = set("hidden_apps"),
            gestureDoubleTap = GestureAction.from(s("gesture_double_tap"), d.gestureDoubleTap),
            gestureOverrides = set("gesture_overrides"),
            showSecond = b("show_second", d.showSecond),
            showLyrics = b("show_lyrics", d.showLyrics),
            lyricsOnline = b("lyrics_online", d.lyricsOnline),
            showUnlock = b("show_unlock", d.showUnlock),
            showCalendar = b("show_calendar", d.showCalendar),
            calendarLeadMinutes = this[intPreferencesKey("calendar_lead")] ?: d.calendarLeadMinutes,
            showInstalls = b("show_installs", d.showInstalls),
            allowAutomation = b("allow_automation", d.allowAutomation),
            showTandem = b("show_tandem", d.showTandem),
        )
    }

    /**
     * Writes only what differs from the default and removes the rest, so a default that improves in a later version
     * reaches everyone who never changed that setting.
     */
    private fun MutablePreferences.writeIsland(v: IslandSettings) {
        val d = IslandSettings()
        fun b(name: String, value: Boolean, default: Boolean) {
            val key = booleanPreferencesKey(name)
            if (value == default) remove(key) else this[key] = value
        }
        fun f(name: String, value: Float, default: Float) {
            val key = floatPreferencesKey(name)
            if (value == default) remove(key) else this[key] = value
        }
        fun fn(name: String, value: Float?) {
            if (value == null) remove(floatPreferencesKey(name)) else this[floatPreferencesKey(name)] = value
        }
        fun s(name: String, value: String, default: String) {
            val key = stringPreferencesKey(name)
            if (value == default) remove(key) else this[key] = value
        }
        fun set(name: String, value: Set<String>) {
            val key = stringSetPreferencesKey(name)
            if (value.isEmpty()) remove(key) else this[key] = value
        }
        b("island_enabled", v.enabled, d.enabled)
        s("fit_mode", v.fitMode.name, d.fitMode.name)
        b("idle_visible", v.idleVisible, d.idleVisible)
        fn("idle_width", v.idleWidthDp)
        fn("idle_height", v.idleHeightDp)
        fn("compact_width", v.compactWidthDp)
        f("shoulder", v.shoulderDp, d.shoulderDp)
        f("collapsed_roundness", v.collapsedRoundness, d.collapsedRoundness)
        f("expanded_margin", v.expandedMarginDp, d.expandedMarginDp)
        fn("expanded_top_radius", v.expandedTopRadiusDp)
        f("expanded_bottom_radius", v.expandedBottomRadiusDp, d.expandedBottomRadiusDp)
        fn("button_outer_radius", v.buttonOuterRadiusDp)
        f("button_inner_radius", v.buttonInnerRadiusDp, d.buttonInnerRadiusDp)
        b("expanded_attached", v.expandedAttached, d.expandedAttached)
        f("offset_x", v.offsetXDp, d.offsetXDp)
        f("offset_y", v.offsetYDp, d.offsetYDp)
        f("cutout_padding", v.cutoutPaddingDp, d.cutoutPaddingDp)
        val other = v.otherFit ?: FitProfile()
        b("other_fit", v.otherFit != null, false)
        fn("other_idle_width", other.idleWidthDp)
        fn("other_idle_height", other.idleHeightDp)
        fn("other_compact_width", other.compactWidthDp)
        f("other_shoulder", other.shoulderDp, d.shoulderDp)
        f("other_roundness", other.collapsedRoundness, d.collapsedRoundness)
        f("other_offset_x", other.offsetXDp, d.offsetXDp)
        f("other_offset_y", other.offsetYDp, d.offsetYDp)
        f("other_cutout_padding", other.cutoutPaddingDp, d.cutoutPaddingDp)
        f("bounce", v.bounce, d.bounce)
        f("speed", v.speed, d.speed)
        b("haptics", v.hapticsEnabled, d.hapticsEnabled)
        f("haptics_strength", v.hapticsStrength, d.hapticsStrength)
        s("card_style", v.cardStyle.name, d.cardStyle.name)
        s("accent_source", v.accentSource.name, d.accentSource.name)
        b("show_music", v.showMusic, d.showMusic)
        b("show_charging", v.showCharging, d.showCharging)
        b("charging_stays", v.chargingStays, d.chargingStays)
        b("show_calls", v.showCalls, d.showCalls)
        b("show_live", v.showLive, d.showLive)
        b("show_torch", v.showTorch, d.showTorch)
        b("show_notifications", v.showNotifications, d.showNotifications)
        b("show_privacy", v.showPrivacy, d.showPrivacy)
        b("alert_battery_low", v.alertBatteryLow, d.alertBatteryLow)
        b("alert_battery_full", v.alertBatteryFull, d.alertBatteryFull)
        b("alert_battery_saver", v.alertBatterySaver, d.alertBatterySaver)
        b("alert_dnd", v.alertDnd, d.alertDnd)
        b("alert_rotation", v.alertRotation, d.alertRotation)
        b("alert_ringer", v.alertRinger, d.alertRinger)
        b("alert_volume", v.alertVolume, d.alertVolume)
        b("alert_brightness", v.alertBrightness, d.alertBrightness)
        b("alert_headphones", v.alertHeadphones, d.alertHeadphones)
        longPreferencesKey("alert_duration").let { if (v.alertDurationMs == d.alertDurationMs) remove(it) else this[it] = v.alertDurationMs }
        b("notify_all_apps", v.notifyAllApps, d.notifyAllApps)
        set("notify_apps", v.notifyApps)
        set("notify_excluded", v.notifyExcluded)
        b("notify_show_text", v.notifyShowText, d.notifyShowText)
        b("hide_system_popups", v.hideSystemPopups, d.hideSystemPopups)
        longPreferencesKey("paused_linger").let { if (v.pausedLingerMs == d.pausedLingerMs) remove(it) else this[it] = v.pausedLingerMs }
        s("gesture_tap", v.gestureTap.name, d.gestureTap.name)
        s("gesture_long_press", v.gestureLongPress.name, d.gestureLongPress.name)
        s("gesture_swipe_down", v.gestureSwipeDown.name, d.gestureSwipeDown.name)
        s("gesture_swipe_left", v.gestureSwipeLeft.name, d.gestureSwipeLeft.name)
        s("gesture_swipe_right", v.gestureSwipeRight.name, d.gestureSwipeRight.name)
        b("hide_fullscreen", v.hideFullscreen, d.hideFullscreen)
        b("hide_landscape", v.hideLandscape, d.hideLandscape)
        b("hide_on_lockscreen", v.hideOnLockscreen, d.hideOnLockscreen)
        b("hide_in_player", v.hideInPlayer, d.hideInPlayer)
        set("hidden_apps", v.hiddenApps)
        s("gesture_double_tap", v.gestureDoubleTap.name, d.gestureDoubleTap.name)
        set("gesture_overrides", v.gestureOverrides)
        b("show_second", v.showSecond, d.showSecond)
        b("show_lyrics", v.showLyrics, d.showLyrics)
        b("lyrics_online", v.lyricsOnline, d.lyricsOnline)
        b("show_unlock", v.showUnlock, d.showUnlock)
        b("show_calendar", v.showCalendar, d.showCalendar)
        intPreferencesKey("calendar_lead").let { if (v.calendarLeadMinutes == d.calendarLeadMinutes) remove(it) else this[it] = v.calendarLeadMinutes }
        b("show_installs", v.showInstalls, d.showInstalls)
        b("allow_automation", v.allowAutomation, d.allowAutomation)
        b("show_tandem", v.showTandem, d.showTandem)
    }

    /** Every setting a person made, as JSON, for the backup file. Typed so a restore puts back the same kinds. */
    suspend fun exportJson(): String {
        val prefs = data.first()
        val root = JSONObject().put("app", "drip").put("version", 1)
        val values = JSONObject()
        for ((key, value) in prefs.asMap()) {
            if (key.name in notBackedUp) continue
            val entry = when (value) {
                is Boolean -> JSONObject().put("t", "b").put("v", value)
                is Float -> JSONObject().put("t", "f").put("v", value.toDouble())
                is Int -> JSONObject().put("t", "i").put("v", value)
                is Long -> JSONObject().put("t", "l").put("v", value)
                is String -> JSONObject().put("t", "s").put("v", value)
                is Set<*> -> JSONObject().put("t", "set").put("v", JSONArray(value.map { it.toString() }))
                else -> continue
            }
            values.put(key.name, entry)
        }
        return root.put("values", values).toString(2)
    }

    /** Puts a backup back. False when the file is not a Drip backup. */
    suspend fun importJson(text: String): Boolean {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return false
        if (root.optString("app") != "drip") return false
        val values = root.optJSONObject("values") ?: return false
        context.dataStore.edit { prefs ->
            for (name in values.keys()) {
                if (name in notBackedUp) continue
                val entry = values.optJSONObject(name) ?: continue
                when (entry.optString("t")) {
                    "b" -> prefs[booleanPreferencesKey(name)] = entry.optBoolean("v")
                    "f" -> prefs[floatPreferencesKey(name)] = entry.optDouble("v").toFloat()
                    "i" -> prefs[intPreferencesKey(name)] = entry.optInt("v")
                    "l" -> prefs[longPreferencesKey(name)] = entry.optLong("v")
                    "s" -> prefs[stringPreferencesKey(name)] = entry.optString("v")
                    "set" -> {
                        val array = entry.optJSONArray("v") ?: JSONArray()
                        prefs[stringSetPreferencesKey(name)] = (0 until array.length()).map { array.getString(it) }.toSet()
                    }
                }
            }
        }
        return true
    }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }
}
