package nl.markmaaktmedia.drip.sources

import nl.markmaaktmedia.drip.island.RepeatMode

/**
 * The framework MediaController has no repeat, shuffle or like of its own. Players that offer them do so as custom
 * actions, named however they like, so these rules read the action string and the name.
 */
enum class CustomKind { REPEAT, SHUFFLE, LIKE }

private val camelHump = Regex("([a-z])([A-Z])")
private val nonWord = Regex("[^\\p{L}\\p{N}]+")

/** Lower case words split by single spaces, padded with a space on both ends so " on " finds a whole word. */
private fun words(vararg parts: String?): String {
    val joined = parts.joinToString(" ") { it.orEmpty() }.replace(camelHump, "$1 $2").lowercase()
    return " " + joined.replace(nonWord, " ").trim() + " "
}

private fun String.hasWord(vararg w: String) = w.any { contains(" $it ") }
private fun String.hasPart(vararg w: String) = w.any { contains(it) }

private val likeParts = listOf("like", "favorite", "favourite", "favoriet", "heart", "thumbs up", "thumb up", "leuk")
private val dislikeParts = listOf("dislike", "thumbs down", "thumb down", "niet leuk")

internal fun customKindOf(action: String?, name: String?): CustomKind? {
    val t = words(action, name)
    return when {
        t.hasPart("shuffle", "willekeurig") -> CustomKind.SHUFFLE
        t.hasPart("repeat", "herhaal", "herhalen") || t.hasWord("loop") -> CustomKind.REPEAT
        likeParts.any { it in t } && dislikeParts.none { it in t } -> CustomKind.LIKE
        else -> null
    }
}

/** Players name the repeat button after the mode it is in. A bare "repeat" says nothing, so that reads as off. */
internal fun repeatModeOf(action: String?, name: String?): RepeatMode {
    val t = words(action, name)
    return when {
        t.hasWord("one", "single", "track", "song", "nummer", "1") -> RepeatMode.ONE
        t.hasWord("off", "none", "disabled", "uit", "geen") -> RepeatMode.OFF
        t.hasWord("all", "list", "queue", "playlist", "alles", "alle", "on", "aan") -> RepeatMode.ALL
        else -> RepeatMode.OFF
    }
}

/** "Turn off shuffle" means it is on now; "shuffle on" means it is on. */
internal fun shuffleOnOf(action: String?, name: String?): Boolean {
    val t = words(action, name)
    return when {
        t.hasWord("disable", "deactivate", "uitzetten", "uitschakelen") || t.hasPart("turn off") -> true
        t.hasWord("enable", "activate", "aanzetten", "inschakelen") || t.hasPart("turn on") -> false
        t.hasWord("off", "disabled", "uit", "false") -> false
        t.hasWord("on", "enabled", "aan", "true") -> true
        else -> false
    }
}

/** "Remove from liked songs" means the song is liked; "Add to liked songs" means it is not yet. */
internal fun likedOf(action: String?, name: String?): Boolean {
    val t = words(action, name)
    return when {
        t.hasPart("unlike", "remove", "unfavo", "unheart", "unsave", "verwijder", "undo") -> true
        t.hasWord("add", "toevoegen", "save", "opslaan") -> false
        t.hasWord("liked", "added", "saved", "filled", "favorited", "favourited", "on") -> true
        else -> false
    }
}

/** What the picker needs to know of one media session. */
data class SessionView(
    val playing: Boolean,
    val hasMetadata: Boolean,
    /** False for a stopped player or one in error, which only shows when nothing else is there. */
    val usable: Boolean = true,
    /** When it last started or stopped playing, in any clock that only goes up. */
    val lastPlayingAt: Long = 0,
)

/**
 * The session to show: the one that plays, else the one that played last. Android lists the sessions by priority, so
 * on a tie the earlier one wins. Null when nothing has a song to show.
 */
internal fun pickSession(sessions: List<SessionView>): Int? {
    val order = compareBy<IndexedValue<SessionView>> { it.value.lastPlayingAt }.thenByDescending { it.index }
    val known = sessions.withIndex().filter { it.value.hasMetadata }
    known.filter { it.value.playing }.maxWithOrNull(order)?.let { return it.index }
    return known.filter { it.value.usable }.maxWithOrNull(order)?.index
}
