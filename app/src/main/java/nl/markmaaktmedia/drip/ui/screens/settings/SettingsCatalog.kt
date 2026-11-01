package nl.markmaaktmedia.drip.ui.screens.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.ui.AppPickerKind
import nl.markmaaktmedia.drip.ui.Route
import nl.markmaaktmedia.drip.ui.theme.DripIcons

/** The pages of the settings overview. */
enum class SettingsPageId(val key: String) {
    Shape("shape"),
    Motion("motion"),
    Shows("shows"),
    Notifications("notifications"),
    Gestures("gestures"),
    Hide("hide"),
    Look("look"),
    Keep("keep"),
    Updates("updates"),
    About("about"),
    ;

    companion object {
        fun fromKey(key: String): SettingsPageId? = entries.firstOrNull { it.key == key }
    }
}

/** What the overview lists, in this order. */
internal enum class SettingsCategory(
    @param:StringRes val title: Int,
    val icon: @Composable () -> Painter,
    val route: Route,
) {
    Shape(R.string.settings_cat_shape, { DripIcons.FitScreen }, Route.SettingsPage(SettingsPageId.Shape)),
    Motion(R.string.settings_cat_motion, { DripIcons.Speed }, Route.SettingsPage(SettingsPageId.Motion)),
    Look(R.string.settings_look, { DripIcons.Palette }, Route.SettingsPage(SettingsPageId.Look)),
    Shows(R.string.settings_cat_shows, { DripIcons.InstantMix }, Route.SettingsPage(SettingsPageId.Shows)),
    Notifications(R.string.settings_cat_notifications, { DripIcons.Notifications }, Route.SettingsPage(SettingsPageId.Notifications)),
    Gestures(R.string.settings_cat_gestures, { DripIcons.Gesture }, Route.SettingsPage(SettingsPageId.Gestures)),
    Hide(R.string.settings_cat_hide, { DripIcons.VisibilityOff }, Route.SettingsPage(SettingsPageId.Hide)),
    Keep(R.string.settings_cat_keep, { DripIcons.Terminal }, Route.SettingsPage(SettingsPageId.Keep)),
    Updates(R.string.settings_cat_updates, { DripIcons.Update }, Route.SettingsPage(SettingsPageId.Updates)),
    About(R.string.settings_cat_about, { DripIcons.Info }, Route.SettingsPage(SettingsPageId.About)),
}

/**
 * The keys of the rows a search can take you to. The catalog says "go to this key" and the page puts the same key on
 * its row, so they are written once, here.
 */
internal object FocusKeys {
    const val ShapeFit = "shape.fit"
    const val ShapeIdleVisible = "shape.idle"
    const val ShapeIdleWidth = "shape.idle.width"
    const val ShapeIdleHeight = "shape.idle.height"
    const val ShapeCompactWidth = "shape.compact"
    const val ShapeShoulder = "shape.shoulder"
    const val ShapeRoundness = "shape.roundness"
    const val ShapeMargin = "shape.margin"
    const val ShapeTop = "shape.top"
    const val ShapeBottom = "shape.bottom"
    const val ShapeButtonOuter = "shape.button.outer"
    const val ShapeButtonInner = "shape.button.inner"
    const val ShapeAttached = "shape.attached"
    const val ShapeNudgeX = "shape.nudge.x"
    const val ShapeNudgeY = "shape.nudge.y"
    const val ShapeCutout = "shape.cutout"
    const val ShapeReset = "shape.reset"

    const val Bounce = "motion.bounce"
    const val Speed = "motion.speed"
    const val Haptics = "motion.haptics"
    const val Strength = "motion.strength"

