package nl.markmaaktmedia.drip.sources

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import nl.markmaaktmedia.drip.island.MediaCommands
import nl.markmaaktmedia.drip.island.NowPlaying

/**
 * Follows every active media session and tells [publish] what the island should show. Each session gets its own
 * callback, so a second player that starts takes over without waiting for the session list to change.
 */
internal class MediaSource(
    private val context: Context,
    private val apps: Apps,
    private val publish: (NowPlaying?) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val component = ComponentName(context, DripListener::class.java)
    private var manager: MediaSessionManager? = null
    private var sessions: List<Session> = emptyList()
    private val onSessions = MediaSessionManager.OnActiveSessionsChangedListener { follow(it.orEmpty()) }

    /** Only works while the notification listener is connected; Android checks that it is enabled. */
    fun start() {
        val m = context.getSystemService(MediaSessionManager::class.java) ?: return
        manager = m
        safely("media sessions") {
            m.addOnActiveSessionsChangedListener(onSessions, component, handler)
            follow(m.getActiveSessions(component))
        }
    }

    fun stop() {
        safely("media stop") { manager?.removeOnActiveSessionsChangedListener(onSessions) }
        manager = null
        sessions.forEach { it.release() }
        sessions = emptyList()
        publish(null)
    }

    private fun follow(controllers: List<MediaController>) {
        val old = sessions.associateBy { it.controller.sessionToken }
        val next = controllers.map { c -> old[c.sessionToken] ?: Session(c).also { it.attach() } }
        val kept = next.toSet()
        old.values.filter { it !in kept }.forEach { it.release() }
        sessions = next
        update()
    }

    private fun update() {
        val views = sessions.map {
            SessionView(isActive(it.state), it.title() != null, isUsable(it.state), it.lastPlayingAt)
        }
        val chosen = pickSession(views)?.let { sessions[it] }
        publish(chosen?.let { safely("now playing") { build(it) } })
    }

    private fun build(s: Session): NowPlaying? {
        val m = s.metadata ?: return null
        val title = s.title() ?: return null
        val state = s.state
        val actions = state?.actions ?: 0L
        val duration = m.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0)
        val custom = state?.customActions.orEmpty()
        fun find(kind: CustomKind) = custom.firstOrNull { customKindOf(it.action, it.name?.toString()) == kind }
        val repeat = find(CustomKind.REPEAT)
        val shuffle = find(CustomKind.SHUFFLE)
        val like = find(CustomKind.LIKE)
        val packageName = s.controller.packageName
        return NowPlaying(
            packageName = packageName,
            appLabel = apps.label(packageName),
            title = title,
            artist = m.text(MediaMetadata.METADATA_KEY_ARTIST) ?: m.text(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: m.text(MediaMetadata.METADATA_KEY_AUTHOR) ?: m.text(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                ?: "",
            album = m.text(MediaMetadata.METADATA_KEY_ALBUM),
            art = s.art,
            durationMs = duration,
            positionMs = state?.position?.takeIf { it >= 0 } ?: 0,
            positionAtElapsed = state?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            speed = when (state?.state) {
                PlaybackState.STATE_PLAYING -> state.playbackSpeed.takeIf { it > 0f } ?: 1f
                PlaybackState.STATE_FAST_FORWARDING, PlaybackState.STATE_REWINDING -> state.playbackSpeed
                // Buffering or paused: the position stands still.
                else -> 0f
            },
            playing = isActive(state),
            canSkipNext = state == null || actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
            canSkipPrevious = state == null || actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
            canSeek = duration > 0 && actions and PlaybackState.ACTION_SEEK_TO != 0L,
            repeat = repeat?.let { repeatModeOf(it.action, it.name?.toString()) },
            shuffle = shuffle?.let { shuffleOnOf(it.action, it.name?.toString()) },
            liked = like?.let { likedOf(it.action, it.name?.toString()) },
            next = nextInQueue(s.queue, state?.activeQueueItemId ?: MediaSession.QueueItem.UNKNOWN_ID.toLong()),
            commands = s.commands,
        )
    }

    private inner class Session(val controller: MediaController) : MediaController.Callback() {
        var metadata: MediaMetadata? = null
        var state: PlaybackState? = null
        var lastPlayingAt = 0L
        var art: Bitmap? = null
        /** Kept from the callback: a long queue is a big binder call, too much to ask for on every state change. */
        var queue: List<QueueEntry> = emptyList()
        private var artSource: Bitmap? = null
        val commands = Commands(this)

        fun attach() {
            metadata = safely("metadata") { controller.metadata }
            state = safely("playback state") { controller.playbackState }
            if (isActive(state)) lastPlayingAt = SystemClock.elapsedRealtime()
            readQueue(safely("queue") { controller.queue })
            readArt()
            safely("media callback") { controller.registerCallback(this, handler) }
        }

        fun release() {
            safely("media callback") { controller.unregisterCallback(this) }
        }

        fun title(): String? = metadata?.let { m ->
            m.text(MediaMetadata.METADATA_KEY_TITLE) ?: m.text(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            this.metadata = metadata
            readArt()
            update()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            val was = isActive(this.state)
            this.state = state
            // Both the start and the stop count, so among paused players the one paused last wins.
            if (isActive(state) != was) lastPlayingAt = SystemClock.elapsedRealtime()
            update()
        }

        override fun onQueueChanged(queue: MutableList<MediaSession.QueueItem>?) {
            readQueue(queue)
            update()
        }

        private fun readQueue(items: List<MediaSession.QueueItem>?) {
            queue = items.orEmpty().map { QueueEntry(it.queueId, it.description?.title?.toString()) }
        }

        override fun onSessionDestroyed() {
            release()
            sessions = sessions - this
            update()
        }

        /**
         * Scales the art once per picture. Players send their metadata again for small things, and a new bitmap each
         * time would make the island see a new song and pick its colours again.
         */
        private fun readArt() {
            val m = metadata
            val source = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: m?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            if (source == null) {
                art = null
                artSource = null
                return
            }
            val before = artSource
            if (before != null && (source === before || samePicture(source, before))) return
            artSource = source
            art = safely("scale art") { source.fitWithin(ART_MAX_PX) } ?: source
        }
    }

    private inner class Commands(private val s: Session) : MediaCommands {
        private fun controls(what: String, block: (MediaController.TransportControls) -> Unit) {
            safely(what) { block(s.controller.transportControls) }
        }

        override fun playPause() = controls("play pause") {
            if (isActive(s.controller.playbackState)) it.pause() else it.play()
        }

        override fun next() = controls("next") { it.skipToNext() }
        override fun previous() = controls("previous") { it.skipToPrevious() }
        override fun seekTo(positionMs: Long) = controls("seek") { it.seekTo(positionMs.coerceAtLeast(0)) }
        override fun cycleRepeat() = custom(CustomKind.REPEAT)
        override fun toggleShuffle() = custom(CustomKind.SHUFFLE)
        override fun toggleLike() = custom(CustomKind.LIKE)

        override fun openPlayer() {
            val activity = safely("session activity") { s.controller.sessionActivity }
            if (activity == null || !activity.sendFrom(context)) context.launchApp(s.controller.packageName)
        }

        // Looked up when pressed: the player swaps the action for its next state after each press.
        private fun custom(kind: CustomKind) = controls("custom action") { c ->
            val action = s.controller.playbackState?.customActions
                ?.firstOrNull { customKindOf(it.action, it.name?.toString()) == kind } ?: return@controls
            c.sendCustomAction(action, action.extras)
        }
    }

    private companion object {
        const val ART_MAX_PX = 512

        fun isActive(state: PlaybackState?) = when (state?.state) {
            PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING,
            PlaybackState.STATE_FAST_FORWARDING, PlaybackState.STATE_REWINDING,
            PlaybackState.STATE_SKIPPING_TO_NEXT, PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
            PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> true
            else -> false
        }

        fun isUsable(state: PlaybackState?) = when (state?.state) {
            PlaybackState.STATE_STOPPED, PlaybackState.STATE_ERROR -> false
            else -> true
        }

        fun MediaMetadata.text(key: String): String? =
            getText(key)?.toString()?.trim()?.takeIf { it.isNotEmpty() }

        fun samePicture(a: Bitmap, b: Bitmap): Boolean =
            a.width == b.width && a.height == b.height && safely("compare art") { a.sameAs(b) } == true
    }
}
