package nl.markmaaktmedia.drip.sources

import nl.markmaaktmedia.drip.island.PlugType
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Remembers the last value of something. Sticky broadcasts and most Android callbacks report the current state the
 * moment they are registered, so the first value only seeds and is never a change.
 */
class ChangeWatch<T> {
    private var seeded = false
    private var last: T? = null

    /** True when [value] differs from the value before it. */
    fun update(value: T): Boolean {
        if (!seeded) {
            seeded = true
            last = value
            return false
        }
        if (value == last) return false
        last = value
        return true
    }

    fun reset() {
        seeded = false
        last = null
    }
}

enum class BatteryEvent { PLUGGED_IN, UNPLUGGED, LOW, FULL }

/**
 * Turns battery readings into the moments worth saying something about. Low warns once per discharge at each of
 * [lowLevels], full once per charge. The first reading only seeds, so starting the service at 10% stays quiet about
 * 15% but still warns at 5%.
 */
class BatteryWatch(private val lowLevels: List<Int> = listOf(15, 5)) {
    private var seeded = false
    private var plugged = false
    private val warned = HashSet<Int>()
    private var fullShown = false

    fun update(level: Int, plugged: Boolean): List<BatteryEvent> {
        if (!seeded) {
            seeded = true
            this.plugged = plugged
            arm(level)
            fullShown = plugged && level >= 100
            return emptyList()
        }
        val events = ArrayList<BatteryEvent>(2)
        if (plugged != this.plugged) {
            this.plugged = plugged
            if (plugged) {
                // Plugged in when already full: nothing to finish, so no "fully charged" later.
                fullShown = level >= 100
                events += BatteryEvent.PLUGGED_IN
            } else {
                // A new discharge. Levels already passed stay quiet, so unplugging at 12% does not warn at once.
                arm(level)
                events += BatteryEvent.UNPLUGGED
            }
        }
        if (plugged) {
            if (level >= 100 && !fullShown) {
                fullShown = true
                events += BatteryEvent.FULL
            }
        } else {
            val crossed = lowLevels.filter { level <= it && it !in warned }
            if (crossed.isNotEmpty()) {
                warned += crossed
                events += BatteryEvent.LOW
            }
        }
        return events
    }

    private fun arm(level: Int) {
        warned.clear()
        lowLevels.filterTo(warned) { level <= it }
    }
}

/** Tells the volume broadcasts that follow Drip's own slider apart from the ones the buttons cause. */
class EchoWindow(private val windowMs: Long) {
    private var ownAt: Long? = null

    fun markOwn(now: Long) {
        ownAt = now
    }

    fun isEcho(now: Long): Boolean {
        val at = ownAt ?: return false
        return now - at in 0..windowMs
    }
}

/**
 * What was last seen of each notification. Chat apps post every new message under the key of the conversation, so an
 * update alerts again when it says something new, unless the app asked to alert only once.
 */
class NotificationMemory {
    private val seen = HashMap<String, String>()

    /** For the notifications that were already there when the listener connected: known, never an alert. */
    fun seed(key: String, fingerprint: String) {
        seen[key] = fingerprint
    }

    /** Records [fingerprint] and tells whether this post may open the island. */
    fun isFresh(key: String, fingerprint: String, onlyAlertOnce: Boolean): Boolean {
        val before = seen.put(key, fingerprint) ?: return true
        return !onlyAlertOnce && before != fingerprint
    }

    fun forget(key: String) {
        seen.remove(key)
    }

    fun clear() = seen.clear()
}

private const val PLUGGED_AC = 1
private const val PLUGGED_USB = 2
private const val PLUGGED_WIRELESS = 4
private const val PLUGGED_DOCK = 8

/** BatteryManager.EXTRA_PLUGGED is a set of bits. */
internal fun plugTypeOf(plugged: Int): PlugType = when {
    plugged == 0 -> PlugType.NONE
    plugged and PLUGGED_WIRELESS != 0 -> PlugType.WIRELESS
    plugged and PLUGGED_DOCK != 0 -> PlugType.DOCK
    plugged and PLUGGED_USB != 0 -> PlugType.USB
    plugged and PLUGGED_AC != 0 -> PlugType.AC
    else -> PlugType.AC
}

internal fun volumeLevel(index: Int, min: Int, max: Int, muted: Boolean): Float {
    if (muted || max <= min) return 0f
    return ((index - min).toFloat() / (max - min)).coerceIn(0f, 1f)
}

internal fun volumeIndex(level: Float, min: Int, max: Int): Int =
    (min + level.coerceIn(0f, 1f) * (max - min)).roundToInt().coerceIn(min, max)

/**
 * Android's brightness slider is not linear: it follows the HLG curve of the system's BrightnessUtils. Reading the
 * raw setting through the same curve makes the bar stand where the system's slider stands.
 */
internal fun brightnessSliderLevel(value: Int, min: Int = 1, max: Int = 255): Float {
    if (max <= min) return 0f
    val norm = ((value - min).toFloat() / (max - min)).coerceIn(0f, 1f) * 12f
    val gamma = if (norm <= 1f) sqrt(norm) * 0.5f else 0.17883277f * ln(norm - 0.28466892f) + 0.55991073f
    return gamma.coerceIn(0f, 1f)
}
