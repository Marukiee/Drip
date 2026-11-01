package nl.markmaaktmedia.drip.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log

/**
 * Switches the island back on when Android switched it off.
 *
 * Android drops accessibility services after some updates, after a crash and on some phones to save battery, and then
 * the island is gone until the person finds the switch again. With WRITE_SECURE_SETTINGS (given once with adb or through
 * Shizuku) Drip can put its own service back in the list itself. Nothing happens without that permission.
 */
object IslandKeeper {

    fun canKeep(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** True when the island is allowed afterwards, either because it already was or because it was put back. */
    fun ensure(context: Context): Boolean {
        if (Access.islandAllowed(context)) return true
        if (!canKeep(context)) return false
        val service = Access.islandComponent(context).flattenToString()
        runCatching {
            val resolver = context.contentResolver
            val current = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, withService(current, service))
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        }.onFailure { Log.w(TAG, "could not switch the island back on", it) }
        return Access.islandAllowed(context)
    }

    /**
     * [current] with [service] added at the end, unless it is in there already. The other services stay as they were and
     * in their order, so nothing the person switched on for another app is lost.
     */
    fun withService(current: String?, service: String): String {
        val entries = split(current)
        if (entries.any { same(it, service) }) return entries.joinToString(SEPARATOR)
        return (entries + service).joinToString(SEPARATOR)
    }

    fun containsService(current: String?, service: String): Boolean = split(current).any { same(it, service) }

    private fun split(value: String?): List<String> =
        value.orEmpty().split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }

    /** Android writes a class in the app's own package short, as "pkg/.Name", and sometimes in full: both are the same. */
    internal fun normalize(entry: String): String {
        val slash = entry.indexOf('/')
        if (slash <= 0) return entry
        val pkg = entry.substring(0, slash)
        val cls = entry.substring(slash + 1)
        return if (cls.startsWith(".")) "$pkg/$pkg$cls" else entry
    }

    private fun same(a: String, b: String) = normalize(a) == normalize(b)

    private const val SEPARATOR = ":"
    private const val TAG = "IslandKeeper"
}
