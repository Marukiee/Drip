package nl.markmaaktmedia.drip.sources

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SeekableByteChannel
import java.nio.charset.Charset

/**
 * Lyrics written into a music file: the Vorbis comments of FLAC, Ogg and Opus (LYRICS, UNSYNCEDLYRICS), the USLT, SYLT
 * and TXXX frames of an MP3's ID3 tag, and the ©lyr atom of an M4A. Only the tags are read, never the music, and
 * pictures and other large blocks are stepped over without loading them.
 *
 * Synced lyrics come back as LRC text, so they go through the same reading as lyrics from anywhere else.
 */
internal object AudioTags {

    fun lyrics(file: SeekableByteChannel): String? {
        val head = read(file, 0, 12) ?: return null
        return when {
            head.startsWith("fLaC") -> flac(file, 0)
            head.startsWith("ID3") -> id3(file) ?: afterId3(file)?.let { if (startsAt(file, it, "fLaC")) flac(file, it) else null }
            head.startsWith("OggS") -> ogg(file)
            head.copyOfRange(4, 8).contentEquals("ftyp".toByteArray()) -> mp4(file)
            else -> null
        }
    }

    // ---- FLAC: metadata blocks after "fLaC", the comments in block type 4.

    private fun flac(file: SeekableByteChannel, start: Long): String? {
        var at = start + 4
        while (true) {
            val header = read(file, at, 4) ?: return null
            val last = header[0].toInt() and 0x80 != 0
            val type = header[0].toInt() and 0x7f
            val length = ((header[1].toInt() and 0xff) shl 16) or ((header[2].toInt() and 0xff) shl 8) or (header[3].toInt() and 0xff)
            if (type == 4) return read(file, at + 4, length)?.let(::vorbisComments)
            if (last) return null
            at += 4 + length
        }
    }

    /** vendor, then KEY=value pairs, all lengths little endian. */
    private fun vorbisComments(data: ByteArray): String? {
        val b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        return runCatching {
            val vendor = b.int
            b.position(b.position() + vendor)
            val comments = (0 until b.int).map {
                val bytes = ByteArray(b.int)
                b.get(bytes)
                String(bytes, Charsets.UTF_8)
            }
            fun value(vararg keys: String) = keys.firstNotNullOfOrNull { key ->
                comments.firstOrNull { it.substringBefore('=').equals(key, ignoreCase = true) }?.substringAfter('=')?.takeIf { it.isNotBlank() }
            }
            value("SYNCEDLYRICS", "LYRICS", "UNSYNCEDLYRICS", "UNSYNCED LYRICS", "LYRICS-ENG")
        }.getOrNull()
    }

    // ---- Ogg Vorbis and Opus: the comment packet, the second one in the stream.

    private fun ogg(file: SeekableByteChannel): String? {
        val packets = mutableListOf<java.io.ByteArrayOutputStream>(java.io.ByteArrayOutputStream())
        var at = 0L
        var pages = 0
        while (pages++ < 64 && packets.size < 3) {
            val header = read(file, at, 27) ?: return null
            if (!header.startsWith("OggS")) return null
            val segments = header[26].toInt() and 0xff
            val table = read(file, at + 27, segments) ?: return null
            var bodyAt = at + 27 + segments
            for (s in table) {
                val size = s.toInt() and 0xff
                val bytes = read(file, bodyAt, size) ?: return null
                packets.last().write(bytes)
                bodyAt += size
                // A segment shorter than 255 ends its packet.
                if (size < 255) packets += java.io.ByteArrayOutputStream()
                if (packets.last().size() > MAX_TAG) return null
            }
            at = bodyAt
        }
        val comments = packets.getOrNull(1)?.toByteArray() ?: return null
        return when {
            comments.size > 7 && comments[0].toInt() == 3 && String(comments, 1, 6) == "vorbis" -> vorbisComments(comments.copyOfRange(7, comments.size))
            comments.startsWith("OpusTags") -> vorbisComments(comments.copyOfRange(8, comments.size))
            else -> null
        }
    }

    // ---- MP3: ID3v2.3 and 2.4 frames.

    private fun afterId3(file: SeekableByteChannel): Long? {
        val h = read(file, 0, 10) ?: return null
        return 10L + syncsafe(h, 6) + if (h[5].toInt() and 0x10 != 0) 10 else 0
    }

    private fun id3(file: SeekableByteChannel): String? {
        val h = read(file, 0, 10) ?: return null
        val major = h[3].toInt()
        if (major != 3 && major != 4) return null
        val end = 10L + syncsafe(h, 6)
        var at = 10L
        // An extended header, rare, comes first.
        if (h[5].toInt() and 0x40 != 0) {
            val ext = read(file, at, 4) ?: return null
            at += if (major == 4) syncsafe(ext, 0).toLong() else 4L + ByteBuffer.wrap(ext).int
        }
        var plain: String? = null
        var synced: String? = null
        while (at + 10 <= end) {
            val f = read(file, at, 10) ?: break
            if (f[0].toInt() == 0) break
            val id = String(f, 0, 4, Charsets.ISO_8859_1)
            val size = if (major == 4) syncsafe(f, 4) else ByteBuffer.wrap(f, 4, 4).int
            if (size <= 0 || at + 10 + size > end) break
            if (id == "USLT" || id == "SYLT" || id == "TXXX") {
                var body = read(file, at + 10, size) ?: break
                // ID3v2.4 may put the frame's own length in front of it.
                if (major == 4 && f[9].toInt() and 0x01 != 0 && body.size > 4) body = body.copyOfRange(4, body.size)
                when (id) {
                    "USLT" -> plain = plain ?: uslt(body)
                    "SYLT" -> synced = synced ?: sylt(body)
                    "TXXX" -> txxx(body)?.let { (name, value) ->
                        when (name.uppercase()) {
                            "SYNCEDLYRICS", "SYNCED LYRICS" -> synced = synced ?: value
                            "LYRICS", "UNSYNCEDLYRICS", "UNSYNCED LYRICS" -> plain = plain ?: value
                        }
                    }
                }
            }
            at += 10 + size
        }
        // A USLT that is LRC already counts as synced.
        return synced ?: plain
    }

