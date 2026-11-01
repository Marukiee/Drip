package nl.markmaaktmedia.drip.sources

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import kotlin.math.roundToInt

internal const val TAG = "DripSources"

/** Runs [block] and logs instead of throwing: a missing service or permission must never take the island down. */
internal inline fun <T> safely(what: String, block: () -> T): T? = try {
    block()
} catch (e: Exception) {
    Log.w(TAG, "$what failed", e)
    null
}

/**
 * Sends [this] with Drip's own right to start an activity. Since Android 14 a PendingIntent no longer gets that by
 * itself, and a tap on the island would quietly do nothing.
 */
internal fun PendingIntent.sendFrom(context: Context): Boolean = safely("send ${creatorPackage}") {
    val options = if (Build.VERSION.SDK_INT >= 34) {
        val mode = if (Build.VERSION.SDK_INT >= 36) {
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
        } else {
            @Suppress("DEPRECATION")
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        }
        ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(mode).toBundle()
    } else {
        null
    }
    send(context, 0, null, null, null, null, options)
    true
} ?: false

internal fun Context.launchApp(packageName: String) {
    safely("launch $packageName") {
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

internal fun Context.listen(receiver: BroadcastReceiver, exported: Boolean, vararg actions: String): Intent? {
    val filter = IntentFilter().apply { actions.forEach(::addAction) }
    val flags = if (exported) ContextCompat.RECEIVER_EXPORTED else ContextCompat.RECEIVER_NOT_EXPORTED
    return ContextCompat.registerReceiver(this, receiver, filter, flags)
}

internal fun Context.unlisten(receiver: BroadcastReceiver) {
    safely("unregister receiver") { unregisterReceiver(receiver) }
}

internal fun Bitmap.fitWithin(max: Int): Bitmap {
    val big = maxOf(width, height)
    if (big <= max) return this
    val source = if (config == Bitmap.Config.HARDWARE) copy(Bitmap.Config.ARGB_8888, false) ?: return this else this
    val s = max.toFloat() / big
    return source.scale((width * s).roundToInt().coerceAtLeast(1), (height * s).roundToInt().coerceAtLeast(1))
}

internal fun Drawable.toBitmapWithin(max: Int): Bitmap {
    if (this is BitmapDrawable && bitmap != null) return bitmap.fitWithin(max)
    val w = intrinsicWidth.takeIf { it > 0 } ?: max
    val h = intrinsicHeight.takeIf { it > 0 } ?: max
    val s = minOf(1f, max.toFloat() / maxOf(w, h))
    return toBitmap((w * s).roundToInt().coerceAtLeast(1), (h * s).roundToInt().coerceAtLeast(1))
}

internal fun Icon.toBitmapWithin(context: Context, max: Int): Bitmap? =
    safely("load icon") { loadDrawable(context)?.toBitmapWithin(max) }

/**
 * Labels and icons of other apps. A download or a ride updates its notification often, and asking the package manager
 * for the same icon every time is wasted work on the main thread.
 */
internal class Apps(private val context: Context) {
    private val pm = context.packageManager
    private val labels = HashMap<String, String>()
    private val icons = lru<Bitmap?>(32)
    private val glyphs = lru<Bitmap?>(64)

    fun label(packageName: String): String = labels.getOrPut(packageName) {
        safely("label $packageName") {
            @Suppress("DEPRECATION")
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } ?: packageName
    }

    fun icon(packageName: String): Bitmap? {
        if (icons.containsKey(packageName)) return icons[packageName]
        val icon = safely("icon $packageName") { pm.getApplicationIcon(packageName).toBitmapWithin(APP_ICON_PX) }
        icons[packageName] = icon
        return icon
    }

    /** The small icon of a button. Resource icons are cached by their id, bitmaps are cheap to read again. */
    fun glyph(icon: Icon?): Bitmap? {
        icon ?: return null
        if (icon.type != Icon.TYPE_RESOURCE) return icon.toBitmapWithin(context, GLYPH_PX)
        val key = safely("glyph key") { "${icon.resPackage}/${icon.resId}" } ?: return null
        if (glyphs.containsKey(key)) return glyphs[key]
        return icon.toBitmapWithin(context, GLYPH_PX).also { glyphs[key] = it }
    }

    private fun <V> lru(limit: Int) = object : LinkedHashMap<String, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>?) = size > limit
    }

    private companion object {
        const val APP_ICON_PX = 144
        const val GLYPH_PX = 72
    }
}
