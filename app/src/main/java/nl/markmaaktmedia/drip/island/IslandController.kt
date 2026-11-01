package nl.markmaaktmedia.drip.island

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import nl.markmaaktmedia.drip.data.IslandSettings

/** What the island is showing. One of these is drawn closed, opened or as a short announcement. */
sealed interface IslandContent {
    /** Stable per thing shown, so a song that moves on keeps its card and a different thing gets a new one. */
    val key: String

    data class Music(val playing: NowPlaying) : IslandContent {
        override val key get() = KEY

        companion object {
            const val KEY = "music"
        }
    }
    data class Call(val call: CallInfo) : IslandContent { override val key get() = "call:" + call.key }
    data class Live(val live: LiveInfo) : IslandContent { override val key get() = "live:" + live.key }
    data class Charging(val battery: BatteryInfo) : IslandContent { override val key get() = "charging" }
    data class Torch(val torch: TorchInfo) : IslandContent { override val key get() = "torch" }
    data class Notice(val alert: Alert) : IslandContent { override val key get() = "alert:" + alert.id }
    /** A camera, microphone or location in use: the icon closed, which app it is opened. */
    data class Privacy(val privacy: PrivacyInfo) : IslandContent { override val key get() = "privacy" }
}

/** Why the island is out of the way right now. Any one of them hides it. */
data class Visibility(
    val statusBarShown: Boolean = true,
    val landscape: Boolean = false,
    val locked: Boolean = false,
    val foregroundPackage: String? = null,
)

data class IslandUi(
    val stage: Stage = Stage.IDLE,
    val content: IslandContent? = null,
    /** The dots for a camera or microphone in use, drawn on whatever stage is showing. */
    val privacy: PrivacyInfo = PrivacyInfo(),
    /**
     * A second thing going on at the same time, shown as its own small drop next to the closed island. Also set while a
     * card or an announcement covers the island, when the drop waits pulled up into the edge.
     */
    val second: IslandContent? = null,
)

/**
 * Decides what the island shows: the most important thing going on, an announcement on top of it for a few seconds, or
 * the opened card someone asked for. The overlay draws what this says and sends taps back.
 *
 * Order of importance: a call, then a timer or other live update, then music that plays, the torch, charging, and music
 * that was paused a moment ago. A call that rings opens the island by itself, the way a phone call would.
 */
