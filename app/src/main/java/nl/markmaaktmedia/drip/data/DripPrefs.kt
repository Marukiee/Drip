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
    NOTIFICATIONS, QUICK_SETTINGS, LOCK_SCREEN, SCREENSHOT, TORCH, OPEN_DRIP;

    companion object {
        fun from(value: String?, fallback: GestureAction) = entries.firstOrNull { it.name == value } ?: fallback
    }
}

/** How the island sits on this phone. Auto picks notch without a camera hole and a pill around one with it. */
enum class FitMode { AUTO, NOTCH, CUTOUT }

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
    /** Shows the small notch or the ring around the camera while nothing is going on. */
    val idleVisible: Boolean = true,
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
    val cutoutPaddingDp: Float = 4f,
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
    val showCalls: Boolean = true,
    val showLive: Boolean = true,
    val showTorch: Boolean = true,
    val showNotifications: Boolean = true,
    val showPrivacy: Boolean = true,
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
    /** Notifications of every app, or only of [notifyApps]. */
    val notifyAllApps: Boolean = true,
    val notifyApps: Set<String> = emptySet(),
    /** Shows the text of a notification. Off keeps it to the app and the title. */
    val notifyShowText: Boolean = true,

    // Gestures on the island.
    val gestureTap: GestureAction = GestureAction.EXPAND,
    val gestureLongPress: GestureAction = GestureAction.OPEN_APP,
    val gestureSwipeDown: GestureAction = GestureAction.EXPAND,
    val gestureSwipeLeft: GestureAction = GestureAction.NEXT_TRACK,
    val gestureSwipeRight: GestureAction = GestureAction.PREVIOUS_TRACK,

    // When the island steps aside.
    val hideFullscreen: Boolean = true,
    val hideLandscape: Boolean = true,
    val hideOnLockscreen: Boolean = false,
    val hiddenApps: Set<String> = emptySet(),
)

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
            bounce = f("bounce", d.bounce),
            speed = f("speed", d.speed),
            hapticsEnabled = b("haptics", d.hapticsEnabled),
            hapticsStrength = f("haptics_strength", d.hapticsStrength),
            cardStyle = CardStyle.entries.firstOrNull { it.name == s("card_style") } ?: d.cardStyle,
            accentSource = AccentSource.entries.firstOrNull { it.name == s("accent_source") } ?: d.accentSource,
            showMusic = b("show_music", d.showMusic),
            showCharging = b("show_charging", d.showCharging),
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
            notifyShowText = b("notify_show_text", d.notifyShowText),
            gestureTap = GestureAction.from(s("gesture_tap"), d.gestureTap),
            gestureLongPress = GestureAction.from(s("gesture_long_press"), d.gestureLongPress),
            gestureSwipeDown = GestureAction.from(s("gesture_swipe_down"), d.gestureSwipeDown),
            gestureSwipeLeft = GestureAction.from(s("gesture_swipe_left"), d.gestureSwipeLeft),
            gestureSwipeRight = GestureAction.from(s("gesture_swipe_right"), d.gestureSwipeRight),
            hideFullscreen = b("hide_fullscreen", d.hideFullscreen),
            hideLandscape = b("hide_landscape", d.hideLandscape),
            hideOnLockscreen = b("hide_on_lockscreen", d.hideOnLockscreen),
            hiddenApps = set("hidden_apps"),
        )
    }

    private fun MutablePreferences.writeIsland(v: IslandSettings) {
        fun b(name: String, value: Boolean) { this[booleanPreferencesKey(name)] = value }
        fun f(name: String, value: Float) { this[floatPreferencesKey(name)] = value }
        fun fn(name: String, value: Float?) {
            if (value == null) remove(floatPreferencesKey(name)) else this[floatPreferencesKey(name)] = value
        }
        fun s(name: String, value: String) { this[stringPreferencesKey(name)] = value }
        fun set(name: String, value: Set<String>) { this[stringSetPreferencesKey(name)] = value }
        b("island_enabled", v.enabled)
        s("fit_mode", v.fitMode.name)
        b("idle_visible", v.idleVisible)
        fn("idle_width", v.idleWidthDp)
        fn("idle_height", v.idleHeightDp)
        fn("compact_width", v.compactWidthDp)
        f("shoulder", v.shoulderDp)
        f("collapsed_roundness", v.collapsedRoundness)
        f("expanded_margin", v.expandedMarginDp)
        fn("expanded_top_radius", v.expandedTopRadiusDp)
        f("expanded_bottom_radius", v.expandedBottomRadiusDp)
        fn("button_outer_radius", v.buttonOuterRadiusDp)
        f("button_inner_radius", v.buttonInnerRadiusDp)
        b("expanded_attached", v.expandedAttached)
        f("offset_x", v.offsetXDp)
        f("offset_y", v.offsetYDp)
        f("cutout_padding", v.cutoutPaddingDp)
        f("bounce", v.bounce)
        f("speed", v.speed)
        b("haptics", v.hapticsEnabled)
        f("haptics_strength", v.hapticsStrength)
        s("card_style", v.cardStyle.name)
        s("accent_source", v.accentSource.name)
        b("show_music", v.showMusic)
        b("show_charging", v.showCharging)
        b("show_calls", v.showCalls)
        b("show_live", v.showLive)
        b("show_torch", v.showTorch)
        b("show_notifications", v.showNotifications)
        b("show_privacy", v.showPrivacy)
        b("alert_battery_low", v.alertBatteryLow)
        b("alert_battery_full", v.alertBatteryFull)
        b("alert_battery_saver", v.alertBatterySaver)
        b("alert_dnd", v.alertDnd)
        b("alert_rotation", v.alertRotation)
        b("alert_ringer", v.alertRinger)
        b("alert_volume", v.alertVolume)
        b("alert_brightness", v.alertBrightness)
        b("alert_headphones", v.alertHeadphones)
        this[longPreferencesKey("alert_duration")] = v.alertDurationMs
        b("notify_all_apps", v.notifyAllApps)
        set("notify_apps", v.notifyApps)
        b("notify_show_text", v.notifyShowText)
        s("gesture_tap", v.gestureTap.name)
        s("gesture_long_press", v.gestureLongPress.name)
        s("gesture_swipe_down", v.gestureSwipeDown.name)
        s("gesture_swipe_left", v.gestureSwipeLeft.name)
        s("gesture_swipe_right", v.gestureSwipeRight.name)
        b("hide_fullscreen", v.hideFullscreen)
        b("hide_landscape", v.hideLandscape)
        b("hide_on_lockscreen", v.hideOnLockscreen)
        set("hidden_apps", v.hiddenApps)
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
