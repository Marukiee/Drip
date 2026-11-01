package nl.markmaaktmedia.drip.sources

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import androidx.annotation.DrawableRes
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.island.Alert
import nl.markmaaktmedia.drip.island.AlertKind

/** In-ear models by the words their makers put in the name, so they get the earbuds icon instead of headphones. */
fun looksLikeEarbuds(name: String): Boolean {
    val n = name.lowercase()
    return EARBUD_WORDS.any { it in n } || Regex("""\bwf-""").containsMatchIn(n)
}

private val EARBUD_WORDS = listOf("bud", "pods", "earbud", "in-ear", "in ear", "earfun", "liberty", "elite", "linkbuds")

/**
 * Bluetooth headphones and earbuds coming in. The name comes from the audio device, which needs no permission; with
 * Nearby devices allowed the announcement also gets their battery, from the hidden battery broadcast or, where it
 * answers, BluetoothDevice.getBatteryLevel. That level arrives a few seconds after the connection, so the announcement
 * is posted again with the same id once it does.
 *
 * One level per device is all Android hands out. Left, right and the case are only told to the maker's own app, over
 * its own protocol.
 */
internal class EarbudsPart(env: SourceEnv) : Part(env) {
    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)
    private val bluetooth = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val known = HashSet<Int>()
    private var pending: Pending? = null

    /** The last announcement, still open to a battery level for [BATTERY_WINDOW_MS]. */
    private class Pending(val address: String?, val name: String, @param:DrawableRes val icon: Int, val at: Long, var level: Int?)

    private val devices = object : AudioDeviceCallback() {
        // Called at once with every device already there; those are in [known] and stay quiet.
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            addedDevices?.forEach { d -> if (known.add(d.id) && d.isSink && d.type in BLUETOOTH_TYPES) onConnected(d) }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            removedDevices?.forEach { known.remove(it.id) }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            safely("bluetooth battery") {
                val level = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1)
                val device = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                if (device != null && level in 0..100) onBattery(device, level)
            }
        }
    }

    // Asks the device itself a little later, for phones where the broadcast came before the announcement.
    private val askAgain = Runnable { pending?.let { p -> if (p.level == null) batteryOf(p)?.let { onLevel(p, it) } } }

    override fun start() {
        val am = audio ?: return
        known.clear()
        safely("audio devices") { am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).forEach { known += it.id } }
        safely("earbuds callback") { am.registerAudioDeviceCallback(devices, env.handler) }
        // Exported because the action is hidden. It is a protected broadcast, and it only reaches apps with Nearby devices.
        safely("earbuds receiver") { context.listen(receiver, true, ACTION_BATTERY_LEVEL_CHANGED) }
    }

    override fun stop() {
        safely("earbuds callback") { audio?.unregisterAudioDeviceCallback(devices) }
        context.unlisten(receiver)
        env.handler.removeCallbacks(askAgain)
        known.clear()
        pending = null
    }

    private fun onConnected(d: AudioDeviceInfo) {
        val s = settings ?: return
        if (!s.alertHeadphones) return
        val now = SystemClock.elapsedRealtime()
        val address = d.address?.takeIf { it.isNotBlank() }
        // One pair can show up as more than one audio device (A2DP and LE Audio).
        pending?.let { if (address != null && it.address == address && now - it.at < BATTERY_WINDOW_MS) return }
        val name = d.productName?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: env.text(R.string.src_headphones)
        val icon = when {
            d.type == AudioDeviceInfo.TYPE_BLE_SPEAKER -> R.drawable.sym_speaker
            looksLikeEarbuds(name) -> R.drawable.sym_earbuds
            else -> R.drawable.sym_headphones
        }
        val p = Pending(address, name, icon, now, null)
        pending = p
        p.level = batteryOf(p)
        post(p)
        env.handler.removeCallbacks(askAgain)
        if (p.level == null && allowed()) {
            env.handler.postDelayed(askAgain, 2_000)
            env.handler.postDelayed(askAgain, 5_000)
        }
    }

    private fun onBattery(device: BluetoothDevice, level: Int) {
        val p = pending ?: return
        val same = safely("bluetooth device") {
            (p.address != null && device.address.equals(p.address, ignoreCase = true)) || nameOf(device) == p.name
        } ?: false
        if (same) onLevel(p, level)
    }

    private fun onLevel(p: Pending, level: Int) {
        if (SystemClock.elapsedRealtime() - p.at > BATTERY_WINDOW_MS || p.level == level) return
        p.level = level
        post(p)
    }

    private fun post(p: Pending) {
        val s = settings ?: return
        val level = p.level
        hub.post(
            Alert(
                id = "headphones",
                kind = AlertKind.HEADPHONES,
                title = p.name,
                text = if (level != null) env.text(R.string.src2_connected_battery, level) else env.text(R.string.src_connected),
                iconRes = p.icon,
                level = level?.let { it / 100f },
                durationMs = s.alertDurationMs,
            ),
        )
    }

    private fun allowed() = context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** The battery by asking the paired device. Hidden API, so it may be missing or refuse; null then. */
    private fun batteryOf(p: Pending): Int? {
        if (!allowed()) return null
        val adapter = bluetooth ?: return null
        return safely("bluetooth battery level") {
            val bonded = adapter.bondedDevices.orEmpty()
            val device = bonded.firstOrNull { p.address != null && it.address.equals(p.address, ignoreCase = true) }
                ?: bonded.firstOrNull { nameOf(it) == p.name }
                ?: return@safely null
            (BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(device) as? Int)?.takeIf { it in 0..100 }
        }
    }

    private fun nameOf(device: BluetoothDevice): String? = safely("bluetooth name") {
        if (!allowed()) null else (device.alias ?: device.name)?.trim()
    }

    private companion object {
        const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
        const val BATTERY_WINDOW_MS = 10_000L
        val BLUETOOTH_TYPES = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_HEARING_AID,
        )
    }
}
