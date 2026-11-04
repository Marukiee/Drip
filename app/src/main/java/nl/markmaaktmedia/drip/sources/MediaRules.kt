package nl.markmaaktmedia.drip.sources

import nl.markmaaktmedia.drip.island.RepeatMode
import nl.markmaaktmedia.drip.island.ShuffleMode

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

// Spotify never says like: its heart is "Add to collection", "Your Library" or "Opslaan in je bibliotheek".
private val likeParts = listOf(
    "like", "favorite", "favourite", "favoriet", "heart", "thumbs up", "thumb up", "leuk", "collection", "library",
    "bibliotheek", "save", "opslaan",
)
/** Bare "add to" and "remove from" are the heart too, unless they name a playlist or the queue. */
private val libraryMoves = listOf(" add to ", " remove from ", " toevoegen ", " verwijderen uit ")
private val notTheHeart = listOf("playlist", "afspeellijst", "queue", "wachtrij")
private val dislikeParts = listOf("dislike", "thumbs down", "thumb down", "niet leuk")

internal fun customKindOf(action: String?, name: String?): CustomKind? {
    val t = words(action, name)
    return when {
        t.hasPart("shuffle", "willekeurig") -> CustomKind.SHUFFLE
        t.hasPart("repeat", "herhaal", "herhal") || t.hasWord("loop") -> CustomKind.REPEAT
        (likeParts.any { it in t } || libraryMoves.any { it in t }) && dislikeParts.none { it in t } &&
            notTheHeart.none { it in t } -> CustomKind.LIKE
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
internal fun shuffleOnOf(action: String?, name: String?): Boolean = shuffleStatedBy(action, name) ?: false

/** As [shuffleOnOf], but null when the words say nothing either way: a bare "Shuffle", as many players have. */
internal fun shuffleStatedBy(action: String?, name: String?): Boolean? {
    val t = words(action, name)
    return when {
        t.hasWord("disable", "deactivate", "uitzetten", "uitschakelen") || t.hasPart("turn off") -> true
        t.hasWord("enable", "activate", "aanzetten", "inschakelen") || t.hasPart("turn on") -> false
        t.hasWord("off", "disabled", "uit", "false") -> false
        t.hasWord("on", "enabled", "aan", "true") -> true
        else -> null
    }
}

/** Shuffle from Media3's icon number: shuffle and shuffle with a star are on, the crossed-out one is off. */
internal fun shuffleOfMedia3Icon(icon: Int): Boolean? = shuffleModeOfMedia3Icon(icon)?.let { it != ShuffleMode.OFF }

/** The same, telling the star apart: smart shuffle. */
internal fun shuffleModeOfMedia3Icon(icon: Int): ShuffleMode? = when (icon) {
    0xe043 -> ShuffleMode.ON
    0xfe043 -> ShuffleMode.SMART
    0xfe044 -> ShuffleMode.OFF
    else -> null
}

/** Smart shuffle from the name of the player's icon, "shuffle_smart" or one with a star or sparkle in it. */
internal fun shuffleModeOfIconName(name: String?): ShuffleMode? {
    val t = words(name ?: return null)
    if (t.hasPart("smart", "star", "sparkle", "enhance")) return ShuffleMode.SMART
    return shuffleOfIconName(name)?.let { if (it) ShuffleMode.ON else ShuffleMode.OFF }
}

/** What a press on shuffle turns it into: on, then off, or on, smart and off for a player with three ways. */
internal fun nextShuffle(now: ShuffleMode, threeWays: Boolean): ShuffleMode = when (now) {
    ShuffleMode.OFF -> ShuffleMode.ON
    ShuffleMode.ON -> if (threeWays) ShuffleMode.SMART else ShuffleMode.OFF
    ShuffleMode.SMART -> ShuffleMode.OFF
}

/** Repeat from Media3's icon number. */
internal fun repeatOfMedia3Icon(icon: Int): RepeatMode? = when (icon) {
    0xe040 -> RepeatMode.ALL
    0xe041 -> RepeatMode.ONE
    0xfe040 -> RepeatMode.OFF
    else -> null
}

/** Shuffle from the name of the player's icon, "ic_shuffle_on" or "shuffle_off"; null when a plain "ic_shuffle". */
internal fun shuffleOfIconName(name: String?): Boolean? {
    val t = words(name ?: return null)
    return when {
        t.hasWord("off", "disabled", "inactive", "outline", "outlined", "unselected") -> false
        t.hasWord("on", "enabled", "active", "selected", "filled", "checked") -> true
        else -> null
    }
}

/** Repeat from the name of the player's icon, "ic_repeat_one", "repeat_on", "repeat_off". */
internal fun repeatOfIconName(name: String?): RepeatMode? {
    val t = words(name ?: return null)
    return when {
        t.hasWord("one", "single", "1") -> RepeatMode.ONE
        t.hasWord("off", "disabled", "inactive", "outline", "outlined", "unselected") -> RepeatMode.OFF
        t.hasWord("on", "all", "enabled", "active", "selected", "filled") -> RepeatMode.ALL
        else -> null
    }
}

/** As [repeatModeOf], but null for a bare "Repeat" that says nothing about the mode. */
internal fun repeatStatedBy(action: String?, name: String?): RepeatMode? {
    val t = words(action, name)
    return when {
        t.hasWord("one", "single", "track", "song", "nummer", "1") -> RepeatMode.ONE
        t.hasWord("off", "none", "disabled", "uit", "geen") -> RepeatMode.OFF
        t.hasWord("all", "list", "queue", "playlist", "alles", "alle", "on", "aan") -> RepeatMode.ALL
        else -> null
    }
}

/**
 * What a switch of the player shows, the way [heartOf] works for the heart: right after a press, and for as long as
 * the player offers the very same action it did when pressed, the press says it; otherwise the player does.
 */
internal fun <T> followPress(stated: T?, pressed: T?, pressedJustNow: Boolean, actionUnchanged: Boolean): T? = when {
    pressed != null && (pressedJustNow || actionUnchanged) -> pressed
    else -> stated ?: pressed
}

/**
 * "Remove from liked songs" means the song is liked; "Add to liked songs" means it is not yet. Players offer the one
 * of the pair that undoes the present state, so which of the two is there says it.
 */
internal fun likedOf(action: String?, name: String?): Boolean = likedStatedBy(action, name) ?: false

/** As [likedOf], but null when the words say nothing either way: "Favorite" in both states, as many players do. */
internal fun likedStatedBy(action: String?, name: String?): Boolean? {
    val t = words(action, name)
    return when {
        t.hasPart("unlike", "remove", "unfavo", "unheart", "unsave", "verwijder", "undo") -> true
        t.hasWord("add", "toevoegen", "save", "opslaan") -> false
        t.hasWord("liked", "added", "saved", "filled", "favorited", "favourited", "on") || t.hasPart(" leuk gevonden ") -> true
        else -> null
    }
}

/**
 * The name of the icon resource a player gives its heart, "ic_favorite_border" or "heart_filled". Most players keep the
 * same action and name in both states and only swap the icon, so this is often the only thing that tells. Null when the
 * name says nothing.
 */
internal fun likedOfIconName(name: String?): Boolean? {
    val t = words(name ?: return null)
    return when {
        t.hasWord("outline", "outlined", "border", "unfilled", "inactive", "off", "empty", "add", "plus", "unchecked", "unselected", "unliked", "unfavorite") -> false
        t.hasWord("filled", "fill", "active", "on", "selected", "checked", "check", "liked", "saved", "added", "full", "solid", "done") -> true
        else -> null
    }
}

/**
 * Whether the heart is filled. Right after a press, and for as long as the player keeps offering the very same action
 * it offered when pressed (so its words and icon carry no state), the press says it. Otherwise the player does.
 */
internal fun heartOf(stated: Boolean?, pressed: Boolean?, pressedJustNow: Boolean, actionUnchanged: Boolean): Boolean = when {
    pressed != null && (pressedJustNow || actionUnchanged) -> pressed
    else -> stated ?: pressed ?: false
}

/** The extra in which Media3 players say which of its standard icons a custom action shows. */
internal const val MEDIA3_ICON_KEY = "androidx.media3.session.EXTRAS_KEY_COMMAND_BUTTON_ICON_COMPAT"

/** Media3's heart, thumb up, star, check circle and bookmark, as Material code points; the filled ones are 0xf0000 more. */
private val media3Hearts = setOf(0xe87d, 0xe8dc, 0xe838, 0xe86c, 0xe866)

/** Liked or not from Media3's icon number, null when the icon is not one of the heart-like ones. */
internal fun likedOfMedia3Icon(icon: Int): Boolean? = when {
    icon in media3Hearts -> false
    icon - 0xf0000 in media3Hearts -> true
    else -> null
}

/** Rating.RATING_HEART, Rating.RATING_THUMB_UP_DOWN and PlaybackState.ACTION_SET_RATING. */
internal const val RATING_HEART = 1
internal const val RATING_THUMB_UP_DOWN = 2
private const val ACTION_SET_RATING = 128L

/** Whether the player takes a heart or a thumb through the session's own rating, which wins over a custom action. */
internal fun likesByRating(ratingType: Int, actions: Long): Boolean =
    (ratingType == RATING_HEART || ratingType == RATING_THUMB_UP_DOWN) && actions and ACTION_SET_RATING != 0L

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

/**
 * The sessions beside [lead] that could show as players of their own: the ones that play first, then the ones that
 * stopped playing most recently. Never one without a song, or a stopped one.
 */
internal fun otherSessions(sessions: List<SessionView>, lead: Int): List<Int> =
    sessions.withIndex()
        .filter { it.index != lead && it.value.hasMetadata && it.value.usable }
        .sortedWith(compareByDescending<IndexedValue<SessionView>> { it.value.playing }.thenByDescending { it.value.lastPlayingAt })
        .map { it.index }

/** One song of a player's queue: MediaSession.QueueItem's id and the title of its description. */
data class QueueEntry(val id: Long, val title: String?)

/** MediaSession.QueueItem.UNKNOWN_ID: the player does not say which item plays. */
private const val UNKNOWN_QUEUE_ID = -1L

/** The title of what comes after the item that plays, or null without a queue, at its end, or when that is unknown. */
internal fun nextInQueue(queue: List<QueueEntry>, activeId: Long): String? {
    if (activeId == UNKNOWN_QUEUE_ID || queue.isEmpty()) return null
    val at = queue.indexOfFirst { it.id == activeId }
    if (at < 0) return null
    return queue.getOrNull(at + 1)?.title?.trim()?.takeIf { it.isNotEmpty() }
}
