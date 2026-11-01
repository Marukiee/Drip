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

    data class Music(val playing: NowPlaying) : IslandContent { override val key get() = "music" }
    data class Call(val call: CallInfo) : IslandContent { override val key get() = "call:" + call.key }
    data class Live(val live: LiveInfo) : IslandContent { override val key get() = "live:" + live.key }
    data class Charging(val battery: BatteryInfo) : IslandContent { override val key get() = "charging" }
    data class Torch(val torch: TorchInfo) : IslandContent { override val key get() = "torch" }
    data class Notice(val alert: Alert) : IslandContent { override val key get() = "alert:" + alert.id }
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
    /** A second thing going on at the same time, shown as its own small drop next to the closed island. */
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
                refresh()
            }
        }
        scope.launch {
            combine(hub.call, hub.live, hub.nowPlaying, hub.torch, hub.battery) { call, live, music, torch, battery ->
                Sources5(call, live, music, torch, battery)
            }.collect { s ->
                val wasRinging = (primary as? IslandContent.Call)?.call?.incoming == true
                val picked = pick(s)
                primary = picked.firstOrNull()
                second = picked.getOrNull(1)
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
            hub.privacy.collect { privacy = it; refresh() }
        }
        scope.launch {
            hub.alerts.collect { onAlert(it) }
        }
        scope.launch {
            // Paused music gives way after a while even when nothing else changes.
            while (true) {
                delay(30_000)
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

    /** Everything worth showing, most important first. The first is the island, the second its small drop. */
    private fun pick(s: Sources5): List<IslandContent> {
        val now = SystemClock.elapsedRealtime()
        if (s.music?.playing == true) lastPlayingAt = now
        val out = mutableListOf<IslandContent>()
        if (settings.showCalls && s.call != null) out += IslandContent.Call(s.call)
        if (settings.showLive) s.live.take(2).forEach { out += IslandContent.Live(it) }
        if (settings.showMusic && s.music?.playing == true) out += IslandContent.Music(s.music)
        if (settings.showTorch && s.torch.on) out += IslandContent.Torch(s.torch)
        if (settings.showCharging && s.battery.charging) out += IslandContent.Charging(s.battery)
        if (settings.showMusic && s.music != null && !s.music.playing && now - lastPlayingAt < PAUSED_LINGER_MS) out += IslandContent.Music(s.music)
        return out
    }

    fun setVisibility(value: Visibility) {
        if (value == visibility) return
        visibility = value
        refresh()
    }

    private fun onAlert(incoming: Alert) {
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

    /** Opens the card of the second thing, the small drop next to the island. */
    fun expandSecond(): Boolean {
        val content = second ?: return false
        expandedKey = content.key
        expandedFallback = content
        haptics.play(Haptics.Kind.EXPAND)
        poke()
        refresh()
        return true
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

    fun collapse() {
        if (expandedKey == null) return
        val wasNotice = expandedKey?.startsWith("alert:") == true
        val ringing = (primary as? IslandContent.Call)?.call?.takeIf { it.incoming }
        if (ringing != null && expandedKey == IslandContent.Call(ringing).key) dismissedRinging = ringing.key
        expandedKey = null
        expandedFallback = null
        expandJob?.cancel()
        haptics.play(Haptics.Kind.COLLAPSE)
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

    /** Someone touched the opened card: it stays open a while longer. */
    fun poke() {
        expandJob?.cancel()
        if (expandedKey == null) return
        expandJob = scope.launch {
            delay(EXPANDED_IDLE_MS)
            collapse()
        }
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
            (settings.hideFullscreen && !visibility.statusBarShown) ||
                (settings.hideLandscape && visibility.landscape) ||
                (settings.hideOnLockscreen && visibility.locked) ||
                (visibility.foregroundPackage != null && visibility.foregroundPackage in settings.hiddenApps)
            ))

        val expanded = expandedKey?.let { key ->
            listOfNotNull(primary, second, alert?.let { IslandContent.Notice(it) }).firstOrNull { it.key == key }
                ?: expandedFallback?.takeIf { it.key == key && it !is IslandContent.Call && it !is IslandContent.Live }
        }
        if (expandedKey != null && expanded == null) {
            // What was open has gone, a call that ended for instance: close quietly.
            expandedKey = null
            expandedFallback = null
        } else if (expanded != null) {
            expandedFallback = expanded
        }

        val ui = when {
            hidden -> IslandUi(Stage.HIDDEN, null, privacy)
            expanded != null -> IslandUi(Stage.EXPANDED, expanded, privacy)
            alert != null -> IslandUi(if (alert!!.kind in MINI_KINDS) Stage.MINI else Stage.ALERT, IslandContent.Notice(alert!!), privacy)
            primary != null -> IslandUi(Stage.COMPACT, primary, privacy, second?.takeIf { settings.showSecond })
            settings.idleVisible -> IslandUi(Stage.IDLE, null, privacy)
            else -> IslandUi(Stage.HIDDEN, null, privacy)
        }
        _ui.value = ui
    }

    companion object {
        /** Announcements that only say a state or a level: they widen the closed island instead of opening a card. */
        val MINI_KINDS = setOf(
            AlertKind.VOLUME, AlertKind.BRIGHTNESS, AlertKind.RINGER, AlertKind.DND, AlertKind.ROTATION, AlertKind.BATTERY_SAVER,
            AlertKind.CHARGING, AlertKind.UNPLUGGED, AlertKind.BATTERY_FULL, AlertKind.UNLOCK, AlertKind.HEADPHONES,
        )
        const val PAUSED_LINGER_MS = 3 * 60_000L
        const val EXPANDED_IDLE_MS = 9_000L
        const val MAX_QUEUE = 4
    }
}