    const val ShowMusic = "shows.music"
    const val ChargingStays = "shows.charging_stays"
    const val ShowCharging = "shows.charging"
    const val ShowCalls = "shows.calls"
    const val ShowLive = "shows.live"
    const val ShowTorch = "shows.torch"
    const val ShowNotifications = "shows.notifications"
    const val ShowPrivacy = "shows.privacy"
    const val AlertBatteryLow = "alerts.battery.low"
    const val AlertBatteryFull = "alerts.battery.full"
    const val AlertBatterySaver = "alerts.battery.saver"
    const val AlertDnd = "alerts.dnd"
    const val AlertRotation = "alerts.rotation"
    const val AlertRinger = "alerts.ringer"
    const val AlertVolume = "alerts.volume"
    const val AlertBrightness = "alerts.brightness"
    const val AlertHeadphones = "alerts.headphones"
    const val AlertDuration = "alerts.duration"
    const val PausedLinger = "shows.paused_linger"
    const val ShowSecond = "shows.second"
    const val ShowLyrics = "shows.lyrics"
    const val LyricsOnline = "shows.lyrics.online"
    const val ShowCalendar = "shows.calendar"
    const val CalendarLead = "shows.calendar.lead"
    const val ShowInstalls = "shows.installs"
    const val ShowUnlock = "shows.unlock"
    const val ShowTandem = "shows.tandem"
    const val AllowAutomation = "shows.automation"
    const val AutomationDocs = "shows.automation.docs"

    const val NotifyOn = "notif.on"
    const val NotifyWhich = "notif.which"
    const val NotifyText = "notif.text"
    const val NotifyHidePopups = "notif.hide_popups"

    const val GestureTap = "gesture.tap"
    const val GestureHold = "gesture.hold"
    const val GestureDown = "gesture.down"
    const val GestureLeft = "gesture.left"
    const val GestureRight = "gesture.right"
    const val GestureDoubleTap = "gesture.double"
    const val GestureScope = "gesture.scope"

    const val HideFullscreen = "hide.fullscreen"
    const val HideLandscape = "hide.landscape"
    const val HideLock = "hide.lock"

    const val LookCard = "look.card"
    const val LookAccent = "look.accent"
    const val AppearanceMode = "appearance.mode"
    const val AppearanceBlack = "appearance.black"
    const val AppearanceAccent = "appearance.accent"
    const val AppearanceStyle = "appearance.style"
    const val Language = "look.language"

    const val KeepStatus = "keep.status"
    const val KeepShizuku = "keep.shizuku"
    const val KeepAdb = "keep.adb"

    const val Version = "updates.version"
    const val AutoUpdate = "updates.auto"
    const val CheckUpdate = "updates.check"
    const val BackupExport = "backup.export"
    const val BackupImport = "backup.import"

    const val Source = "about.source"
    const val Privacy = "about.privacy"
    const val Reset = "about.reset"
}

/**
 * One setting that can be found. The words come from string resources (the title and the description are the very
 * strings the row shows, the keywords one string shared by a few entries), so the search works in the language of the
 * app.
 *
 * [route] is the page that opens and [focus] the row on it to scroll to. [via] is a page to put underneath, so that
 * Back from a leaf screen goes up to the page that normally leads to it instead of straight to the overview.
 * [page] names the leaf screen when the entry lives there rather than on the page of its category.
 */
internal class SettingsEntry(
    val id: String,
    @param:StringRes val title: Int,
    @param:StringRes val keywords: Int?,
    val category: SettingsCategory,
    val route: Route,
    val icon: @Composable () -> Painter,
    @param:StringRes val subtitle: Int? = null,
    @param:StringRes val page: Int? = null,
    val focus: String? = null,
    val via: Route? = null,
) {
    /** True for the entry that is the page of its category itself, not a row on it. */
    val isCategoryPage: Boolean get() = focus == null && route == category.route
}

internal object SettingsCatalog {
    private val shape = Route.SettingsPage(SettingsPageId.Shape)
    private val motion = Route.SettingsPage(SettingsPageId.Motion)
    private val shows = Route.SettingsPage(SettingsPageId.Shows)
    private val notifications = Route.SettingsPage(SettingsPageId.Notifications)
    private val gestures = Route.SettingsPage(SettingsPageId.Gestures)
    private val hide = Route.SettingsPage(SettingsPageId.Hide)
    private val look = Route.SettingsPage(SettingsPageId.Look)
    private val keep = Route.SettingsPage(SettingsPageId.Keep)
    private val updates = Route.SettingsPage(SettingsPageId.Updates)
    private val about = Route.SettingsPage(SettingsPageId.About)

