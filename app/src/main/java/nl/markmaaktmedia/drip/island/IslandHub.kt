package nl.markmaaktmedia.drip.island

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Where the sources leave what is going on and where the island picks it up.
 *
 * Plain state flows, written from any thread. The sources do not know the island exists and the island does not know
 * where a value came from, which is what lets the debug demo stand in for a real phone call.
 */
class IslandHub {
    val nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val call = MutableStateFlow<CallInfo?>(null)
    /** Newest first. */
    val live = MutableStateFlow<List<LiveInfo>>(emptyList())
    val battery = MutableStateFlow(BatteryInfo())
    val torch = MutableStateFlow(TorchInfo())
    val privacy = MutableStateFlow(PrivacyInfo())
    /** Media volume, 0 to 1. */
    val musicVolume = MutableStateFlow(0f)

    @Volatile var torchCommands: TorchCommands? = null
    /** Sets the media volume, 0 to 1, without the system's own volume panel. */
    @Volatile var setMusicVolume: ((Float) -> Unit)? = null

    private val _alerts = MutableSharedFlow<Alert>(extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val alerts: SharedFlow<Alert> = _alerts.asSharedFlow()

    fun post(alert: Alert) {
        _alerts.tryEmit(alert)
    }

    /** True while the notification listener is connected, so the app can tell whether it still needs the permission. */
    val listenerConnected = MutableStateFlow(false)
    /** True while the accessibility service, and so the island, is running. */
    val islandRunning = MutableStateFlow(false)
}
