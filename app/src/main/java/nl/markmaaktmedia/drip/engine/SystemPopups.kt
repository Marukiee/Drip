package nl.markmaaktmedia.drip.engine

import android.content.Context
import android.provider.Settings
import android.util.Log

/**
 * Android's own pop-ups for new notifications, switched off while the island shows them instead.
 *
 * It is one global setting that needs WRITE_SECURE_SETTINGS, the same permission that keeps the island on. Drip only
 * puts back what it switched off itself, and does so as soon as the island stops, so the pop-ups are never gone while
 * nothing shows the messages.
 */
object SystemPopups {

    fun apply(context: Context, hide: Boolean) {
        if (!IslandKeeper.canKeep(context)) return
        val resolver = context.contentResolver
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        runCatching {
            if (hide) {
                if (Settings.Global.getInt(resolver, HEADS_UP, 1) != 0) {
                    Settings.Global.putInt(resolver, HEADS_UP, 0)
                    prefs.edit().putBoolean(OURS, true).apply()
                }
            } else if (prefs.getBoolean(OURS, false)) {
                Settings.Global.putInt(resolver, HEADS_UP, 1)
                prefs.edit().remove(OURS).apply()
            }
        }.onFailure { Log.w(TAG, "could not switch Android's pop-ups", it) }
    }

    /** Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED, hidden from apps. */
    private const val HEADS_UP = "heads_up_notifications_enabled"
    private const val PREFS = "drip_device"
    private const val OURS = "popups_hidden_by_drip"
    private const val TAG = "SystemPopups"
}