class IslandController(
    private val scope: CoroutineScope,
    private val hub: IslandHub,
    private val haptics: Haptics,
) {
    private val _ui = MutableStateFlow(IslandUi())
    val ui: StateFlow<IslandUi> = _ui.asStateFlow()

    private var settings = IslandSettings()
    private var visibility = Visibility()
    private var primary: IslandContent? = null
    private var second: IslandContent? = null
    private var privacy = PrivacyInfo()
    private var sources: Sources5? = null
    /** What was swapped to the front by a tap on the small drop: it stays the island while it lasts. */
    private var pinnedKey: String? = null

    private val queue = ArrayDeque<Alert>()
    private var alert: Alert? = null
    private var alertJob: Job? = null

    /** The key of the opened card, or null when the island is closed. */
    private var expandedKey: String? = null
    private var expandedFallback: IslandContent? = null
    private var expandJob: Job? = null

    /** A ringing call the person closed by hand stays closed. */
    private var dismissedRinging: String? = null

    /** When the music last played, so paused music lingers for a while and then gives way. */
    private var lastPlayingAt = 0L

    fun start(settingsFlow: kotlinx.coroutines.flow.Flow<IslandSettings>) {
        scope.launch {
            settingsFlow.collect {
                settings = it
                haptics.enabled = it.hapticsEnabled
                haptics.strength = it.hapticsStrength
                repick()
                refresh()
            }
        }
        scope.launch {
            combine(hub.call, hub.live, hub.nowPlaying, hub.torch, hub.battery) { call, live, music, torch, battery ->
                Sources5(call, live, music, torch, battery)
            }.collect { s ->
                val wasRinging = (primary as? IslandContent.Call)?.call?.incoming == true
                sources = s
                repick()
                val call = s.call
                if (call != null && call.incoming && settings.showCalls && dismissedRinging != call.key && !wasRinging) {
                    haptics.play(Haptics.Kind.CALL)
                    expandedKey = IslandContent.Call(call).key
                    expandJob?.cancel()
                }
                if (call == null) dismissedRinging = null
                // Picked up: the opened call card closes by itself after a while, like any other.
                if (wasRinging && call != null && !call.incoming && expandedKey == IslandContent.Call(call).key) poke()
                refresh()
            }
        }
        scope.launch {
            hub.privacy.collect {
                privacy = it
                repick()
                refresh()
            }
        }
        scope.launch {
            hub.alerts.collect { onAlert(it) }
        }
        scope.launch {
            // Opening or closing the lyrics stops or starts the card's own time again.
            hub.lyricsOpen.collect { poke() }
        }
        scope.launch {
            // Paused music gives way after a while even when nothing else changes.
            while (true) {
                delay(30_000)
                repick()
                refresh()
            }
        }
    }

    private data class Sources5(
        val call: CallInfo?,
        val live: List<LiveInfo>,
        val music: NowPlaying?,
        val torch: TorchInfo,
        val battery: BatteryInfo,
    )

    private fun repick() {
        val picked = pick(sources ?: return).toMutableList()
        val pinned = picked.indexOfFirst { it.key == pinnedKey }
        when {
            pinned < 0 -> pinnedKey = null
            // A call still comes first; whatever was swapped forward waits behind it.
            pinned > 0 && picked.first() !is IslandContent.Call -> picked.add(0, picked.removeAt(pinned))
        }
        primary = picked.firstOrNull()
        second = picked.getOrNull(1)
        // Paused music goes the moment its time is up, not at the next check.
        lingerJob?.cancel()
        val music = sources?.music
        if (music != null && !music.playing) {
            val left = lastPlayingAt + settings.pausedLingerMs - SystemClock.elapsedRealtime()
            if (left > 0) lingerJob = scope.launch {
                delay(left + 50)
                repick()
                refresh()
            }
        }
    }

    private var lingerJob: Job? = null

    /**
     * The small drop opens at once as the card, and becomes the island: closed again it stays in front, with what was
     * the island now the small drop. Unless it ended while open, like a torch switched off: then it is simply gone.
     */
    fun openSecond(): Boolean {
        val content = second ?: return false
        pinnedKey = content.key
        repick()
        expandedKey = content.key
        expandedFallback = content
        expandJob?.cancel()
        haptics.play(Haptics.Kind.EXPAND)
        poke()
        refresh()
        return true
    }

    /** Everything worth showing, most important first. The first is the island, the second its small drop. */
    private fun pick(s: Sources5): List<IslandContent> {
        val now = SystemClock.elapsedRealtime()
        if (s.music?.playing == true) lastPlayingAt = now
        val out = mutableListOf<IslandContent>()
        if (settings.showCalls && s.call != null) out += IslandContent.Call(s.call)
        if (settings.showLive) s.live.take(2).forEach { out += IslandContent.Live(it) }
        if (settings.showMusic && s.music?.playing == true) out += IslandContent.Music(s.music)
        // After the music, so during a song or a video call the camera is the small drop beside it.
        if (privacy.any) out += IslandContent.Privacy(privacy)
        if (settings.showTorch && s.torch.on) out += IslandContent.Torch(s.torch)
        // Held at a limit (Battery Care) the phone is plugged in but not charging: still worth showing, with the limit.
        val plugged = s.battery.charging || (s.battery.limit != null && s.battery.plug != PlugType.NONE)
        if (settings.showCharging && plugged) out += IslandContent.Charging(s.battery)
        if (settings.showMusic && s.music != null && !s.music.playing && now - lastPlayingAt < settings.pausedLingerMs) out += IslandContent.Music(s.music)
        return out
    }

    fun setVisibility(value: Visibility) {
        if (value == visibility) return
        visibility = value
        refresh()
    }

    /** What was announced lately, so the same message posted twice (an app that updates it at once) shows once. */
    private val recent = ArrayDeque<Pair<String, Long>>()

    private fun signature(a: Alert) = listOf(a.kind.name, a.packageName.orEmpty(), a.title, a.text.orEmpty()).joinToString("|")

    private fun onAlert(incoming: Alert) {
        // News from the app that is open is news the person is looking at: picking a device in Tandem, a message in the
        // chat that is open. It would only cover the top of that app.
        val fromFront = incoming.packageName != null && incoming.packageName == visibility.foregroundPackage
        if (fromFront && (incoming.kind == AlertKind.NOTIFICATION || incoming.kind == AlertKind.OTHER)) return
        if (incoming.kind == AlertKind.NOTIFICATION || incoming.kind == AlertKind.ALARM) {
            val now = SystemClock.elapsedRealtime()
            while (recent.isNotEmpty() && now - recent.first().second > DUPLICATE_WINDOW_MS) recent.removeFirst()
            val sig = signature(incoming)
            if (recent.any { it.first == sig }) return
            recent.addLast(sig to now)
        }
        val current = alert
        if (current != null && current.id == incoming.id) {
            // The same announcement again, like the volume moving on: update it in place and give it its time again.
            alert = incoming
            restartAlertTimer(incoming)
            refresh()
            return
        }
        queue.removeAll { it.id == incoming.id }
        queue.addLast(incoming)
        while (queue.size > MAX_QUEUE) queue.removeFirst()
        if (current == null) showNextAlert()
    }

    private fun showNextAlert() {
        val next = queue.removeFirstOrNull()
        alert = next
        if (next != null) {
            if (expandedKey == null) haptics.play(if (next.kind in MINI_KINDS) Haptics.Kind.TICK else Haptics.Kind.ALERT)
            restartAlertTimer(next)
        }
        refresh()
    }

    private fun restartAlertTimer(a: Alert) {
        alertJob?.cancel()
        alertJob = scope.launch {
            delay(a.durationMs ?: settings.alertDurationMs)
            alert = null
            // A short breath between two announcements, so the second reads as new.
            if (queue.isNotEmpty()) delay(180)
            showNextAlert()
        }
    }

    /** Opens the card of what is showing. Returns false when there is nothing to open. */
    fun expand(): Boolean {
        val content = _ui.value.content ?: primary ?: return false
        expandedKey = content.key
        expandedFallback = content
        if (content is IslandContent.Notice) {
            // An announcement that is opened stays until it is closed, instead of running out under the finger.
            alertJob?.cancel()
        }
        haptics.play(Haptics.Kind.EXPAND)
        poke()
        refresh()
        return true
    }

    fun collapse(quiet: Boolean = false) {
        if (expandedKey == null) return
        val wasNotice = expandedKey?.startsWith("alert:") == true
        val ringing = (primary as? IslandContent.Call)?.call?.takeIf { it.incoming }
        if (ringing != null && expandedKey == IslandContent.Call(ringing).key) dismissedRinging = ringing.key
        expandedKey = null
        expandedFallback = null
        expandJob?.cancel()
        hub.replyTo.value = null
        if (!quiet) haptics.play(Haptics.Kind.COLLAPSE)
        if (wasNotice) {
            alert = null
            showNextAlert()
        } else {
            refresh()
        }
    }

    fun toggle() {
        if (expandedKey != null) collapse() else expand()
    }

    private var typing = false

    /** While a reply is typed the card stays open; afterwards it closes after the usual while again. */
    fun setTyping(on: Boolean) {
        typing = on
        if (on) expandJob?.cancel() else poke()
    }

    /** Someone touched the opened card: it stays open a while longer. */
    fun poke() {
        expandJob?.cancel()
        // Lyrics are read along with the song: the card stays until it is closed by hand.
        val reading = expandedKey == IslandContent.Music.KEY && settings.showLyrics && hub.lyricsOpen.value
        if (expandedKey == null || typing || reading) return
        expandJob = scope.launch {
            delay(EXPANDED_IDLE_MS)
            collapse()
        }
    }

    /**
     * Another screen came to the front, an app or the screenshot editor: an open card would sit over its top bar, so it
     * closes by itself. A call stays open, and so does a reply being typed.
     */
    fun stepAside() {
        if (expandedKey == null || typing || _ui.value.content is IslandContent.Call) return
        collapse(quiet = true)
    }

    /** Puts away what is showing: an announcement goes, an opened card closes. For the Dismiss gesture. */
    fun dismiss() {
        when {
            expandedKey != null -> collapse()
            alert != null -> dismissAlert()
        }
    }

    /** Takes an announcement away early, for its buttons. */
    fun dismissAlert() {
        if (expandedKey?.startsWith("alert:") == true) {
            collapse()
        } else {
            alertJob?.cancel()
            alert = null
            showNextAlert()
        }
    }

    private fun refresh() {
        val ringing = (primary as? IslandContent.Call)?.call?.incoming == true
        val hidden = !settings.enabled || (!ringing && (
            // On the lock screen the status bar is part of the lock screen's own window, so it looks like full screen
            // there; the lock screen has its own switch instead.
            (settings.hideFullscreen && !visibility.statusBarShown && !visibility.locked) ||
                (settings.hideLandscape && visibility.landscape) ||
                (settings.hideOnLockscreen && visibility.locked) ||
                (visibility.foregroundPackage != null && visibility.foregroundPackage in settings.hiddenApps)
            ))

        val expanded = expandedKey?.let { key ->
            listOfNotNull(primary, second, alert?.let { IslandContent.Notice(it) }).firstOrNull { it.key == key }
                ?: expandedFallback?.takeIf { it.key == key && it !is IslandContent.Call && it !is IslandContent.Live && it !is IslandContent.Privacy }
        }
        if (expandedKey != null && expanded == null) {
            // What was open has gone, a call that ended for instance: close quietly.
            expandedKey = null
            expandedFallback = null
        } else if (expanded != null) {
            expandedFallback = expanded
        }

        // The small drop is kept while a card or an announcement covers the island, so it can step aside in place and
        // come back where it was, instead of melting into the island and swelling out again.
        val drop = second?.takeIf { settings.showSecond && primary != null }
        // The small dots only when the camera or microphone is not already showing as the island or its drop.
        val dots = privacy.takeIf { primary !is IslandContent.Privacy && drop !is IslandContent.Privacy } ?: PrivacyInfo()
        val ui = when {
            hidden -> IslandUi(Stage.HIDDEN, null, dots)
            expanded != null -> IslandUi(Stage.EXPANDED, expanded, dots, drop)
            alert != null -> IslandUi(if (alert!!.kind in MINI_KINDS) Stage.MINI else Stage.ALERT, IslandContent.Notice(alert!!), dots, drop)
            primary != null -> IslandUi(Stage.COMPACT, primary, dots, drop)
            settings.idleVisible -> IslandUi(Stage.IDLE, null, dots)
            else -> IslandUi(Stage.HIDDEN, null, dots)
        }
        _ui.value = ui
    }

    companion object {
        /** Announcements that only say a state or a level: they widen the closed island instead of opening a card. */
        val MINI_KINDS = setOf(
            AlertKind.VOLUME, AlertKind.BRIGHTNESS, AlertKind.RINGER, AlertKind.DND, AlertKind.ROTATION, AlertKind.BATTERY_SAVER,
            AlertKind.CHARGING, AlertKind.UNPLUGGED, AlertKind.BATTERY_FULL, AlertKind.UNLOCK, AlertKind.HEADPHONES,
        )
        const val DUPLICATE_WINDOW_MS = 6_000L
        const val EXPANDED_IDLE_MS = 9_000L
        const val MAX_QUEUE = 4
    }
}
