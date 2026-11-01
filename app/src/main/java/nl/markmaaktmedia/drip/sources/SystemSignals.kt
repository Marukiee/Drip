package nl.markmaaktmedia.drip.sources

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.provider.Settings
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.island.AlertKind

/** Do Not Disturb, auto rotate and brightness: switches that only ever announce, they keep no state in the hub. */
internal class SignalsPart(env: SourceEnv) : Part(env) {
    private val notifications: NotificationManager? = context.getSystemService(NotificationManager::class.java)
    private val resolver = context.contentResolver
    private val dnd = ChangeWatch<Int>()
    private val rotation = ChangeWatch<Int>()
    private val brightness = ChangeWatch<Int>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            safely("dnd") { onDnd() }
        }
    }

    private val rotationObserver = object : ContentObserver(env.handler) {
        override fun onChange(selfChange: Boolean) {
            safely("rotation") { onRotation() }
        }
    }

    private val brightnessObserver = object : ContentObserver(env.handler) {
        override fun onChange(selfChange: Boolean) {
            safely("brightness") { onBrightness() }
        }
    }

    override fun start() {
        dnd.reset()
        rotation.reset()
        brightness.reset()
        safely("dnd") {
            filter()?.let(dnd::update)
            context.listen(receiver, false, NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        safely("rotation") {
            rotation.update(readRotation())
            resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false, rotationObserver)
        }
        safely("brightness") {
            brightness.update(readBrightness())
            resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, brightnessObserver)
        }
    }

    override fun stop() {
        context.unlisten(receiver)
        safely("rotation") { resolver.unregisterContentObserver(rotationObserver) }
        safely("brightness") { resolver.unregisterContentObserver(brightnessObserver) }
    }

    private fun onDnd() {
        val f = filter() ?: return
        if (!dnd.update(f) || settings?.alertDnd != true) return
        val on = f != NotificationManager.INTERRUPTION_FILTER_ALL
        val detail = when (f) {
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> R.string.src_dnd_priority
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> R.string.src_dnd_alarms
            NotificationManager.INTERRUPTION_FILTER_NONE -> R.string.src_dnd_silence
            else -> null
        }
        val icon = when {
            !on -> R.drawable.sym_notifications_active
            f == NotificationManager.INTERRUPTION_FILTER_NONE -> R.drawable.sym_do_not_disturb_on_total_silence
            else -> R.drawable.sym_do_not_disturb_on
        }
        env.announce(
            "dnd", AlertKind.DND, env.text(if (on) R.string.src_dnd_on else R.string.src_dnd_off),
            detail?.let(env::text), icon,
        )
    }

    private fun onRotation() {
        val value = readRotation()
        if (!rotation.update(value) || settings?.alertRotation != true) return
        val auto = value == 1
        env.announce(
            "rotation", AlertKind.ROTATION, env.text(if (auto) R.string.src_rotation_on else R.string.src_rotation_off),
            icon = if (auto) R.drawable.sym_screen_rotation else R.drawable.sym_screen_lock_rotation,
        )
    }

    private fun onBrightness() {
        val value = readBrightness()
        if (value < 0 || !brightness.update(value) || settings?.alertBrightness != true) return
        env.announce(
            "brightness", AlertKind.BRIGHTNESS, env.text(R.string.src_brightness),
            icon = R.drawable.sym_brightness_6, level = brightnessSliderLevel(value),
        )
    }

    /** Null while Android does not know yet, which happens for a moment after boot. */
    private fun filter(): Int? = notifications?.currentInterruptionFilter
        ?.takeIf { it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN }

    private fun readRotation() = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0)

    private fun readBrightness() = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, -1)
}