    private fun page(category: SettingsCategory, keywords: Int) =
        SettingsEntry(category.name.lowercase(), category.title, keywords, category, category.route, category.icon)

    private fun row(
        id: String, title: Int, keywords: Int?, category: SettingsCategory, focus: String, icon: @Composable () -> Painter,
        subtitle: Int? = null,
    ) = SettingsEntry(id, title, keywords, category, category.route, icon, subtitle = subtitle, focus = focus)

    val all: List<SettingsEntry> = listOf(
        // Shape and fit
        page(SettingsCategory.Shape, R.string.settings_kw_shape),
        row("shape_fit", R.string.shape_fit, R.string.settings_kw_fit, SettingsCategory.Shape, FocusKeys.ShapeFit, { DripIcons.FitScreen }),
        row("shape_idle", R.string.shape_idle_visible, null, SettingsCategory.Shape, FocusKeys.ShapeIdleVisible, { DripIcons.Visibility }, R.string.shape_idle_visible_sub),
        row("shape_idle_width", R.string.shape_idle_width, R.string.settings_kw_sizes, SettingsCategory.Shape, FocusKeys.ShapeIdleWidth, { DripIcons.Straighten }, R.string.shape_size_auto),
        row("shape_idle_height", R.string.shape_idle_height, R.string.settings_kw_sizes, SettingsCategory.Shape, FocusKeys.ShapeIdleHeight, { DripIcons.Straighten }, R.string.shape_size_auto),
        row("shape_compact", R.string.shape_compact_width, R.string.settings_kw_sizes, SettingsCategory.Shape, FocusKeys.ShapeCompactWidth, { DripIcons.Straighten }, R.string.shape_compact_width_sub),
        row("shape_shoulder", R.string.shape_shoulder, R.string.settings_kw_corners, SettingsCategory.Shape, FocusKeys.ShapeShoulder, { DripIcons.Contrast }, R.string.shape_shoulder_sub),
        row("shape_roundness", R.string.shape_roundness, R.string.settings_kw_corners, SettingsCategory.Shape, FocusKeys.ShapeRoundness, { DripIcons.Contrast }, R.string.shape_roundness_sub),
        row("shape_margin", R.string.shape_margin, R.string.settings_kw_sizes, SettingsCategory.Shape, FocusKeys.ShapeMargin, { DripIcons.OpenInFull }, R.string.shape_margin_sub),
        row("shape_top", R.string.shape_top_corners, R.string.settings_kw_corners, SettingsCategory.Shape, FocusKeys.ShapeTop, { DripIcons.Contrast }, R.string.shape_top_corners_auto),
        row("shape_bottom", R.string.shape_bottom_corners, R.string.settings_kw_corners, SettingsCategory.Shape, FocusKeys.ShapeBottom, { DripIcons.Contrast }, R.string.shape_bottom_corners_sub),
        row("shape_button_outer", R.string.shape_button_outer, R.string.settings_kw_corners, SettingsCategory.Shape, FocusKeys.ShapeButtonOuter, { DripIcons.Touch }, R.string.shape_button_outer_auto),
        row("shape_button_inner", R.string.shape_button_inner, R.string.settings_kw_corners, SettingsCategory.Shape, FocusKeys.ShapeButtonInner, { DripIcons.Touch }, R.string.shape_button_inner_sub),
        row("shape_attached", R.string.shape_attached, null, SettingsCategory.Shape, FocusKeys.ShapeAttached, { DripIcons.Pin }, R.string.shape_attached_sub),
        row("shape_nudge_x", R.string.shape_nudge_x, R.string.settings_kw_nudge, SettingsCategory.Shape, FocusKeys.ShapeNudgeX, { DripIcons.Drag }, R.string.shape_nudge_sub),
        row("shape_nudge_y", R.string.shape_nudge_y, R.string.settings_kw_nudge, SettingsCategory.Shape, FocusKeys.ShapeNudgeY, { DripIcons.Drag }, R.string.shape_nudge_sub),
        row("shape_cutout", R.string.shape_cutout_padding, R.string.settings_kw_fit, SettingsCategory.Shape, FocusKeys.ShapeCutout, { DripIcons.Camera }, R.string.shape_cutout_padding_sub),
        row("shape_reset", R.string.shape_reset, null, SettingsCategory.Shape, FocusKeys.ShapeReset, { DripIcons.Restart }, R.string.shape_reset_sub),

        // Motion and feel
        page(SettingsCategory.Motion, R.string.settings_kw_motion),
        row("motion_bounce", R.string.motion_bounce, R.string.settings_kw_motion, SettingsCategory.Motion, FocusKeys.Bounce, { DripIcons.Swipe }, R.string.motion_bounce_sub),
        row("motion_speed", R.string.motion_speed, R.string.settings_kw_motion, SettingsCategory.Motion, FocusKeys.Speed, { DripIcons.Speed }, R.string.motion_speed_sub),
        row("motion_haptics", R.string.motion_haptics, R.string.settings_kw_haptics, SettingsCategory.Motion, FocusKeys.Haptics, { DripIcons.Vibration }, R.string.motion_haptics_sub),
        row("motion_strength", R.string.motion_strength, R.string.settings_kw_haptics, SettingsCategory.Motion, FocusKeys.Strength, { DripIcons.Vibration }, R.string.motion_strength_sub),

        // What the island shows
        page(SettingsCategory.Shows, R.string.settings_kw_shows),
        row("show_music", R.string.show_music, R.string.settings_kw_shows, SettingsCategory.Shows, FocusKeys.ShowMusic, { DripIcons.Music }, R.string.show_music_sub),
        row("charging_stays", R.string.charging_stays, R.string.settings_kw_shows, SettingsCategory.Shows, FocusKeys.ChargingStays, { DripIcons.Pin }, R.string.charging_stays_sub),
        row("paused_linger", R.string.paused_linger, R.string.settings_kw_shows, SettingsCategory.Shows, FocusKeys.PausedLinger, { DripIcons.Pause }, null),
        row("show_charging", R.string.show_charging, R.string.settings_kw_shows, SettingsCategory.Shows, FocusKeys.ShowCharging, { DripIcons.BatteryCharging }, R.string.show_charging_sub),
        row("show_calls", R.string.show_calls, R.string.settings_kw_shows, SettingsCategory.Shows, FocusKeys.ShowCalls, { DripIcons.Call }, R.string.show_calls_sub),
        row("show_live", R.string.show_live, R.string.settings_kw_shows, SettingsCategory.Shows, FocusKeys.ShowLive, { DripIcons.Timer }, R.string.show_live_sub),
        row("show_torch", R.string.show_torch, R.string.settings_kw_shows, SettingsCategory.Shows, FocusKeys.ShowTorch, { DripIcons.Flashlight }, R.string.show_torch_sub),
        row("show_notifications", R.string.show_notifications, R.string.settings_kw_notifications, SettingsCategory.Shows, FocusKeys.ShowNotifications, { DripIcons.Notifications }, R.string.show_notifications_sub),
        row("show_privacy", R.string.show_privacy, R.string.settings_kw_shows, SettingsCategory.Shows, FocusKeys.ShowPrivacy, { DripIcons.Mic }, R.string.show_privacy_sub),
        row("alert_battery_low", R.string.alert_battery_low, R.string.settings_kw_alerts, SettingsCategory.Shows, FocusKeys.AlertBatteryLow, { DripIcons.BatteryAlert }),
        row("alert_battery_full", R.string.alert_battery_full, R.string.settings_kw_alerts, SettingsCategory.Shows, FocusKeys.AlertBatteryFull, { DripIcons.Battery }),
        row("alert_battery_saver", R.string.alert_battery_saver, R.string.settings_kw_alerts, SettingsCategory.Shows, FocusKeys.AlertBatterySaver, { DripIcons.BatterySaver }),
        row("alert_dnd", R.string.alert_dnd, R.string.settings_kw_alerts, SettingsCategory.Shows, FocusKeys.AlertDnd, { DripIcons.Dnd }),
        row("alert_rotation", R.string.alert_rotation, R.string.settings_kw_alerts, SettingsCategory.Shows, FocusKeys.AlertRotation, { DripIcons.ScreenRotation }),
        row("alert_ringer", R.string.alert_ringer, R.string.settings_kw_alerts, SettingsCategory.Shows, FocusKeys.AlertRinger, { DripIcons.Ring }),
        row("alert_volume", R.string.alert_volume, R.string.settings_kw_alerts, SettingsCategory.Shows, FocusKeys.AlertVolume, { DripIcons.VolumeUp }),
        row("alert_brightness", R.string.alert_brightness, R.string.settings_kw_alerts, SettingsCategory.Shows, FocusKeys.AlertBrightness, { DripIcons.Brightness }),
        row("alert_headphones", R.string.alert_headphones, R.string.settings_kw_alerts, SettingsCategory.Shows, FocusKeys.AlertHeadphones, { DripIcons.Headphones }),
        row("show_second", R.string.set2_second, R.string.set2_kw_more, SettingsCategory.Shows, FocusKeys.ShowSecond, { DripIcons.Tab }, R.string.set2_second_sub),
        row("show_lyrics", R.string.set2_lyrics, R.string.set2_kw_more, SettingsCategory.Shows, FocusKeys.ShowLyrics, { DripIcons.Lyrics }, R.string.set2_lyrics_sub),
        row("lyrics_online", R.string.set2_lyrics_online, R.string.set2_kw_more, SettingsCategory.Shows, FocusKeys.LyricsOnline, { DripIcons.Language }, R.string.set2_lyrics_online_sub),
        row("show_calendar", R.string.set2_calendar, R.string.set2_kw_more, SettingsCategory.Shows, FocusKeys.ShowCalendar, { DripIcons.Calendar }, R.string.set2_calendar_sub),
        row("calendar_lead", R.string.set2_calendar_lead, R.string.set2_kw_more, SettingsCategory.Shows, FocusKeys.CalendarLead, { DripIcons.Hourglass }),
        row("show_installs", R.string.set2_installs, R.string.set2_kw_more, SettingsCategory.Shows, FocusKeys.ShowInstalls, { DripIcons.InstallMobile }, R.string.set2_installs_sub),
        row("show_unlock", R.string.set2_unlock, R.string.set2_kw_more, SettingsCategory.Shows, FocusKeys.ShowUnlock, { DripIcons.LockOpen }, R.string.set2_unlock_sub),
        row("show_tandem", R.string.set2_tandem, R.string.set2_kw_more, SettingsCategory.Shows, FocusKeys.ShowTandem, { DripIcons.Devices }, R.string.set2_tandem_sub),
        row("allow_automation", R.string.set2_automation, R.string.set2_kw_automation, SettingsCategory.Shows, FocusKeys.AllowAutomation, { DripIcons.Hub }, R.string.set2_automation_sub),
        row("automation_docs", R.string.set2_automation_docs, R.string.set2_kw_automation, SettingsCategory.Shows, FocusKeys.AutomationDocs, { DripIcons.OpenInNew }, R.string.set2_automation_docs_sub),

        // Notifications
        page(SettingsCategory.Notifications, R.string.settings_kw_notifications),
        row("alert_duration", R.string.alert_duration, R.string.settings_kw_alerts, SettingsCategory.Notifications, FocusKeys.AlertDuration, { DripIcons.Hourglass }, R.string.alert_duration_sub),
        row("notif_on", R.string.notif_on, R.string.settings_kw_notifications, SettingsCategory.Notifications, FocusKeys.NotifyOn, { DripIcons.Notifications }, R.string.notif_on_sub),
        row("notif_which", R.string.notif_which, R.string.settings_kw_notifications, SettingsCategory.Notifications, FocusKeys.NotifyWhich, { DripIcons.Apps }),
        SettingsEntry(
            "notif_apps", R.string.picker_notify_title, R.string.settings_kw_notifications, SettingsCategory.Notifications,
            Route.AppPicker(AppPickerKind.NOTIFY), { DripIcons.Apps }, page = R.string.notif_apps, via = notifications,
        ),
        row("notif_text", R.string.notif_text, R.string.settings_kw_notifications, SettingsCategory.Notifications, FocusKeys.NotifyText, { DripIcons.File }, R.string.notif_text_sub),
        row("notif_hide_popups", R.string.notif_hide_popups, R.string.settings_kw_notifications, SettingsCategory.Notifications, FocusKeys.NotifyHidePopups, { DripIcons.NotificationsOff }, R.string.notif_hide_popups_sub),

        // Gestures
        page(SettingsCategory.Gestures, R.string.settings_kw_gestures),
        row("gesture_scope", R.string.set2_gesture_scope, R.string.settings_kw_gestures, SettingsCategory.Gestures, FocusKeys.GestureScope, { DripIcons.Gesture }),
        row("gesture_tap", R.string.gesture_tap, R.string.settings_kw_gestures, SettingsCategory.Gestures, FocusKeys.GestureTap, { DripIcons.Touch }),
        row("gesture_double", R.string.set2_gesture_double_tap, R.string.settings_kw_gestures, SettingsCategory.Gestures, FocusKeys.GestureDoubleTap, { DripIcons.Touch }),
        row("gesture_hold", R.string.gesture_hold, R.string.settings_kw_gestures, SettingsCategory.Gestures, FocusKeys.GestureHold, { DripIcons.Touch }),
        row("gesture_down", R.string.gesture_swipe_down, R.string.settings_kw_gestures, SettingsCategory.Gestures, FocusKeys.GestureDown, { DripIcons.KeyDown }),
        row("gesture_left", R.string.gesture_swipe_left, R.string.settings_kw_gestures, SettingsCategory.Gestures, FocusKeys.GestureLeft, { DripIcons.KeyLeft }),
        row("gesture_right", R.string.gesture_swipe_right, R.string.settings_kw_gestures, SettingsCategory.Gestures, FocusKeys.GestureRight, { DripIcons.KeyRight }),

        // When to step aside
        page(SettingsCategory.Hide, R.string.settings_kw_hide),
        row("hide_fullscreen", R.string.hide_fullscreen, R.string.settings_kw_hide, SettingsCategory.Hide, FocusKeys.HideFullscreen, { DripIcons.OpenInFull }, R.string.hide_fullscreen_sub),
        row("hide_landscape", R.string.hide_landscape, R.string.settings_kw_hide, SettingsCategory.Hide, FocusKeys.HideLandscape, { DripIcons.ScreenRotation }, R.string.hide_landscape_sub),
        row("hide_lock", R.string.hide_lock, R.string.settings_kw_hide, SettingsCategory.Hide, FocusKeys.HideLock, { DripIcons.Lock }, R.string.hide_lock_sub),
        SettingsEntry(
            "hide_apps", R.string.picker_hide_title, R.string.settings_kw_hide, SettingsCategory.Hide,
            Route.AppPicker(AppPickerKind.HIDE), { DripIcons.Apps }, page = R.string.hide_apps, via = hide,
        ),

        // Look and language
        page(SettingsCategory.Look, R.string.settings_kw_look),
        row("look_card", R.string.look_card, R.string.settings_kw_look, SettingsCategory.Look, FocusKeys.LookCard, { DripIcons.Palette }, R.string.look_card_sub),
        row("look_accent", R.string.look_accent, R.string.settings_kw_look, SettingsCategory.Look, FocusKeys.LookAccent, { DripIcons.Music }, R.string.look_accent_sub),
        row("look_mode", R.string.appearance_mode, R.string.settings_kw_look, SettingsCategory.Look, FocusKeys.AppearanceMode, { DripIcons.DarkMode }),
        row("look_black", R.string.appearance_black, R.string.settings_kw_look, SettingsCategory.Look, FocusKeys.AppearanceBlack, { DripIcons.DarkMode }, R.string.appearance_black_sub),
        SettingsEntry(
            "look_appearance", R.string.settings_appearance, R.string.settings_kw_appearance, SettingsCategory.Look, Route.Appearance,
            { DripIcons.Palette }, subtitle = R.string.settings_appearance_sub, via = look,
        ),
        SettingsEntry(
            "look_seed", R.string.appearance_accent, R.string.settings_kw_appearance, SettingsCategory.Look, Route.Appearance,
            { DripIcons.Palette }, page = R.string.settings_appearance, focus = FocusKeys.AppearanceAccent, via = look,
        ),
        SettingsEntry(
            "look_style", R.string.appearance_style, R.string.settings_kw_appearance, SettingsCategory.Look, Route.Appearance,
            { DripIcons.Palette }, page = R.string.settings_appearance, focus = FocusKeys.AppearanceStyle, via = look,
        ),
        row("look_language", R.string.settings_language, R.string.settings_kw_language, SettingsCategory.Look, FocusKeys.Language, { DripIcons.Language }),

        // Keep the island on
        page(SettingsCategory.Keep, R.string.settings_kw_keep),
        row("keep_shizuku", R.string.keep_section_shizuku, R.string.settings_kw_keep, SettingsCategory.Keep, FocusKeys.KeepShizuku, { DripIcons.Terminal }),
        row("keep_adb", R.string.keep_section_adb, R.string.settings_kw_keep, SettingsCategory.Keep, FocusKeys.KeepAdb, { DripIcons.Desktop }, R.string.keep_adb_body),

        // Updates and backup
        page(SettingsCategory.Updates, R.string.settings_kw_updates),
        row("updates_version", R.string.settings_version, R.string.settings_kw_updates, SettingsCategory.Updates, FocusKeys.Version, { DripIcons.Info }),
        row("updates_auto", R.string.settings_auto_update, R.string.settings_kw_updates, SettingsCategory.Updates, FocusKeys.AutoUpdate, { DripIcons.Update }, R.string.settings_auto_update_sub),
        row("updates_check", R.string.settings_check_update, R.string.settings_kw_updates, SettingsCategory.Updates, FocusKeys.CheckUpdate, { DripIcons.Refresh }),
        SettingsEntry(
            "updates_changelog", R.string.changelog_title, R.string.settings_kw_updates, SettingsCategory.Updates, Route.Changelog,
            { DripIcons.Update }, subtitle = R.string.changelog_sub, via = updates,
        ),
        row("backup_export", R.string.backup_export, R.string.settings_kw_updates, SettingsCategory.Updates, FocusKeys.BackupExport, { DripIcons.Upload }, R.string.backup_export_sub),
        row("backup_import", R.string.backup_import, R.string.settings_kw_updates, SettingsCategory.Updates, FocusKeys.BackupImport, { DripIcons.Download }, R.string.backup_import_sub),

        // About
        page(SettingsCategory.About, R.string.settings_kw_about),
        row("about_source", R.string.about_github, R.string.settings_kw_about, SettingsCategory.About, FocusKeys.Source, { DripIcons.OpenInNew }, R.string.about_github_sub),
        row("about_privacy", R.string.about_privacy, R.string.settings_kw_about, SettingsCategory.About, FocusKeys.Privacy, { DripIcons.Shield }, R.string.about_privacy_body),
        row("about_reset", R.string.about_reset, R.string.settings_kw_about, SettingsCategory.About, FocusKeys.Reset, { DripIcons.Restart }, R.string.about_reset_sub),
    )

    /** The catalog in the language shown now. Looked up by [SearchEntry.id] to find what to open. */
    fun index(context: Context): SettingsIndex {
        val entries = all.map { entry ->
            SearchEntry(
                id = entry.id,
                title = context.getString(entry.title),
                subtitle = entry.subtitle?.let(context::getString),
                keywords = entry.keywords?.let { SettingsSearch.parseKeywords(context.getString(it)) }.orEmpty(),
                category = context.getString(entry.category.title),
                page = entry.page?.let(context::getString),
            )
        }
        return SettingsIndex(entries, all.associateBy { it.id })
    }
}

internal class SettingsIndex(val entries: List<SearchEntry>, private val byId: Map<String, SettingsEntry>) {
    fun target(id: String): SettingsEntry? = byId[id]
}
