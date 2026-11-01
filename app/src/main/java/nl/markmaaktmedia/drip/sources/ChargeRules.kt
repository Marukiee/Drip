package nl.markmaaktmedia.drip.sources

import android.os.BatteryManager
import kotlin.math.abs
import kotlin.math.roundToInt

/** What [ChargeTraits] makes of one battery reading. */
data class ChargeReading(val fast: Boolean, val limit: Int?)

/**
 * Fast charging and a charge limit (Battery Care on an Xperia, the 80% limit on a Pixel), read from the battery
 * broadcast. Android has a public answer for neither, so this is a guess from what the broadcast does carry, with a
 * little memory per time on the charger: the current dips as the battery fills and a held battery tops itself up now
 * and then, and the island should not flicker with either.
 */
class ChargeTraits {
    private var fastSeen = false
    private var heldAt: Int? = null

    /**
     * [maxCurrentUa] and [maxVoltageUv] are the hidden extras of the broadcast (0 when missing), [currentNow] the
     * current of BatteryManager, in whatever unit and sign the phone uses, or null.
     */
    fun update(plugged: Boolean, status: Int, level: Int, maxCurrentUa: Int, maxVoltageUv: Int, currentNow: Int?): ChargeReading {
        if (!plugged) {
            reset()
            return ChargeReading(fast = false, limit = null)
        }
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING
        if (charging && (plugWatts(maxCurrentUa, maxVoltageUv) >= FAST_WATTS || fastCurrent(currentNow, level))) fastSeen = true

        // Discharging while plugged in is a weak charger under load, not a limit.
        val held = level < 100 &&
            (status == BatteryManager.BATTERY_STATUS_NOT_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
        val before = heldAt
        heldAt = when {
            held -> roundedLimit(level)
            // Topping up to the limit again after it slipped a little: still the same limit.
            before != null && charging && level < 100 && level >= before - TOP_UP_SLACK -> before
            else -> null
        }
        return ChargeReading(fast = fastSeen && charging && heldAt == null, limit = heldAt)
    }

    fun reset() {
        fastSeen = false
        heldAt = null
    }

    companion object {
        const val FAST_WATTS = 15.0
        /** Into the battery itself: an ordinary 10 W charger stays well under this once losses and the phone are paid. */
        const val FAST_MILLIAMPS = 2_500
        private const val TOP_UP_SLACK = 3

        /** Watts the charger offers. A missing voltage is 5 V, as in SystemUI. */
        fun plugWatts(maxCurrentUa: Int, maxVoltageUv: Int): Double {
            if (maxCurrentUa <= 0) return 0.0
            val volts = if (maxVoltageUv > 0) maxVoltageUv / 1_000_000.0 else 5.0
            return maxCurrentUa / 1_000_000.0 * volts
        }

        /**
         * The current in milliamps, whatever the phone reports: most give microamps, some milliamps, and some count
         * charging as negative. No phone charges with 20 A, so anything bigger is microamps.
         */
        fun milliamps(raw: Int): Int {
            val a = abs(raw.toLong())
            return (if (a > 20_000) a / 1_000 else a).toInt()
        }

        /** Near full every charger trickles, and a few microamps there would read as many milliamps, so only below 90%. */
        private fun fastCurrent(currentNow: Int?, level: Int): Boolean =
            currentNow != null && level <= 90 && milliamps(currentNow) >= FAST_MILLIAMPS

        /** The level a limit is held at, put on the round number it hovers around (79 or 81 is 80). */
        fun roundedLimit(level: Int): Int {
            val round = (level / 5.0).roundToInt() * 5
            return if (abs(round - level) <= 1) round else level
        }
    }
}
