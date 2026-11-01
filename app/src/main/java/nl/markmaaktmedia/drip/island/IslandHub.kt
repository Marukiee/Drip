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
    /**
     * The app in front, not the home screen. Android does not tell other apps which app holds the camera or the
     * microphone, but it is nearly always the one in front when it starts, so the privacy card names that one.
     */
    val foregroundApp = MutableStateFlow<String?>(null)
    /** Whether the lyrics are open under the music card. Stays as it was left, so they are there the next time too. */
    val lyricsOpen = MutableStateFlow(false)
    /** The lyrics of the song that plays, while [lyricsOpen]. */
    val lyrics = MutableStateFlow<LyricsState>(LyricsState.Off)
    /** Tries again after the lyrics could not be fetched. Set by the lyrics source. */
    @Volatile var retryLyrics: (() -> Unit)? = null
    /** Looks the lyrics up online, also when reading the song's file was on offer. Set by the lyrics source. */
    @Volatile var lookLyricsOnline: (() -> Unit)? = null
    /** The message whose card should open straight into typing a reply (its reply button in the notch was pressed). */
    val replyTo = MutableStateFlow<String?>(null)

    @Volatile var torchCommands: TorchCommands? = null
    /** Gives the island the keyboard while a reply is typed, and takes it away again. Set by the island service. */
    @Volatile var setTyping: ((Boolean) -> Unit)? = null
    /** Opens Android's panel for where sound plays. Set by the island service. */
    @Volatile var openOutput: (() -> Unit)? = null
    /** Sets the media volume, 0 to 1, without the system's own volume panel. */
    @Volatile var setMusicVolume: ((Float) -> Unit)? = null
    /** One step up or down (AudioManager.ADJUST_RAISE or ADJUST_LOWER) of a stream, without the system's panel. */
    @Volatile var nudgeVolume: ((stream: Int, direction: Int) -> Unit)? = null

    private val _alerts = MutableSharedFlow<Alert>(extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val alerts: SharedFlow<Alert> = _alerts.asSharedFlow()

    fun post(alert: Alert) {
        _alerts.tryEmit(alert)
    }

    private val _demos = MutableSharedFlow<Demo>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** Asks the island to act something out, for the try buttons in the app and the debug broadcast. */
    val demos: SharedFlow<Demo> = _demos.asSharedFlow()

    fun demo(demo: Demo) {
        _demos.tryEmit(demo)
    }

    /** True while the notification listener is connected, so the app can tell whether it still needs the permission. */
    val listenerConnected = MutableStateFlow(false)
    /** True while the accessibility service, and so the island, is running. */
    val islandRunning = MutableStateFlow(false)
}

/** What the island can act out without a real song, call or charger. */
enum class Demo {
    MUSIC, LYRICS, FILE_LYRICS, CALL, INCOMING, CHARGING, TIMER, NAVIGATION, DOWNLOAD, DELIVERY, CALENDAR, INSTALL, ALERT, REPLY, VOLUME,
    TORCH, EARBUDS, UNLOCK, PRIVACY, SECOND, EXPAND, COLLAPSE, CLEAR,
}
