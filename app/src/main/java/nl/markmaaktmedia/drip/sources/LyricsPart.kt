package nl.markmaaktmedia.drip.sources

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.markmaaktmedia.drip.BuildConfig
import nl.markmaaktmedia.drip.island.LyricsState
import nl.markmaaktmedia.drip.island.NowPlaying

/**
 * The lyrics of the song that plays, but only while they are open under the music card. What the player put in the
 * song comes first; otherwise lrclib.net, when looking online is on. Answers are kept for the last songs, so going back
 * to one costs nothing.
 */
internal class LyricsPart(env: SourceEnv) : Part(env) {
    private var scope: CoroutineScope? = null
    private val wanted = MutableStateFlow(Wanted(show = false, online = false))
    private val retry = MutableStateFlow(0)
    private val lrclib = LrcLib("Drip/${BuildConfig.VERSION_NAME} (https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO})")
    private val cache = object : LinkedHashMap<String, LyricsState>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LyricsState>?) = size > 40
    }

    private data class Wanted(val show: Boolean, val online: Boolean)

    /** What of the song matters for its lyrics; the rest of NowPlaying changes all the time. */
    private data class Song(val key: String, val title: String, val artist: String, val album: String?, val durationMs: Long, val embedded: String?)

    override fun start() {
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = s
        hub.retryLyrics = { retry.value++ }
        s.launch {
            val song = hub.nowPlaying.map { it?.let(::songOf) }.distinctUntilChanged()
            combine(song, hub.lyricsOpen, wanted, retry) { np, open, w, _ -> Triple(np, open, w) }
                .collectLatest { (np, open, w) -> resolve(np, open, w) }
        }
    }

    override fun stop() {
        scope?.cancel()
        scope = null
        hub.retryLyrics = null
        hub.lyrics.value = LyricsState.Off
    }

    override fun onSettings() {
        val s = settings ?: return
        wanted.value = Wanted(s.showLyrics, s.lyricsOnline)
    }

    private fun songOf(np: NowPlaying) = Song(
        songKey(np.title, np.artist, np.durationMs), np.title, np.artist, np.album, np.durationMs, np.embeddedLyrics,
    )

    private suspend fun resolve(song: Song?, open: Boolean, w: Wanted) {
        if (song == null || !open || !w.show) {
            if (song == null || !w.show) hub.lyrics.value = LyricsState.Off
            return
        }
        val key = song.key
        song.embedded?.let { text ->
            val lines = if (looksSynced(text)) parseLrc(text) else plainLines(text)
            if (lines.isNotEmpty()) {
                hub.lyrics.value = LyricsState.Found(key, lines, synced = lines.any { it.timeMs > 0 }, online = false)
                return
            }
        }
        cache[key]?.let {
            hub.lyrics.value = it
            return
        }
        if (!w.online) {
            hub.lyrics.value = LyricsState.AskOnline(key)
            return
        }
        hub.lyrics.value = LyricsState.Loading(key)
        val answer = withContext(Dispatchers.IO) {
            runCatching { lrclib.find(song.title, song.artist, song.album, song.durationMs / 1000) }
        }
        val state = answer.fold(
            onSuccess = { a ->
                when {
                    a == null -> LyricsState.Missing(key)
                    a.synced != null -> LyricsState.Found(key, parseLrc(a.synced), synced = true, online = true)
                    a.plain != null -> LyricsState.Found(key, plainLines(a.plain), synced = false, online = true)
                    else -> LyricsState.Missing(key, instrumental = a.instrumental)
                }
            },
            onFailure = { LyricsState.Failed(key) },
        )
        if (state !is LyricsState.Failed) cache[key] = state
        hub.lyrics.value = state
    }
}