    private fun uslt(body: ByteArray): String? {
        if (body.size < 5) return null
        val encoding = body[0].toInt()
        val descriptorEnd = terminator(body, 4, encoding)
        return decode(body, descriptorEnd.second, body.size, encoding).takeIf { it.isNotBlank() }
    }

    /** Timed text, written out as LRC; only when the times are milliseconds (format 2), not frames. */
    private fun sylt(body: ByteArray): String? {
        if (body.size < 7) return null
        val encoding = body[0].toInt()
        if (body[4].toInt() != 2) return null
        var at = terminator(body, 6, encoding).second
        val lines = StringBuilder()
        while (at < body.size) {
            val (textEnd, next) = terminator(body, at, encoding)
            if (next + 4 > body.size) break
            val text = decode(body, at, textEnd, encoding).trim()
            val ms = ByteBuffer.wrap(body, next, 4).int.toLong()
            lines.append("[%02d:%02d.%02d]%s\n".format(ms / 60_000, ms / 1000 % 60, ms % 1000 / 10, text))
            at = next + 4
        }
        return lines.toString().takeIf { it.isNotBlank() }
    }

    private fun txxx(body: ByteArray): Pair<String, String>? {
        if (body.size < 2) return null
        val encoding = body[0].toInt()
        val (nameEnd, valueAt) = terminator(body, 1, encoding)
        return decode(body, 1, nameEnd, encoding) to decode(body, valueAt, body.size, encoding)
    }

    /** Where a text in [encoding] that starts at [from] ends, and where what follows it begins. */
    private fun terminator(b: ByteArray, from: Int, encoding: Int): Pair<Int, Int> {
        val wide = encoding == 1 || encoding == 2
        var i = from
        while (i < b.size) {
            if (wide) {
                if (i + 1 < b.size && b[i].toInt() == 0 && b[i + 1].toInt() == 0) return i to i + 2
                i += 2
            } else {
                if (b[i].toInt() == 0) return i to i + 1
                i++
            }
        }
        return b.size to b.size
    }

    private fun decode(b: ByteArray, from: Int, to: Int, encoding: Int): String {
        if (to <= from) return ""
        val charset: Charset = when (encoding) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        return String(b, from, to - from, charset).trimEnd('\u0000')
    }

    private fun syncsafe(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0x7f) shl 21) or ((b[at + 1].toInt() and 0x7f) shl 14) or
            ((b[at + 2].toInt() and 0x7f) shl 7) or (b[at + 3].toInt() and 0x7f)

    // ---- M4A: moov > udta > meta > ilst > ©lyr > data.

    private fun mp4(file: SeekableByteChannel): String? {
        val moov = atom(file, 0, file.size(), "moov") ?: return null
        val udta = atom(file, moov.first, moov.second, "udta") ?: return null
        val meta = atom(file, udta.first, udta.second, "meta") ?: return null
        // meta is a full atom: four bytes of version and flags before its children.
        val ilst = atom(file, meta.first + 4, meta.second, "ilst") ?: return null
        val lyr = atom(file, ilst.first, ilst.second, "©lyr") ?: return null
        val data = atom(file, lyr.first, lyr.second, "data") ?: return null
        // data: four bytes of type, four of locale, then the text.
        val length = (data.second - data.first - 8).toInt()
        if (length <= 0 || length > MAX_TAG) return null
        return read(file, data.first + 8, length)?.let { String(it, Charsets.UTF_8) }?.takeIf { it.isNotBlank() }
    }

    /** The content of the first child atom named [type] between [from] and [to], as start and end. */
    private fun atom(file: SeekableByteChannel, from: Long, to: Long, type: String): Pair<Long, Long>? {
        val name = type.toByteArray(Charsets.ISO_8859_1)
        var at = from
        while (at + 8 <= to) {
            val h = read(file, at, 8) ?: return null
            var size = ByteBuffer.wrap(h, 0, 4).int.toLong() and 0xffffffffL
            var header = 8L
            if (size == 1L) {
                size = ByteBuffer.wrap(read(file, at + 8, 8) ?: return null).long
                header = 16L
            } else if (size == 0L) {
                size = to - at
            }
            if (size < header) return null
            if (h.copyOfRange(4, 8).contentEquals(name)) return (at + header) to (at + size)
            at += size
        }
        return null
    }

    // ---- reading

    private fun read(file: SeekableByteChannel, at: Long, length: Int): ByteArray? {
        if (length < 0 || length > MAX_TAG || at < 0 || at + length > file.size()) return null
        val buffer = ByteBuffer.allocate(length)
        file.position(at)
        while (buffer.hasRemaining()) if (file.read(buffer) < 0) return null
        return buffer.array()
    }

    private fun startsAt(file: SeekableByteChannel, at: Long, magic: String): Boolean =
        read(file, at, magic.length)?.startsWith(magic) == true

    private fun ByteArray.startsWith(magic: String): Boolean {
        val m = magic.toByteArray(Charsets.ISO_8859_1)
        return size >= m.size && m.indices.all { this[it] == m[it] }
    }

    /** No tag Drip reads is this large; anything bigger is a picture or a broken file. */
    private const val MAX_TAG = 4 * 1024 * 1024
}
