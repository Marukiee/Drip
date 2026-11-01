package nl.markmaaktmedia.drip.island

import android.app.PendingIntent
import android.graphics.Bitmap
import androidx.annotation.DrawableRes

/**
 * What the island can show. The sources (media sessions, notifications, the battery, the torch) write these into
 * [IslandHub]; the overlay only reads them. Nothing here holds a View or a Context, so a value can be made up in a test or
 * by the debug demo just as well as by the real source.
 */

/** Something to press. [run] does the work, on the main thread. */
data class IslandAction(
    val title: String,
    val kind: ActionKind = ActionKind.OTHER,
    /** An icon of the app's own, used when [kind] has no icon of Drip's. */
    val icon: Bitmap? = null,
    /** For an action that takes typed text (a reply): sends it. [run] then does nothing. */
    val reply: ((CharSequence) -> Unit)? = null,
    val run: () -> Unit,
) {
    // Two actions are the same when they look the same: the lambda is new every time a notification is read again.
    override fun equals(other: Any?) = other is IslandAction && other.title == title && other.kind == kind
    override fun hashCode() = title.hashCode() * 31 + kind.hashCode()
}

enum class ActionKind { ANSWER, DECLINE, HANG_UP, SPEAKER, MUTE, REPLY, MARK_READ, OPEN, OTHER }

/** The commands of the player that is playing. Implemented by the media source over a MediaController. */
interface MediaCommands {
    fun playPause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun cycleRepeat()
    fun toggleShuffle()
    /** The heart: only there when the player offers a like or favourite action of its own. */
    fun toggleLike()
    fun openPlayer()
}

enum class RepeatMode { OFF, ALL, ONE }

data class NowPlaying(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val art: Bitmap? = null,
    val durationMs: Long = 0,
    /** The position at [positionAtElapsed] (SystemClock.elapsedRealtime). The card moves it on with [speed] while playing. */
    val positionMs: Long = 0,
    val positionAtElapsed: Long = 0,
    val speed: Float = 1f,
    val playing: Boolean = false,
    val canSkipNext: Boolean = true,
    val canSkipPrevious: Boolean = true,
    val canSeek: Boolean = false,
    /** Null when the player has no repeat, shuffle or like. Then the button is left out. */
    val repeat: RepeatMode? = null,
    val shuffle: Boolean? = null,
    val liked: Boolean? = null,
    /** The title of what plays next, when the player tells. */
    val next: String? = null,
    val commands: MediaCommands,
) {
    override fun equals(other: Any?): Boolean = other is NowPlaying && packageName == other.packageName &&
        title == other.title && artist == other.artist && album == other.album && art === other.art &&
        durationMs == other.durationMs && positionMs == other.positionMs && positionAtElapsed == other.positionAtElapsed &&
        speed == other.speed && playing == other.playing && canSkipNext == other.canSkipNext &&
        canSkipPrevious == other.canSkipPrevious && canSeek == other.canSeek && repeat == other.repeat &&
        shuffle == other.shuffle && liked == other.liked

    override fun hashCode(): Int = (packageName.hashCode() * 31 + title.hashCode()) * 31 + playing.hashCode()
}

data class CallInfo(
    /** The key of the notification, so a newer one for the same call replaces this. */
    val key: String,
    val packageName: String,
    val appLabel: String,
    val appIcon: Bitmap? = null,
    val caller: String,
    val callerPhoto: Bitmap? = null,
    /** "Incoming call", "Ongoing call", "On hold": the line the phone app writes under the name. */
    val status: String? = null,
    val incoming: Boolean,
    /** When the call was picked up, in System.currentTimeMillis. Null while it rings. */
    val startedAt: Long? = null,
    val actions: List<IslandAction> = emptyList(),
    val open: (() -> Unit)? = null,
)

enum class LiveKind { TIMER, STOPWATCH, NAVIGATION, DELIVERY, RIDE, RECORDING, DOWNLOAD, INSTALL, CALENDAR, SPORTS, GENERIC }

