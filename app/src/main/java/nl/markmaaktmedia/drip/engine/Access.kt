package nl.markmaaktmedia.drip.engine

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import nl.markmaaktmedia.drip.island.IslandService
import nl.markmaaktmedia.drip.sources.DripListener

/**
 * The two things Drip has to be allowed, and the screens of Android where they are allowed. Both are read from Android
 * itself as well as from the hub, because the hub only knows once a service has started, and a service that was just
 * switched on may still be on its way.
 */
object Access {
    fun islandComponent(context: Context) = ComponentName(context, IslandService::class.java)
    fun listenerComponent(context: Context) = ComponentName(context, DripListener::class.java)

    fun islandAllowed(context: Context): Boolean = runCatching {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        IslandKeeper.containsService(enabled, islandComponent(context).flattenToString())
    }.getOrDefault(false)

    fun listenerAllowed(context: Context): Boolean = runCatching {
        context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(listenerComponent(context))
    }.getOrDefault(false)

    fun postAllowed(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun granted(context: Context, permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /** The next appointment on the island. */
    fun calendarAllowed(context: Context) = granted(context, Manifest.permission.READ_CALENDAR)

    /** Nearby devices: the battery of Bluetooth earbuds. */
    fun bluetoothAllowed(context: Context) = granted(context, Manifest.permission.BLUETOOTH_CONNECT)

    /** The location dot needs precise location; approximate alone is not enough to hear whether GPS runs. */
    fun preciseLocationAllowed(context: Context) = granted(context, Manifest.permission.ACCESS_FINE_LOCATION)
    fun approximateLocationAllowed(context: Context) = granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** The Accessibility settings, with Drip's row lit up where the phone supports that. */
    fun openAccessibility(context: Context) {
        val flat = islandComponent(context).flattenToString()
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .putExtra(FragmentArgsKey, flat)
            .putExtra(ShowFragmentArgs, Bundle().apply { putString(FragmentArgsKey, flat) })
        start(context, intent)
    }

    /** Drip's own page of notification access, or the list of all of them where that page does not exist. */
    fun openListener(context: Context) {
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listenerComponent(context).flattenToString())
        if (!start(context, detail)) start(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    /** Drip's own notification settings, for a permission Android no longer asks about. */
    fun openNotificationSettings(context: Context) {
        start(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }

    /** App info, where a sideloaded app gets its restricted settings allowed. */
    fun openAppInfo(context: Context) {
        start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }

    private fun start(context: Context, intent: Intent): Boolean =
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

    // The extras Settings reads to scroll to a row and highlight it.
    private const val FragmentArgsKey = ":settings:fragment_args_key"
    private const val ShowFragmentArgs = ":settings:show_fragment_args"
}
