package nl.markmaaktmedia.drip.sources

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.FileInputStream
import kotlin.math.abs

/**
 * The music file of the song that plays, for players of your own files (PixelPlayer and the like), whose lyrics live in
 * the file and not in what they tell Android. Needs permission to see music; streaming apps have no file to find.
 */
internal object LocalSongs {

    val permission: String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    fun allowed(context: Context): Boolean = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /** Apps that stream, where a file on the phone would only be a coincidence. */
    private val streaming = listOf(
        "com.spotify", "com.google.android.apps.youtube", "com.deezer", "com.aspiro.tidal", "com.apple.android.music",
        "com.soundcloud", "com.amazon.mp3", "com.qobuz", "com.pandora", "com.audible", "com.google.android.youtube",
        "au.com.shiftyjelly.pocketcasts", "com.bbc.sounds", "com.google.android.apps.podcasts", "com.anghami", "com.napster",
    )

    /** Whether the song may well be a file on the phone: the player says where it is, or it is not a streaming app. */
    fun mayBeLocal(packageName: String, mediaId: String?, mediaUri: String?): Boolean =
        mediaUri?.startsWith("content://") == true || mediaUri?.startsWith("file://") == true ||
            mediaId?.toLongOrNull() != null || streaming.none { packageName.startsWith(it) }

    /** Lyrics from the song's file, LRC when timed, or null when there is no file or nothing in it. */
    fun lyrics(context: Context, title: String, artist: String, durationMs: Long, mediaId: String?, mediaUri: String?): String? {
        if (!allowed(context)) return null
        val uri = find(context, title, artist, durationMs, mediaId, mediaUri) ?: return null
        return safely("read tags") {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                FileInputStream(fd.fileDescriptor).channel.use { AudioTags.lyrics(it) }
            }
        }
    }

    /** The player's own pointer to the file first, then its id as a MediaStore id, then the title and artist. */
    private fun find(context: Context, title: String, artist: String, durationMs: Long, mediaId: String?, mediaUri: String?): Uri? {
        val audio = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        mediaUri?.let { Uri.parse(it) }?.takeIf { it.scheme == "content" || it.scheme == "file" }?.let { return it }
        mediaId?.takeIf { it.startsWith("content://") }?.let { return Uri.parse(it) }
        mediaId?.toLongOrNull()?.let { id ->
            val uri = ContentUris.withAppendedId(audio, id)
            if (titleOf(context, uri)?.equals(title, ignoreCase = true) == true) return uri
        }
        return safely("find song") {
            context.contentResolver.query(
                audio,
                arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION),
                "LOWER(${MediaStore.Audio.Media.TITLE}) = LOWER(?)", arrayOf(title), null,
            )?.use { c ->
                var best: Pair<Long, Long>? = null
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val rowArtist = c.getString(1).orEmpty()
                    val gap = if (durationMs > 0) abs(c.getLong(2) - durationMs) else 0L
                    // The same song by someone else, or another recording of it, is not this one.
                    if (artist.isNotBlank() && rowArtist.isNotBlank() && !sameArtist(rowArtist, artist)) continue
                    if (durationMs > 0 && gap > 4000) continue
                    if (best == null || gap < best.second) best = id to gap
                }
                best?.let { ContentUris.withAppendedId(audio, it.first) }
            }
        }
    }

    private fun sameArtist(a: String, b: String): Boolean {
        val x = a.lowercase()
        val y = b.lowercase()
        return x == y || x.contains(firstArtist(b).lowercase()) || y.contains(firstArtist(a).lowercase())
    }

    private fun titleOf(context: Context, uri: Uri): String? = safely("song title") {
        context.contentResolver.query(uri, arrayOf(MediaStore.Audio.Media.TITLE), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }
}
