package nl.markmaaktmedia.drip.sources

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.PowerManager
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.island.AlertKind
import nl.markmaaktmedia.drip.island.BatteryInfo
import nl.markmaaktmedia.drip.island.PlugType

/** The charger, the battery level and Battery Saver. */
internal class BatteryPart(env: SourceEnv) : Part(env) {
    private val battery: BatteryManager? = context.getSystemService(BatteryManager::class.java)
    private val power: PowerManager? = context.getSystemService(PowerManager::class.java)
    private var watch = BatteryWatch()
    private val saver = ChangeWatch<Boolean>()
    private val traits = ChargeTraits()
    private var info: BatteryInfo? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            safely("battery") {
                when (intent.action) {
                    Intent.ACTION_BATTERY_CHANGED -> read(intent)
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> readSaver()
                }
            }
        }
    }

    override fun start() {
        watch = BatteryWatch()
        traits.reset()
        saver.reset()
        saver.update(powerSave())
        // The battery broadcast is sticky: registering hands back the current state, which only seeds the watch.
        context.listen(receiver, false, Intent.ACTION_BATTERY_CHANGED, PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            ?.let(::read)
    }

    override fun stop() {
        context.unlisten(receiver)
        info = null
        hub.battery.value = BatteryInfo()
    }

    override fun onSettings() = publish()

    private fun read(intent: Intent) {
        val raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (raw < 0 || scale <= 0) return
        val level = (raw * 100 / scale).coerceIn(0, 100)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            (status == BatteryManager.BATTERY_STATUS_FULL && plugged != 0)
        val plug = plugTypeOf(plugged)
        val toFull = if (charging) safely("charge time") { battery?.computeChargeTimeRemaining() }?.takeIf { it > 0 } else null
        val reading = traits.update(
            plugged = plugged != 0, status = status, level = level,
            maxCurrentUa = intent.getIntExtra(EXTRA_MAX_CURRENT, 0), maxVoltageUv = intent.getIntExtra(EXTRA_MAX_VOLTAGE, 0),
            currentNow = safely("current") { battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) }
                ?.takeIf { it != Int.MIN_VALUE && it != 0 },
        )
        info = BatteryInfo(
            level = level, charging = charging, plug = plug, timeToFullMs = toFull, powerSave = powerSave(),
            fast = reading.fast, limit = reading.limit,
        )
        publish()
        // Plugged counts, not charging: a phone that holds at 80% to spare its battery is still on the charger.
        watch.update(level, plugged != 0).forEach { tell(it, level, plug) }
    }

    private fun readSaver() {
        val on = powerSave()
        info = info?.copy(powerSave = on)
        publish()
        if (!saver.update(on) || settings?.alertBatterySaver != true) return
        env.announce(
            "battery_saver", AlertKind.BATTERY_SAVER,
            env.text(if (on) R.string.src_saver_on else R.string.src_saver_off),
            icon = R.drawable.sym_energy_savings_leaf,
        )
    }

    private fun tell(event: BatteryEvent, level: Int, plug: PlugType) {
        val s = settings ?: return
        val share = level / 100f
        when (event) {
            BatteryEvent.PLUGGED_IN -> if (s.showCharging) {
                env.announce(
                    "charging", AlertKind.CHARGING, env.text(R.string.src_charging),
                    env.text(R.string.src_charging_text, env.text(plugLabel(plug)), level),
                    if (plug == PlugType.USB) R.drawable.sym_usb else R.drawable.sym_bolt_filled, share,
                )
            }
            BatteryEvent.UNPLUGGED -> if (s.showCharging) {
                env.announce(
                    "charging", AlertKind.UNPLUGGED, env.text(R.string.src_unplugged),
                    env.text(R.string.src_percent, level), R.drawable.sym_bolt, share,
                )
            }
            BatteryEvent.LOW -> if (s.alertBatteryLow) {
                env.announce(
                    "battery_low", AlertKind.BATTERY_LOW, env.text(R.string.src_battery_low),
                    env.text(R.string.src_battery_left, level), R.drawable.sym_battery_alert, share,
                )
            }
            BatteryEvent.FULL -> if (s.alertBatteryFull) {
                env.announce(
                    "battery_full", AlertKind.BATTERY_FULL, env.text(R.string.src_battery_full),
                    icon = R.drawable.sym_battery_full, level = 1f,
                )
            }
        }
    }

    private fun publish() {
        val s = settings ?: return
        val i = info ?: return
        hub.battery.value = if (s.showCharging) i else i.copy(charging = false, plug = PlugType.NONE, timeToFullMs = null, fast = false, limit = null)
    }

    private fun powerSave() = safely("power save") { power?.isPowerSaveMode } ?: false

    private fun plugLabel(plug: PlugType) = when (plug) {
        PlugType.USB -> R.string.src_plug_usb
        PlugType.WIRELESS -> R.string.src_plug_wireless
        PlugType.DOCK -> R.string.src_plug_dock
        PlugType.AC, PlugType.NONE -> R.string.src_plug_ac
    }

    private companion object {
        // Hidden extras of the battery broadcast, the same SystemUI reads for "Charging rapidly".
        const val EXTRA_MAX_CURRENT = "max_charging_current"
        const val EXTRA_MAX_VOLTAGE = "max_charging_voltage"
    }
}
