package nl.markmaaktmedia.drip.sources

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.LocationManager
import kotlinx.coroutines.flow.update
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.island.Alert
import nl.markmaaktmedia.drip.island.AlertKind

/** A lock that opens on the island when the phone is unlocked. */
internal class UnlockPart(env: SourceEnv) : Part(env) {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            if (settings?.showUnlock != true) return
            // Shorter than other announcements: it only confirms what the person just did.
            hub.post(
                Alert(
                    id = "unlock", kind = AlertKind.UNLOCK, title = env.text(R.string.src2_unlocked),
                    iconRes = R.drawable.sym_lock_open, durationMs = 1_200,
                ),
            )
        }
    }

    override fun start() {
        safely("unlock receiver") { context.listen(receiver, false, Intent.ACTION_USER_PRESENT) }
    }

    override fun stop() {
        context.unlisten(receiver)
    }
}

/**
 * The location dot: the GPS of the phone is running for some app. Android only tells this to an app with precise
 * location, and only whether GPS runs, not which app asked; a location from Wi-Fi or the network shows no dot.
 */
internal class LocationPart(env: SourceEnv) : Part(env) {
    private val locations: LocationManager? = context.getSystemService(LocationManager::class.java)
    private var registered = false
    private var running = false

    private val callback = object : GnssStatus.Callback() {
        override fun onStarted() {
            running = true
            publish()
        }

        override fun onStopped() {
            running = false
            publish()
        }
    }

    override fun start() = ensure()

    override fun stop() {
        if (registered) safely("gnss callback") { locations?.unregisterGnssStatusCallback(callback) }
        registered = false
        running = false
        hub.privacy.update { it.copy(location = false) }
    }

    override fun onSettings() = ensure()

    /** Registers only while the dot is wanted and allowed: without precise location the call throws. */
    private fun ensure() {
        val lm = locations ?: return
        val wanted = settings?.showPrivacy == true &&
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (wanted && !registered) {
            safely("gnss callback") {
                registered = lm.registerGnssStatusCallback(callback, env.handler)
            }
        } else if (!wanted && registered) {
            safely("gnss callback") { lm.unregisterGnssStatusCallback(callback) }
            registered = false
            running = false
        }
        publish()
    }

    private fun publish() {
        val on = registered && running && settings?.showPrivacy == true
        hub.privacy.update { it.copy(location = on, locationApp = if (!on) null else if (it.location) it.locationApp else hub.foregroundApp.value) }
    }
}
