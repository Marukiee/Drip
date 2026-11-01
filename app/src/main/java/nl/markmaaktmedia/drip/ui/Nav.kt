package nl.markmaaktmedia.drip.ui

import androidx.compose.runtime.mutableStateListOf
import nl.markmaaktmedia.drip.ui.screens.settings.SettingsPageId

/** Which list of apps the picker edits. */
enum class AppPickerKind(val key: String) {
    /** The apps whose notifications may open the island. */
    NOTIFY("notify"),

    /** The apps in which the island steps aside. */
    HIDE("hide"),
    ;

    companion object {
        fun fromKey(key: String): AppPickerKind? = entries.firstOrNull { it.key == key }
    }
}

sealed interface Route {
    data object Home : Route
    data object Appearance : Route
    data object Changelog : Route

    /** The first steps again, from About. Finishing it only closes it. */
    data object OnboardingPreview : Route
    data class AppPicker(val kind: AppPickerKind) : Route

    /** A page of the settings overview. */
    data class SettingsPage(val page: SettingsPageId) : Route
}

/** A small back stack. The home screen is always at the bottom. */
class Nav {
    val stack = mutableStateListOf<Route>(Route.Home)

    // The scroll position of the home screen lives here, not in the screen: a page opened from it takes it out of the
    // composition, and coming back must not start from the top.
    val homeList = androidx.compose.foundation.lazy.LazyListState()

    val top: Route get() = stack.last()

    fun push(route: Route) {
        if (top != route) stack.add(route)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }
}

/** Keeps the back stack through a rotation or any other recreation of the activity. */
val NavSaver: androidx.compose.runtime.saveable.Saver<Nav, Any> = androidx.compose.runtime.saveable.listSaver<Nav, String>(
    save = { nav -> nav.stack.map(::routeKey) },
    restore = { saved ->
        Nav().also { nav ->
            nav.stack.clear()
            nav.stack.addAll(saved.mapNotNull(::routeFromKey).ifEmpty { listOf(Route.Home) })
        }
    },
)

private fun routeFromKey(key: String): Route? = when {
    key == "home" -> Route.Home
    key == "appearance" -> Route.Appearance
    key == "changelog" -> Route.Changelog
    key == "onboarding-preview" -> Route.OnboardingPreview
    key.startsWith("apps:") -> AppPickerKind.fromKey(key.removePrefix("apps:"))?.let(Route::AppPicker)
    key.startsWith("settings:") -> SettingsPageId.fromKey(key.removePrefix("settings:"))?.let(Route::SettingsPage)
    else -> null
}
