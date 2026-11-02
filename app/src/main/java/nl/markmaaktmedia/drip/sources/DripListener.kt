package nl.markmaaktmedia.drip.sources

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.markmaaktmedia.drip.automation.isAutomationKey
import nl.markmaaktmedia.drip.data.IslandSettings
import nl.markmaaktmedia.drip.graph
import nl.markmaaktmedia.drip.island.CallInfo
import nl.markmaaktmedia.drip.island.LiveInfo
import nl.markmaaktmedia.drip.island.NowPlaying
import nl.markmaaktmedia.drip.island.LiveKind
import nl.markmaaktmedia.drip.R

/**
 * Music, calls, live notifications and notification alerts.
 *
 * What the sources found is kept apart from what is published, so turning a switch back on shows the song or the
 * timer that is already going instead of waiting for its next update. Everything runs on the main thread: the
 * listener's own callbacks, the media callbacks and the settings.
 */
class DripListener : NotificationListenerService() {

    private class CallEntry(val postedAt: Long, val info: CallInfo)
    /** [tandem] is a transfer of Tandem's, which has a switch of its own. */
    private class LiveEntry(val firstSeen: Long, val info: LiveInfo, val tandem: Boolean)

    private val hub get() = graph.hub
    private lateinit var apps: Apps
    private lateinit var reader: NotificationReader
    private val memory = NotificationMemory()
    private val tandem = TandemWatch()
    private val calls = HashMap<String, CallEntry>()
    private val live = HashMap<String, LiveEntry>()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
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
            announceRemote(nowPlaying?.remote, np)
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
        tandem.forget(sbn.key)
        if (calls.remove(sbn.key) != null) publishCall()
        val was = live.remove(sbn.key) ?: return
        // A download near its end whose bar is taken away has most likely finished; the app may post "complete" anew.
        val done = was.info.kind == LiveKind.DOWNLOAD && !was.info.finished && (was.info.progress ?: 0f) >= 0.9f &&
            reason != REASON_CANCEL && reason != REASON_CANCEL_ALL
        if (done) finish(sbn.key, was, null) else publishLive()
    }

    /**
     * Keeps a finished download on the island a moment, with a way to open it: the finished notification's own when
     * there is one, else what the download had. Its card stays open after that until it is closed.
     */
    private fun finish(key: String, was: LiveEntry, now: LiveInfo?) {
        val info = was.info.copy(
            finished = true, progress = 1f, indeterminate = false,
            text = now?.text ?: getString(R.string.src_download_done),
            shortText = getString(R.string.src_download_done),
            actions = now?.actions.orEmpty(),
            open = now?.open ?: was.info.open,
        )
        live[key] = LiveEntry(was.firstSeen, info, was.tandem)
        publishLive()
        handler.postDelayed({
            if (live[key]?.info?.finished == true) {
                live.remove(key)
                publishLive()
            }
        }, FINISHED_MS)
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
        val quiet = f.silent || importanceOf(key, rankingMap) < NotificationManager.IMPORTANCE_DEFAULT
        // An app that posts "complete" as a new notification: tapping that one opens the file, so the finished download
        // on the island takes over its way to open it.
        if (!seed && !f.ongoing) {
            live.entries.firstOrNull { (_, e) ->
                e.info.finished && e.info.packageName == sbn.packageName &&
                    (f.title == e.info.title || f.text?.contains(e.info.title) == true)
            }?.let { (k, e) ->
                val now = reader.live(sbn, f)
                live[k] = LiveEntry(e.firstSeen, e.info.copy(open = now.open, actions = now.actions), e.tandem)
                publishLive()
            }
        }
        val liveChanged = if (isLive(f, packageName, quiet)) {
            // Ordered by when it first showed up, so a download that updates every second does not jump to the front.
            val first = live[key]?.firstSeen ?: sbn.postTime
            live[key] = LiveEntry(first, reader.live(sbn, f), tandemRole(f) == TandemRole.TRANSFER)
            true
        } else {
            val was = live[key]
            if (!seed && was != null && was.info.kind == LiveKind.DOWNLOAD && !was.info.finished && !f.ongoing) {
                // The same notification now says the download is done: it stays a moment, opening what it opens now.
                finish(key, was, reader.live(sbn, f))
                true
            } else {
                live.remove(key) != null
            }
        }
        if (!seed) {
            if (callChanged) publishCall()
            if (liveChanged) publishLive()
        }
        if (fresh) alert(sbn, f, rankingMap)
        tandemNews(sbn, f, fresh, seed)
    }

    /** Tandem's connection and clipboard come as quiet notifications, which the plain alert rules let pass by. */
    private fun tandemNews(sbn: StatusBarNotification, f: NoticeFacts, fresh: Boolean, seed: Boolean) {
        val role = tandemRole(f) ?: return
        val s = settings
        when (role) {
            TandemRole.CONNECTION -> {
                // Recorded even while switched off, so switching on does not announce who was there all along.
                val joined = tandem.joined(sbn.key, tandemConnectedTo(f.text), seed)
                if (s?.showTandem == true) joined.forEach { hub.post(reader.tandemConnected(sbn, it, s)) }
            }
            TandemRole.CLIPBOARD -> if (fresh && s?.showTandem == true) hub.post(reader.tandemClipboard(sbn, f, s))
            TandemRole.TRANSFER -> Unit
        }
    }

    /** How much the notification may interrupt, as the ranking says; as much as possible when it cannot be read. */
    private fun importanceOf(key: String, rankingMap: RankingMap?): Int {
        val map = rankingMap ?: safely("ranking") { currentRanking } ?: return NotificationManager.IMPORTANCE_DEFAULT
        val r = Ranking()
        return if (map.getRanking(key, r)) r.importance else NotificationManager.IMPORTANCE_DEFAULT
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

    /**
     * The volume buttons moved the speaker the music plays on (a Sonos, a Chromecast): Android leaves the phone's own
     * volume alone then, so the island says it the way it says the phone's volume, with the speaker's name.
     */
    private fun announceRemote(was: nl.markmaaktmedia.drip.island.RemoteVolume?, np: NowPlaying?) {
        val now = np?.remote ?: return
        val s = settings ?: return
        if (was == null || was.level == now.level || !s.alertVolume) return
        // Drip's own slider moved it: the card already shows it.
        if (android.os.SystemClock.elapsedRealtime() - (media?.ownVolumeAt ?: 0L) < OWN_VOLUME_MS) return
        hub.post(
            nl.markmaaktmedia.drip.island.Alert(
                id = "volume", kind = nl.markmaaktmedia.drip.island.AlertKind.VOLUME,
                title = now.name ?: np.appLabel, iconRes = R.drawable.sym_speaker_group, level = now.level,
                durationMs = s.alertDurationMs, packageName = np.packageName,
            ),
        )
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

    /** What automation put in the live list stays there, in front: it was asked for on purpose. */
    private fun publishLive() {
        val s = settings ?: return
        val mine = if (s.showLive) {
            live.values.filter { !it.tandem || s.showTandem }.sortedByDescending { it.firstSeen }.map { it.info }
        } else {
            emptyList()
        }
        // Automation entries and Drip's own (an appointment, an install) share the list and are not the listener's to drop.
        hub.live.update { now -> now.filter { (s.allowAutomation && isAutomationKey(it.key)) || it.key.startsWith(OWN_LIVE_PREFIX) } + mine }
    }

    private companion object {
        /** How long a finished download stays on the closed island. */
        const val FINISHED_MS = 12_000L
        const val OWN_VOLUME_MS = 800L
    }

    private fun teardown() {
        connected = false
        scope?.cancel()
        scope = null
        media?.stop()
        media = null
        settings = null
        memory.clear()
        tandem.clear()
        calls.clear()
        live.clear()
        nowPlaying = null
        hub.nowPlaying.value = null
        hub.call.value = null
        hub.live.update { now -> now.filter { isAutomationKey(it.key) || it.key.startsWith(OWN_LIVE_PREFIX) } }
    }
}
