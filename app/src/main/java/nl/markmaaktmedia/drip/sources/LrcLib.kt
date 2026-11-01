package nl.markmaaktmedia.drip.sources

import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject

/**
 * lrclib.net, a free and open collection of lyrics, many of them synced. Only asked when the person switched looking
 * online on. It gets the title, the artist, the album and the length of the song, nothing else.
 */
internal class LrcLib(private val userAgent: String) {

    /** What the collection has for a song. [synced] in LRC when there is a timed version, else [plain]. */
    data class Answer(val synced: String?, val plain: String?, val instrumental: Boolean)

    /**
     * The exact song first, then without the album, then a looser search on the cleaned title and the first artist that
     * takes the result closest in length. Null when nothing fits; an IOException when the site cannot be reached.
     */
    fun find(title: String, artist: String, album: String?, durationSec: Long): Answer? {
        if (durationSec > 0) {
            get(mapOf("track_name" to title, "artist_name" to artist, "album_name" to album, "duration" to durationSec.toString()))?.let { return it }
            get(mapOf("track_name" to title, "artist_name" to artist, "duration" to durationSec.toString()))?.let { return it }
        }
        return search(cleanTitle(title), firstArtist(artist), durationSec)
    }

    private fun get(params: Map<String, String?>): Answer? {
        val body = request("api/get", params) ?: return null
        return answerOf(JSONObject(body))
    }

    private fun search(title: String, artist: String, durationSec: Long): Answer? {
        val body = request("api/search", mapOf("track_name" to title, "artist_name" to artist)) ?: return null
        val results = JSONArray(body)
        val candidates = (0 until results.length()).map { results.getJSONObject(it) }
            // A different length is a different recording, whose timings would be off.
            .filter { durationSec <= 0 || abs(it.optDouble("duration", 0.0) - durationSec) <= 4 }
        val best = candidates.firstOrNull { !it.isNull("syncedLyrics") } ?: candidates.firstOrNull { !it.isNull("plainLyrics") }
        return best?.let(::answerOf)
    }

    private fun answerOf(o: JSONObject): Answer? {
        val synced = o.optString("syncedLyrics").takeIf { !o.isNull("syncedLyrics") && it.isNotBlank() }
        val plain = o.optString("plainLyrics").takeIf { !o.isNull("plainLyrics") && it.isNotBlank() }
        val instrumental = o.optBoolean("instrumental", false)
        if (synced == null && plain == null && !instrumental) return null
        return Answer(synced, plain, instrumental)
    }

    /** The body of a GET, or null for a song the site does not know (404). */
    private fun request(path: String, params: Map<String, String?>): String? {
        val query = params.filterValues { !it.isNullOrBlank() }
            .entries.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }
        val connection = URL("$BASE$path?$query").openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "application/json")
            when (connection.responseCode) {
                HttpURLConnection.HTTP_NOT_FOUND -> null
                in 200..299 -> connection.inputStream.bufferedReader().use { it.readText() }
                else -> throw FileNotFoundException("lrclib answered ${connection.responseCode}")
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val BASE = "https://lrclib.net/"
        const val TIMEOUT_MS = 8000
    }
}
