package nl.markmaaktmedia.drip.sources

import nl.markmaaktmedia.drip.island.LyricLine

/** [mm:ss.xx], [m:ss] or [mm:ss:xx] at the start of an LRC line. Tags like [ar:Artist] have no digits there. */
private val stamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

/** Word timings of enhanced LRC, <mm:ss.xx>, which the island does not use. */
private val wordStamp = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")

/**
 * The lines of synced lyrics in LRC, oldest first. A line with several stamps (a chorus written once) is there at each
 * of them. Empty lines stay: they are the pauses between verses.
 */
internal fun parseLrc(text: String): List<LyricLine> {
    val out = mutableListOf<LyricLine>()
    text.lineSequence().forEach { raw ->
        var rest = raw.trim()
        val times = mutableListOf<Long>()
        while (true) {
            val m = stamp.matchAt(rest, 0) ?: break
            val (min, sec, frac) = m.destructured
            val fraction = when (frac.length) {
                1 -> frac.toLong() * 100
                2 -> frac.toLong() * 10
                3 -> frac.toLong()
                else -> 0L
            }
            times += min.toLong() * 60_000L + sec.toLong() * 1000L + fraction
            rest = rest.substring(m.range.last + 1).trimStart()
        }
        if (times.isEmpty()) return@forEach
        val words = rest.replace(wordStamp, "").replace(Regex("\\s+"), " ").trim()
        times.forEach { out += LyricLine(it, words) }
    }
    return out.sortedBy { it.timeMs }
}

/** Whether [text] is LRC, with time stamps, rather than plain lines. */
internal fun looksSynced(text: String): Boolean = text.lineSequence().count { stamp.matchAt(it.trim(), 0) != null } >= 3

/** Plain lyrics as lines without times; the empty ones between verses stay as one. */
internal fun plainLines(text: String): List<LyricLine> =
    text.lines().map { it.trim() }
        .fold(mutableListOf<String>()) { acc, l -> if (!(l.isEmpty() && acc.lastOrNull()?.isEmpty() == true)) acc += l; acc }
        .dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() }
        .map { LyricLine(0L, it) }

/** Which song, as far as lyrics go: the same title and artist at the same length are the same lyrics. */
internal fun songKey(title: String, artist: String, durationMs: Long): String =
    "${title.trim().lowercase()}|${artist.trim().lowercase()}|${durationMs / 1000}"

private val featuring = Regex("""\s*[(\[](?:feat\.?|ft\.?|with|met)\s[^)\]]*[)\]]""", RegexOption.IGNORE_CASE)
private val versionTail = Regex("""\s+-\s+[^-]*\b(?:remaster(?:ed)?|version|edit|live|mix|mono|stereo|acoustic|radio)\b.*$""", RegexOption.IGNORE_CASE)
private val versionBrackets = Regex("""\s*[(\[][^)\]]*\b(?:remaster(?:ed)?|version|edit|live|mono|stereo)\b[^)\]]*[)\]]""", RegexOption.IGNORE_CASE)

/** The title without what streaming services add to it, for a second, looser search: "Song (feat. X) - Remastered 2011" is "Song". */
internal fun cleanTitle(title: String): String =
    title.replace(featuring, "").replace(versionTail, "").replace(versionBrackets, "").trim().ifEmpty { title.trim() }

/** The first of several artists, "A, B & C" is "A". */
internal fun firstArtist(artist: String): String =
    artist.split(", ", " & ", " feat. ", " ft. ", " x ", " X ", "; ").first().trim().ifEmpty { artist.trim() }
