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
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import nl.markmaaktmedia.drip.island.MediaCommands
import nl.markmaaktmedia.drip.island.NowPlaying
import nl.markmaaktmedia.drip.island.RemoteVolume
import nl.markmaaktmedia.drip.island.RepeatMode
import nl.markmaaktmedia.drip.island.ShuffleMode

/**
 * Follows every active media session and tells [publish] what the island should show: the player that matters most
 * first, then the others going on at the same time. Each session gets its own callback, so a second player that starts
 * takes over without waiting for the session list to change.
 */
internal class MediaSource(
    private val context: Context,
    private val apps: Apps,
    private val publish: (List<NowPlaying>) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())

    /** One thread for covers that have to be read from a link, so the callbacks never wait on a file. */
    private val artLoader = java.util.concurrent.Executors.newSingleThreadExecutor()

    /** When Drip itself last moved a speaker's volume, so that is not announced as news. */
    @Volatile var ownVolumeAt = 0L
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
        artLoader.shutdown()
        safely("media stop") { manager?.removeOnActiveSessionsChangedListener(onSessions) }
        manager = null
        sessions.forEach { it.release() }
        sessions = emptyList()
        publish(emptyList())
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
        val chosen = pickSession(views)
        if (chosen == null) {
            publish(emptyList())
            return
        }
        sessions.forEach { it.volumeVia = null }
        val taken = HashSet<Session>()
        val players = ArrayList<NowPlaying>()
        // Other players at the same time each get their own activity: a speaker that plays on while the phone plays
        // something else. Paused ones come along too; the island decides how long those stay.
        for (i in listOf(chosen) + otherSessions(views, chosen)) {
            if (players.size == MAX_PLAYERS) break
            val s = sessions[i]
            if (s in taken) continue
            val np = player(s, taken) ?: continue
            if (players.none { it.packageName == np.packageName }) players += np
        }
        publish(players)
    }

    /**
     * [s] as one player. The same song can be in two sessions: a player that casts to a speaker whose own app shows it
     * too (PixelPlayer on a Sonos, with the Sonos app alongside). The player leads, with its cover and its buttons; the
     * speaker's app only lends what the player lacks, the speaker's volume above all. Both count as [taken].
     */
    private fun player(s: Session, taken: MutableSet<Session>): NowPlaying? {
        val twin = sessions.firstOrNull { it !== s && it !in taken && sameSong(it, s) }
        taken += s
        twin?.let { taken += it }
        val twinLeads = twin != null && twinLeads(
            s.isRemote(), twin.isRemote(), s.namesSpeaker(), twin.namesSpeaker(),
            twinKeepsLead(twin.led, s.led, isActive(s.state), isActive(twin.state), SystemClock.elapsedRealtime() - twin.lastPlayingAt),
        )
        val (lead, mirror) = if (twinLeads) twin to s else s to twin
        if (mirror != null) {
            lead.led = true
            mirror.led = false
        }
        lead.volumeVia = mirror?.takeIf { it.isRemote() && !lead.isRemote() }
        var np = safely("now playing") { build(lead) }
        if (np != null && mirror != null) {
            if (np.art == null && mirror.art != null) np = np.copy(art = mirror.art)
            // Without a name of its own, the speaker is called after its app: "Sonos".
            if (np.remote == null) remoteOf(mirror)?.let { r -> np = np?.copy(remote = r.copy(name = r.name ?: apps.label(mirror.controller.packageName))) }
        }
        return np
    }

    /** Two sessions with one song: the same title, and the same artist when both say one. */
    private fun sameSong(a: Session, b: Session): Boolean {
        val ta = a.title()?.trim() ?: return false
        val tb = b.title()?.trim() ?: return false
        if (!ta.equals(tb, ignoreCase = true)) return false
        val aa = a.metadata?.text(MediaMetadata.METADATA_KEY_ARTIST)?.lowercase()
        val ab = b.metadata?.text(MediaMetadata.METADATA_KEY_ARTIST)?.lowercase()
        return aa == null || ab == null || aa.contains(ab) || ab.contains(aa)
    }

    private fun Session.isRemote(): Boolean =
        safely("playback info") { controller.playbackInfo?.playbackType } == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE

    private fun Session.namesSpeaker(): Boolean = safely("session extras") {
        val extras = controller.extras
        !extras?.getString(EXTRA_REMOTE_NAME).isNullOrBlank() || !extras?.getString(EXTRA_REMOTE_NAME_SHORT).isNullOrBlank()
    } == true

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
        // The shuffle and repeat a player keeps as a session (Media3 and MediaSessionCompat players: PixelPlayer,
        // YouTube Music, Poweramp, Musicolet...) are the truth, changed here or in the player itself. Only players that
        // offer nothing but a button of their own fall back on reading that button.
        val standard = s.compat?.takeIf { it.isSessionReady }
        val customShuffle = shuffle?.let { a ->
            val stated = shuffleOfAction(a, s.controller.packageName)
            if (stated == ShuffleMode.SMART) s.threeWayShuffle = true
            stated
        }
        val shuffleMode = when {
            standard != null && actions and ACTION_SET_SHUFFLE_MODE != 0L && !s.threeWayShuffle -> {
                val on = standard.shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL || standard.shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_GROUP
                followPress(if (on) ShuffleMode.ON else ShuffleMode.OFF, s.pressedShuffle, justNow(s.shuffleAt), actionUnchanged = false)
            }
            shuffle != null -> followPress(customShuffle, s.pressedShuffle, justNow(s.shuffleAt), signatureOf(shuffle) == s.shuffleSignature) ?: ShuffleMode.OFF
            else -> null
        }
        val repeatMode = when {
            standard != null && actions and ACTION_SET_REPEAT_MODE != 0L -> {
                val mode = when (standard.repeatMode) {
                    PlaybackStateCompat.REPEAT_MODE_ONE -> RepeatMode.ONE
                    PlaybackStateCompat.REPEAT_MODE_ALL, PlaybackStateCompat.REPEAT_MODE_GROUP -> RepeatMode.ALL
                    else -> RepeatMode.OFF
                }
                followPress(mode, s.pressedRepeat, justNow(s.repeatAt), actionUnchanged = false)
            }
            repeat != null -> followPress(repeatOfAction(repeat, s.controller.packageName), s.pressedRepeat, justNow(s.repeatAt), signatureOf(repeat) == s.repeatSignature)
                ?: RepeatMode.OFF
            else -> null
        }
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
            repeat = repeatMode,
            shuffle = shuffleMode?.let { it != ShuffleMode.OFF },
            smartShuffle = shuffleMode == ShuffleMode.SMART,
            liked = likedOf(s, m, rated, like, packageName, title),
            next = nextInQueue(s.queue, state?.activeQueueItemId ?: MediaSession.QueueItem.UNKNOWN_ID.toLong()),
            commands = s.commands,
            embeddedLyrics = lyricsOf(m),
            mediaId = m.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
            mediaUri = m.getString(MediaMetadata.METADATA_KEY_MEDIA_URI),
            remote = remoteOf(s),
            hidesIsland = safely("session extras") { s.controller.extras?.getBoolean(EXTRA_HIDE_ISLAND) } == true,
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

    /**
     * Playing on a speaker elsewhere: the player tells Android its volume (a Sonos group, a Chromecast), which is then
     * that speaker's and not the phone's. The name comes from the session's extras when the player gives it.
     */
    private fun remoteOf(s: Session): RemoteVolume? = safely("remote volume") {
        val info = s.controller.playbackInfo ?: return@safely null
        if (info.playbackType != MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE || info.maxVolume <= 0) return@safely null
        val extras = s.controller.extras
        val name = (extras?.getString(EXTRA_REMOTE_NAME) ?: extras?.getString(EXTRA_REMOTE_NAME_SHORT))?.trim()?.takeIf { it.isNotEmpty() }
            ?: (if (Build.VERSION.SDK_INT >= 30) info.volumeControlId?.trim()?.takeIf { it.isNotEmpty() && ' ' in it } else null)
        RemoteVolume(
            level = info.currentVolume.toFloat() / info.maxVolume,
            steps = info.maxVolume,
            name = name,
            adjustable = info.volumeControl != android.media.VolumeProvider.VOLUME_CONTROL_FIXED,
        )
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

    private fun shuffleOfAction(a: PlaybackState.CustomAction, packageName: String): ShuffleMode? =
        media3Icon(a)?.let(::shuffleModeOfMedia3Icon)
            ?: shuffleModeOfIconName(iconName(packageName, a.icon))
            ?: shuffleStatedBy(a.action, a.name?.toString())?.let { if (it) ShuffleMode.ON else ShuffleMode.OFF }

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
        var pressedShuffle: ShuffleMode? = null
        /** Shuffle here has a third way, smart shuffle: Spotify has it, and any player that once showed it. */
        var threeWayShuffle = controller.packageName.startsWith("com.spotify")
        var shuffleAt = 0L
        var shuffleSignature: String? = null
        var pressedRepeat: RepeatMode? = null
        var repeatAt = 0L
        var repeatSignature: String? = null
        var metadata: MediaMetadata? = null
        var state: PlaybackState? = null
        var lastPlayingAt = 0L
        /** Led the last time this session shared its song with another one. Kept when the two part for a moment. */
        var led = false
        var art: Bitmap? = null
        /** Rating.RATING_NONE and the like. Read with the song, since a podcast in the same app may take no heart. */
        var ratingType = Rating.RATING_NONE
        /** Kept from the callback: a long queue is a big binder call, too much to ask for on every state change. */
        var queue: List<QueueEntry> = emptyList()
        private var artSource: Bitmap? = null
        private var artUri: String? = null

        /** Loads a cover from a link off the main thread, and shows it if the song still has that link by then. */
        private fun loadArt(uri: String) = safely("cover loader") {
            artLoader.execute {
                val bitmap = safely("cover from $uri") {
                    context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { input ->
                        android.graphics.BitmapFactory.decodeStream(input)
                    }?.fitWithin(ART_MAX_PX)
                }
                handler.post {
                    if (artUri == uri && bitmap != null) {
                        art = bitmap
                        update()
                    }
                }
            }
        }
        val commands = Commands(this)
        /** The speaker app's session that holds the volume while this player plays on that speaker. */
        var volumeVia: Session? = null

        /** The same session seen through MediaControllerCompat, which also knows its shuffle and repeat modes. */
        var compat: MediaControllerCompat? = null
        private val compatCallback = object : MediaControllerCompat.Callback() {
            override fun onShuffleModeChanged(shuffleMode: Int) = update()
            override fun onRepeatModeChanged(repeatMode: Int) = update()
            override fun onSessionReady() = update()
        }

        fun attach() {
            // Before the reads: a player that has just started tells its song right now, and would go unnoticed in between.
            safely("media callback") { controller.registerCallback(this, handler) }
            metadata = safely("metadata") { controller.metadata }
            state = safely("playback state") { controller.playbackState }
            if (isActive(state)) lastPlayingAt = SystemClock.elapsedRealtime()
            readQueue(safely("queue") { controller.queue })
            readRatingType()
            readArt()
            compat = safely("compat controller") {
                MediaControllerCompat(context, MediaSessionCompat.Token.fromToken(controller.sessionToken)).also {
                    it.registerCallback(compatCallback, handler)
                }
            }
        }

        fun release() {
            safely("media callback") { controller.unregisterCallback(this) }
            safely("compat callback") { compat?.unregisterCallback(compatCallback) }
            compat = null
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

        // A speaker's volume moved: the buttons of the phone, the speaker itself, or Drip.
        override fun onAudioInfoChanged(info: MediaController.PlaybackInfo) = update()

        // The name of the speaker can come along in the session's extras, and the wish to hide the island.
        override fun onExtrasChanged(extras: android.os.Bundle?) = update()

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
                artSource = null
                // No picture, but maybe a link to one on the phone: a player's own artwork provider, or a file.
                val uri = listOf(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
                    .firstNotNullOfOrNull { m?.getString(it)?.takeIf { u -> u.startsWith("content://") || u.startsWith("file://") } }
                if (uri == null) {
                    art = null
                    artUri = null
                } else if (uri != artUri) {
                    artUri = uri
                    art = null
                    loadArt(uri)
                }
                return
            }
            artUri = null
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
            val standard = s.compat?.takeIf { it.isSessionReady && actions() and ACTION_SET_REPEAT_MODE != 0L }
            if (standard != null) {
                val mode = when (s.pressedRepeat) {
                    RepeatMode.ONE -> PlaybackStateCompat.REPEAT_MODE_ONE
                    RepeatMode.ALL -> PlaybackStateCompat.REPEAT_MODE_ALL
                    else -> PlaybackStateCompat.REPEAT_MODE_NONE
                }
                safely("repeat mode") { standard.transportControls.setRepeatMode(mode) }
            } else {
                custom(CustomKind.REPEAT)
            }
        }

        override fun toggleShuffle() {
            val shown = safely("shuffle") { build(s) }?.let {
                when {
                    it.smartShuffle -> ShuffleMode.SMART
                    it.shuffle == true -> ShuffleMode.ON
                    else -> ShuffleMode.OFF
                }
            } ?: ShuffleMode.OFF
            s.pressedShuffle = nextShuffle(shown, s.threeWayShuffle)
            s.shuffleAt = SystemClock.elapsedRealtime()
            s.shuffleSignature = action(CustomKind.SHUFFLE)?.let(::signatureOf)
            handler.post { update() }
            // Spotify's three ways only go round through its own button; the standard mode knows just on and off.
            val standard = s.compat?.takeIf { it.isSessionReady && actions() and ACTION_SET_SHUFFLE_MODE != 0L && !s.threeWayShuffle }
            if (standard != null) {
                val mode = if (s.pressedShuffle == ShuffleMode.OFF) PlaybackStateCompat.SHUFFLE_MODE_NONE else PlaybackStateCompat.SHUFFLE_MODE_ALL
                safely("shuffle mode") { standard.transportControls.setShuffleMode(mode) }
            } else {
                custom(CustomKind.SHUFFLE)
            }
        }

        private fun actions() = s.controller.playbackState?.actions ?: 0L

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

        /** The session that holds the speaker's volume: this one, or the speaker app's alongside it. */
        private val volume get() = (s.volumeVia ?: s).controller

        override fun setRemoteVolume(level: Float) {
            val info = safely("remote volume") { volume.playbackInfo } ?: return
            if (info.maxVolume <= 0) return
            ownVolumeAt = SystemClock.elapsedRealtime()
            safely("set remote volume") { volume.setVolumeTo(Math.round(level.coerceIn(0f, 1f) * info.maxVolume), 0) }
        }

        override fun nudgeRemoteVolume(direction: Int) {
            ownVolumeAt = SystemClock.elapsedRealtime()
            safely("nudge remote volume") { volume.adjustVolume(direction, 0) }
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
        /** More players than this at once is a phone full of forgotten sessions, not music. */
        const val MAX_PLAYERS = 3
        /**
         * Where a player can name the speaker it plays on, in its session's extras: "Living room + Kitchen". Drip's own
         * key, and a short one for players that would rather not carry a package name around.
         */
        const val EXTRA_REMOTE_NAME = "nl.markmaaktmedia.drip.REMOTE_NAME"
        const val EXTRA_REMOTE_NAME_SHORT = "remote_name"
        const val EXTRA_HIDE_ISLAND = "nl.markmaaktmedia.drip.HIDE_ISLAND"
        /** PlaybackStateCompat's actions for the shuffle and repeat modes, which reach the framework state as bits. */
        const val ACTION_SET_SHUFFLE_MODE = PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE
        const val ACTION_SET_REPEAT_MODE = PlaybackStateCompat.ACTION_SET_REPEAT_MODE

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