/** A stretch of a Live Update progress bar (Android 16 ProgressStyle): its share of the bar and its colour. */
data class ProgressSegment(val length: Float, val color: Int? = null)

/** A marked spot on such a bar, like a stop on a route, from 0 to 1. */
data class ProgressPoint(val position: Float, val color: Int? = null)

/** A notification that keeps going: a timer, a ride, a delivery, a download. */
data class LiveInfo(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val appIcon: Bitmap? = null,
    /** The large picture of the notification: the turn arrow of a route, the thumbnail of a download. */
    val picture: Bitmap? = null,
    /** The colour the app gave its notification, if any. */
    val accent: Int? = null,
    val kind: LiveKind = LiveKind.GENERIC,
    val title: String,
    val text: String? = null,
    /** 0 to 1, or null when there is no bar. */
    val progress: Float? = null,
    val indeterminate: Boolean = false,
    /** A running clock: the moment it counts from or to, in System.currentTimeMillis. */
    val chronometerBase: Long? = null,
    val countDown: Boolean = false,
    /** A short text for the closed island when there is no clock, like "3 min" or "12%". */
    val shortText: String? = null,
    /** The stretches of the bar, when the app split it up (a route, a delivery). Lengths add up to 1. */
    val segments: List<ProgressSegment> = emptyList(),
    val points: List<ProgressPoint> = emptyList(),
    /** The little picture that rides along the bar (the courier, the car). */
    val tracker: Bitmap? = null,
    /** Arrival time of a route, or another second line for the opened card, like "Aankomst 14:32". */
    val detail: String? = null,
    val actions: List<IslandAction> = emptyList(),
    val open: (() -> Unit)? = null,
)

enum class PlugType { NONE, AC, USB, WIRELESS, DOCK }

data class BatteryInfo(
    val level: Int = 100,
    val charging: Boolean = false,
    val plug: PlugType = PlugType.NONE,
    /** Android's own estimate, when it has one. */
    val timeToFullMs: Long? = null,
    val powerSave: Boolean = false,
    /** Faster than plain charging, as far as Android lets it be read. */
    val fast: Boolean = false,
    /** Charging held at a limit to spare the battery (Battery Care on an Xperia), in percent. */
    val limit: Int? = null,
)

data class TorchInfo(
    val available: Boolean = false,
    val on: Boolean = false,
    /** 1 based. [maxStrength] is 1 on phones without dimming. */
    val strength: Int = 1,
    val maxStrength: Int = 1,
)

interface TorchCommands {
    fun setOn(on: Boolean)
    fun setStrength(level: Int)
}

/** The green and orange dots: a camera or a microphone in use by any app. */
/** The dots for a camera, a microphone or location in use, and, when it can be found out, which app uses it. */
data class PrivacyInfo(
    val camera: Boolean = false,
    val microphone: Boolean = false,
    val location: Boolean = false,
    val cameraApp: String? = null,
    val microphoneApp: String? = null,
    val locationApp: String? = null,
) {
    val any get() = camera || microphone || location
}

enum class AlertKind {
    NOTIFICATION, CHARGING, UNPLUGGED, BATTERY_LOW, BATTERY_FULL, BATTERY_SAVER, DND, ROTATION, RINGER, VOLUME,
    BRIGHTNESS, HEADPHONES, ALARM, UNLOCK, OTHER,
}

/**
 * A short announcement: it opens the island a little, stays for [durationMs] and closes again. An alert with the [id] of
 * one already showing replaces it and starts its time again, which is how a volume bar follows the buttons.
 */
data class Alert(
    val id: String,
    val kind: AlertKind,
    val title: String,
    val text: String? = null,
    /** A Material Symbol of Drip's. */
    @DrawableRes val iconRes: Int? = null,
    /** An app icon or a contact photo, shown round. Wins over [iconRes]. */
    val image: Bitmap? = null,
    val packageName: String? = null,
    val accent: Int? = null,
    /** 0 to 1 for a bar (volume, brightness, battery), or null. */
    val level: Float? = null,
    val durationMs: Long? = null,
    val actions: List<IslandAction> = emptyList(),
    val open: (() -> Unit)? = null,
)
