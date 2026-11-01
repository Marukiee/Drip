package nl.markmaaktmedia.drip.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.data.IslandSettings
import nl.markmaaktmedia.drip.graph
import nl.markmaaktmedia.drip.island.Alert
import nl.markmaaktmedia.drip.island.AlertKind
import nl.markmaaktmedia.drip.island.LiveInfo
import nl.markmaaktmedia.drip.island.LiveKind
import nl.markmaaktmedia.drip.sources.Apps
import nl.markmaaktmedia.drip.sources.launchApp
import kotlin.math.roundToInt

/**
 * Lets Tasker and friends put their own things on the island (see docs/AUTOMATION.md). Exported on purpose, so it does
 * nothing at all until the user allows automation in the settings, and it reads every extra as untrusted.
 */
class AutomationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val command = try {
            val extras = intent.extras
            @Suppress("DEPRECATION")
            val values = extras?.keySet()?.associateWith { extras.get(it) }.orEmpty()
            parseAutomation(intent.action, values)
        } catch (e: Exception) {
            // A parcel another app made up can fail to unpack; that is its problem, not a crash of Drip.
            Log.w(TAG, "unreadable automation intent", e)
            null
        } ?: return
        val app = context.applicationContext
        val pending = goAsync()
        app.graph.scope.launch {
            try {
                val settings = app.graph.prefs.island.first()
                if (settings.allowAutomation) withContext(Dispatchers.Main) { perform(app, command, settings) }
            } catch (e: Exception) {
                Log.w(TAG, "automation failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun perform(context: Context, command: AutomationCommand, s: IslandSettings) {
        val hub = context.graph.hub
        val key = automationKey(command.id)
        when (command) {
            is AutomationCommand.Hide -> hub.live.update { list -> list.filterNot { it.key == key } }
            is AutomationCommand.Show -> {
                val apps = Apps(context)
                val pkg = command.packageName
                val appIcon = pkg?.let(apps::icon)
                val iconRes = command.icon?.let { iconFor(it) }
                val open: (() -> Unit)? = pkg?.let { { context.launchApp(it) } }
                if (command.lasting) {
                    val info = LiveInfo(
                        key = key,
                        packageName = pkg ?: context.packageName,
                        appLabel = pkg?.let(apps::label) ?: context.getString(R.string.app_name),
                        // A live item has no slot for a symbol of Drip's, so the symbol is drawn into the app icon's.
                        appIcon = appIcon ?: iconRes?.let { symbol(context, it, command.color ?: Color.WHITE) },
                        accent = command.color,
                        kind = if (command.seconds != null) LiveKind.TIMER else LiveKind.GENERIC,
                        title = command.title,
                        text = command.text,
                        progress = command.progress,
                        chronometerBase = command.seconds?.let { System.currentTimeMillis() + it * 1000 },
                        countDown = command.seconds != null,
                        shortText = command.progress?.let { "${(it * 100).roundToInt()}%" },
                        open = open,
                    )
                    // In place when it is there already, so a bar that Tasker updates every second does not jump.
                    hub.live.update { list ->
                        if (list.any { it.key == key }) list.map { if (it.key == key) info else it } else listOf(info) + list
                    }
                } else {
                    hub.post(
                        Alert(
                            id = key,
                            kind = AlertKind.OTHER,
                            title = command.title,
                            text = command.text,
                            iconRes = iconRes ?: R.drawable.sym_notifications_active,
                            image = appIcon,
                            packageName = pkg,
                            accent = command.color,
                            level = command.progress,
                            durationMs = command.durationMs ?: s.alertDurationMs,
                            open = open,
                        ),
                    )
                }
            }
        }
    }

    private fun symbol(context: Context, res: Int, tint: Int): Bitmap? = try {
        ContextCompat.getDrawable(context, res)?.mutate()?.apply { setTint(tint) }?.toBitmap(SYMBOL_PX, SYMBOL_PX)
    } catch (e: Exception) {
        Log.w(TAG, "symbol $res", e)
        null
    }

    private companion object {
        const val TAG = "DripAutomation"
        const val SYMBOL_PX = 96

        fun iconFor(name: String): Int? = when (name) {
            "timer" -> R.drawable.sym_timer
            "music" -> R.drawable.sym_music_note
            "bolt" -> R.drawable.sym_bolt
            "notifications" -> R.drawable.sym_notifications
            "check" -> R.drawable.sym_check_circle
            "warning" -> R.drawable.sym_error
            "home" -> R.drawable.sym_home
            "car" -> R.drawable.sym_directions_car
            "alarm" -> R.drawable.sym_alarm
            "battery" -> R.drawable.sym_battery_full
            "call" -> R.drawable.sym_call
            "mail" -> R.drawable.sym_mail
            "message" -> R.drawable.sym_sms
            "info" -> R.drawable.sym_info
            "calendar" -> R.drawable.sym_calendar_month
            "location" -> R.drawable.sym_location_on
            "download" -> R.drawable.sym_download
            "upload" -> R.drawable.sym_upload
            "sports" -> R.drawable.sym_sports_soccer
            "heart" -> R.drawable.sym_favorite
            "computer" -> R.drawable.sym_computer
            "phone" -> R.drawable.sym_smartphone
            "coffee" -> R.drawable.sym_coffee
            "wifi" -> R.drawable.sym_wifi
            "bluetooth" -> R.drawable.sym_bluetooth
            "lock" -> R.drawable.sym_lock
            "headphones" -> R.drawable.sym_headphones
            "camera" -> R.drawable.sym_photo_camera
            "mic" -> R.drawable.sym_mic
            "sleep" -> R.drawable.sym_bedtime
            else -> null
        }
    }
}
