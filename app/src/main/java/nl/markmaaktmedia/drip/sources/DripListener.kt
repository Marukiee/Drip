package nl.markmaaktmedia.drip.sources

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.markmaaktmedia.drip.data.IslandSettings
import nl.markmaaktmedia.drip.graph
import nl.markmaaktmedia.drip.island.CallInfo
import nl.markmaaktmedia.drip.island.LiveInfo
import nl.markmaaktmedia.drip.island.NowPlaying

/**
 * Music, calls, live notifications and notification alerts.
 *
 * What the sources found is kept apart from what is published, so turning a switch back on shows the song or the
 * timer that is already going instead of waiting for its next update. Everything runs on the main thread: the
 * listener's own callbacks, the media callbacks and the settings.
 */
class DripListener : NotificationListenerService() {

    private class CallEntry(val postedAt: Long, val info: CallInfo)
    private class LiveEntry(val firstSeen: Long, val info: LiveInfo)

    private val hub get() = graph.hub
    private lateinit var apps: Apps
    private lateinit var reader: NotificationReader
    private val memory = NotificationMemory()
    private val calls = HashMap<String, CallEntry>()
    private val live = HashMap<String, LiveEntry>()
    private var nowPlaying: NowPlaying? = null
    private var media: MediaSource? = null
    private var scope: CoroutineScope? = null
    private var settings: IslandSettings? = null
    private var connected = false
    private val ranking = Ranking()

    override fun onCreate() {
        super.onCreate()
        apps = Apps(this)
        reader = NotificationReader(this, apps) { key -> safely("cancel") { cancelNotification(key) } }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        teardown()
        connected = true
        hub.listenerConnected.value = true
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also {
            it.launch {
                graph.prefs.island.collect { s ->
                    settings = s
                    publishAll()
                }
            }
        }
        // What is already in the shade only seeds: none of it is news.
        safely("active notifications") {
            activeNotifications?.forEach { sbn -> safely("read ${sbn.key}") { take(sbn, null, seed = true) } }
        }
        media = MediaSource(this, apps) { np ->
            nowPlaying = np
            publishMedia()
        }.also { it.start() }
        publishAll()
    }

    override fun onListenerDisconnected() {
        teardown()
        hub.listenerConnected.value = false
        safely("rebind") { requestRebind(ComponentName(this, DripListener::class.java)) }
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        teardown()
        hub.listenerConnected.value = false
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        if (sbn == null || !connected) return
        safely("posted ${sbn.key}") { take(sbn, rankingMap, seed = false) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        if (sbn == null || !connected) return
        memory.forget(sbn.key)
        if (calls.remove(sbn.key) != null) publishCall()
        if (live.remove(sbn.key) != null) publishLive()
    }

    private fun take(sbn: StatusBarNotification, rankingMap: RankingMap?, seed: Boolean) {
        if (sbn.packageName == packageName) return
        val key = sbn.key
        val f = reader.facts(sbn)
        val print = reader.fingerprint(sbn)
        val fresh = if (seed) {
            memory.seed(key, print)
            false
        } else {
            memory.isFresh(key, print, onlyAlertOnce = sbn.notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        }

        val callChanged = if (isCall(f)) {
            calls[key] = CallEntry(sbn.postTime, reader.call(sbn, f))
            true
        } else {
            calls.remove(key) != null
        }
        val liveChanged = if (isLive(f, packageName)) {
            // Ordered by when it first showed up, so a download that updates every second does not jump to the front.
            val first = live[key]?.firstSeen ?: sbn.postTime
            live[key] = LiveEntry(first, reader.live(sbn, f))
            true
        } else {
            live.remove(key) != null
        }
        if (!seed) {
            if (callChanged) publishCall()
            if (liveChanged) publishLive()
        }
        if (fresh) alert(sbn, f, rankingMap)
    }

    private fun alert(sbn: StatusBarNotification, f: NoticeFacts, rankingMap: RankingMap?) {
        val s = settings ?: return
        val map = rankingMap ?: safely("ranking") { currentRanking } ?: return
        if (!map.getRanking(sbn.key, ranking)) return
        val rank = RankFacts(
            importance = ranking.importance,
            ambient = ranking.isAmbient,
            matchesFilter = ranking.matchesInterruptionFilter(),
            suspended = ranking.isSuspended,
        )
        val kind = alertKindFor(f, rank, s, packageName) ?: return
        hub.post(reader.alert(sbn, f, kind, s))
    }

    private fun publishAll() {
        publishMedia()
        publishCall()
        publishLive()
    }

    private fun publishMedia() {
        val s = settings ?: return
        hub.nowPlaying.value = nowPlaying.takeIf { s.showMusic }
    }

    /** A ringing call wins over one on hold; otherwise the newest. */
    private fun publishCall() {
        val s = settings ?: return
        hub.call.value = if (s.showCalls) {
            calls.values.maxWithOrNull(compareBy<CallEntry> { it.info.incoming }.thenBy { it.postedAt })?.info
        } else {
            null
        }
    }

    private fun publishLive() {
        val s = settings ?: return
        val mine = if (s.showLive) live.values.sortedByDescending { it.firstSeen }.map { it.info } else emptyList()
        // Drip's own entries (an appointment, an install) live in the same list and are not the listener's to drop.
        hub.live.update { current -> mine + current.filter { it.key.startsWith(OWN_LIVE_PREFIX) } }
    }

    private fun teardown() {
        connected = false
        scope?.cancel()
        scope = null
        media?.stop()
        media = null
        settings = null
        memory.clear()
        calls.clear()
        live.clear()
        nowPlaying = null
        hub.nowPlaying.value = null
        hub.call.value = null
        hub.live.update { current -> current.filter { it.key.startsWith(OWN_LIVE_PREFIX) } }
    }
}
