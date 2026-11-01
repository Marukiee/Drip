package nl.markmaaktmedia.drip.sources

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.Rating
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import nl.markmaaktmedia.drip.island.MediaCommands
import nl.markmaaktmedia.drip.island.NowPlaying
import nl.markmaaktmedia.drip.island.RepeatMode

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
        fun find(kind: CustomKind) = custom.firstOrNull { kindOf(it) == kind }
        val repeat = find(CustomKind.REPEAT)
        val shuffle = find(CustomKind.SHUFFLE)
        val like = find(CustomKind.LIKE)
        val rated = likesByRating(s.ratingType, actions)
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
            repeat = repeat?.let { a ->
                followPress(repeatOfAction(a, packageName), s.pressedRepeat, justNow(s.repeatAt), signatureOf(a) == s.repeatSignature)
                    ?: RepeatMode.OFF
            },
            shuffle = shuffle?.let { a ->
                followPress(shuffleOfAction(a, packageName), s.pressedShuffle, justNow(s.shuffleAt), signatureOf(a) == s.shuffleSignature)
                    ?: false
            },
            liked = likedOf(s, m, rated, like, packageName, title),
            next = nextInQueue(s.queue, state?.activeQueueItemId ?: MediaSession.QueueItem.UNKNOWN_ID.toLong()),
            commands = s.commands,
            embeddedLyrics = lyricsOf(m),
            mediaId = m.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
            mediaUri = m.getString(MediaMetadata.METADATA_KEY_MEDIA_URI),
        )
    }

    /**
     * Whether the song is liked. The player's own rating says it best; then the heart action, by Media3's icon number,
     * the name of its icon or its words. A player that tells none of those gets what was last pressed here for this
     * song, so the heart at least follows the press.
     */
    private fun likedOf(s: Session, m: MediaMetadata, rated: Boolean, like: PlaybackState.CustomAction?, packageName: String, title: String): Boolean? {
        if (rated && m.containsKey(MediaMetadata.METADATA_KEY_USER_RATING)) return likedByRating(m)
        if (like == null && !rated) return null
        val pressed = s.pressedLike?.takeIf { it.first == title }?.second
        return heartOf(
            stated = like?.let { likedOfAction(it, packageName) },
            pressed = pressed,
            // The player may take a moment to send its new state.
            pressedJustNow = SystemClock.elapsedRealtime() - s.pressedAt < 1500,
            actionUnchanged = like == null || signatureOf(like) == s.pressedSignature,
        )
    }

    /** Everything a player could change about its heart action to show the new state. */
    private fun signatureOf(a: PlaybackState.CustomAction): String =
        "${a.action}|${a.name}|${a.icon}|${a.extras?.getInt(MEDIA3_ICON_KEY, -1)}"

    private fun likedOfAction(a: PlaybackState.CustomAction, packageName: String): Boolean? =
        a.extras?.takeIf { it.containsKey(MEDIA3_ICON_KEY) }?.getInt(MEDIA3_ICON_KEY)?.let(::likedOfMedia3Icon)
            ?: likedOfIconName(iconName(packageName, a.icon))
            ?: likedStatedBy(a.action, a.name?.toString())

    private val iconNames = HashMap<Pair<String, Int>, String?>()

    /** The resource name of a player's icon, "ic_favorite_border", read once per icon. */
    private fun iconName(packageName: String, id: Int): String? {
        if (id == 0) return null
        return iconNames.getOrPut(packageName to id) {
            safely("icon name") { context.packageManager.getResourcesForApplication(packageName).getResourceEntryName(id) }
        }
    }

    /** Lyrics a player put in the metadata under a key of its own ("lyrics", "android.media.metadata.LYRICS"...). */
    private fun lyricsOf(m: MediaMetadata): String? = safely("lyrics") {
        m.keySet().firstOrNull { "lyric" in it.lowercase() }?.let { m.getText(it)?.toString() ?: m.getString(it) }?.takeIf { it.isNotBlank() }
    }

    private fun media3Icon(a: PlaybackState.CustomAction): Int? =
        a.extras?.takeIf { it.containsKey(MEDIA3_ICON_KEY) }?.getInt(MEDIA3_ICON_KEY)

    private fun kindOf(a: PlaybackState.CustomAction): CustomKind? {
        val media3 = media3Icon(a)
        if (media3 != null && likedOfMedia3Icon(media3) != null) return CustomKind.LIKE
        if (media3 != null && shuffleOfMedia3Icon(media3) != null) return CustomKind.SHUFFLE
        if (media3 != null && repeatOfMedia3Icon(media3) != null) return CustomKind.REPEAT
        return customKindOf(a.action, a.name?.toString())
    }

    private fun shuffleOfAction(a: PlaybackState.CustomAction, packageName: String): Boolean? =
        media3Icon(a)?.let(::shuffleOfMedia3Icon)
            ?: shuffleOfIconName(iconName(packageName, a.icon))
            ?: shuffleStatedBy(a.action, a.name?.toString())

    private fun repeatOfAction(a: PlaybackState.CustomAction, packageName: String): RepeatMode? =
        media3Icon(a)?.let(::repeatOfMedia3Icon)
            ?: repeatOfIconName(iconName(packageName, a.icon))
            ?: repeatStatedBy(a.action, a.name?.toString())

    private fun justNow(at: Long) = SystemClock.elapsedRealtime() - at < 1500

    private inner class Session(val controller: MediaController) : MediaController.Callback() {
        /** The song and the state the heart was last pressed to here, for players that never say it themselves. */
        var pressedLike: Pair<String, Boolean>? = null
        var pressedAt = 0L
        /** The heart action as it was when pressed, to see whether the player changed it afterwards. */
        var pressedSignature: String? = null
        /** The same for shuffle and repeat, which belong to the player rather than to one song. */
        var pressedShuffle: Boolean? = null
        var shuffleAt = 0L
        var shuffleSignature: String? = null
        var pressedRepeat: RepeatMode? = null
        var repeatAt = 0L
        var repeatSignature: String? = null
        var metadata: MediaMetadata? = null
        var state: PlaybackState? = null
        var lastPlayingAt = 0L
        var art: Bitmap? = null
        /** Rating.RATING_NONE and the like. Read with the song, since a podcast in the same app may take no heart. */
        var ratingType = Rating.RATING_NONE
        /** Kept from the callback: a long queue is a big binder call, too much to ask for on every state change. */
        var queue: List<QueueEntry> = emptyList()
        private var artSource: Bitmap? = null
        val commands = Commands(this)

        fun attach() {
            metadata = safely("metadata") { controller.metadata }
            state = safely("playback state") { controller.playbackState }
            if (isActive(state)) lastPlayingAt = SystemClock.elapsedRealtime()
            readQueue(safely("queue") { controller.queue })
            readRatingType()
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
            readRatingType()
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

        private fun readRatingType() {
            ratingType = safely("rating type") { controller.ratingType } ?: Rating.RATING_NONE
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
        override fun cycleRepeat() {
            val shown = safely("repeat") { build(s)?.repeat } ?: RepeatMode.OFF
            s.pressedRepeat = when (shown) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
            s.repeatAt = SystemClock.elapsedRealtime()
            s.repeatSignature = action(CustomKind.REPEAT)?.let(::signatureOf)
            handler.post { update() }
            custom(CustomKind.REPEAT)
        }

        override fun toggleShuffle() {
            s.pressedShuffle = safely("shuffle") { build(s)?.shuffle } != true
            s.shuffleAt = SystemClock.elapsedRealtime()
            s.shuffleSignature = action(CustomKind.SHUFFLE)?.let(::signatureOf)
            handler.post { update() }
            custom(CustomKind.SHUFFLE)
        }

        private fun action(kind: CustomKind) = s.controller.playbackState?.customActions?.firstOrNull { kindOf(it) == kind }
        override fun toggleLike() {
            val shown = safely("liked") { build(s)?.liked } == true
            s.title()?.let { s.pressedLike = it to !shown }
            s.pressedAt = SystemClock.elapsedRealtime()
            s.pressedSignature = s.controller.playbackState?.customActions?.firstOrNull { kindOf(it) == CustomKind.LIKE }?.let(::signatureOf)
            handler.post { update() }
            if (!likesByRating(s.ratingType, s.controller.playbackState?.actions ?: 0L)) return custom(CustomKind.LIKE)
            val rating = if (s.ratingType == Rating.RATING_HEART) Rating.newHeartRating(!shown) else Rating.newThumbRating(!shown)
            controls("rate") { it.setRating(rating) }
        }

        override fun openPlayer() {
            val activity = safely("session activity") { s.controller.sessionActivity }
            if (activity == null || !activity.sendFrom(context)) context.launchApp(s.controller.packageName)
        }

        // Looked up when pressed: the player swaps the action for its next state after each press.
        private fun custom(kind: CustomKind) = controls("custom action") { c ->
            val action = s.controller.playbackState?.customActions
                ?.firstOrNull { kindOf(it) == kind } ?: return@controls
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

        /** The heart or thumb the user gave this song, through the session's rating. Unrated is not liked. */
        fun likedByRating(m: MediaMetadata): Boolean = safely("user rating") {
            val r = m.getRating(MediaMetadata.METADATA_KEY_USER_RATING) ?: return@safely false
            when (r.ratingStyle) {
                Rating.RATING_HEART -> r.hasHeart()
                Rating.RATING_THUMB_UP_DOWN -> r.isThumbUp
                else -> false
            }
        } == true

        fun samePicture(a: Bitmap, b: Bitmap): Boolean =
            a.width == b.width && a.height == b.height && safely("compare art") { a.sameAs(b) } == true
    }
}
