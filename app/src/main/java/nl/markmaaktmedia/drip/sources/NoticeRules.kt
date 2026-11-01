package nl.markmaaktmedia.drip.sources

import nl.markmaaktmedia.drip.data.IslandSettings
import nl.markmaaktmedia.drip.island.ActionKind
import nl.markmaaktmedia.drip.island.AlertKind
import nl.markmaaktmedia.drip.island.LiveKind
import nl.markmaaktmedia.drip.island.ProgressPoint
import nl.markmaaktmedia.drip.island.ProgressSegment
import kotlin.math.roundToInt

/**
 * The parts of a notification the rules below look at. The listener copies them out of the Notification, so the rules
 * stay plain Kotlin and the tests can make one up without a phone.
 */
data class NoticeFacts(
    val packageName: String,
    val category: String? = null,
    /** The simple name of the style, like "MediaStyle", "CallStyle" or "ProgressStyle". */
    val template: String? = null,
    val ongoing: Boolean = false,
    val groupSummary: Boolean = false,
    val hasMediaSession: Boolean = false,
    /** Notification.CallStyle's call type, or null when the notification has none. */
    val callType: Int? = null,
    val hasCallIntents: Boolean = false,
    val hasFullScreenIntent: Boolean = false,
    val showsChronometer: Boolean = false,
    val countDown: Boolean = false,
    val progress: Int = 0,
    val progressMax: Int = 0,
    val indeterminate: Boolean = false,
    /** FLAG_PROMOTED_ONGOING, which only Android 16 and later set. */
    val promoted: Boolean = false,
    val title: String? = null,
    val text: String? = null,
    val subText: String? = null,
    val channelId: String? = null,
    /** The newest message of a conversation is the user's own, which is how an app shows a reply that was just sent. */
    val lastFromUser: Boolean = false,
)

/** What the ranking says about a notification. */
data class RankFacts(
    val importance: Int,
    val ambient: Boolean = false,
    val matchesFilter: Boolean = true,
    val suspended: Boolean = false,
)

internal const val CALL_TYPE_INCOMING = 1
private const val IMPORTANCE_DEFAULT = 3

internal fun isMedia(f: NoticeFacts): Boolean = f.hasMediaSession || f.category == "transport" ||
    f.template == "MediaStyle" || f.template == "DecoratedMediaCustomViewStyle"

internal fun isCall(f: NoticeFacts): Boolean {
    val callish = f.template == "CallStyle" || f.callType != null || f.category == "call"
    // Some apps file a missed call under "call" too. That one can be swiped away and is a plain notification.
    return callish && (f.ongoing || f.hasFullScreenIntent || f.callType != null || f.hasCallIntents)
}

/** Ringing: the call style says so, or a phone app rings full screen and has no clock running yet. */
internal fun isIncomingCall(f: NoticeFacts): Boolean = f.callType == CALL_TYPE_INCOMING ||
    ((f.callType == null || f.callType == 0) && f.hasFullScreenIntent && !f.showsChronometer)

private val liveCategories = setOf("navigation", "stopwatch", "progress", "workout", "location_sharing")
private val runningNotices = listOf(
    "is running", "are running", "running in the background", "tap for more information",
    "wordt uitgevoerd", "actief op de achtergrond", "draait op de achtergrond", "tik voor meer informatie",
)

internal fun isClockApp(packageName: String) = "clock" in packageName.lowercase()

/** An ongoing notification that has something to show: a bar, a clock, a promotion or a category made for it. */
internal fun isLive(f: NoticeFacts, ownPackage: String): Boolean {
    if (f.packageName == ownPackage || f.packageName == "android") return false
    if (!f.ongoing || f.groupSummary || isMedia(f) || isCall(f)) return false
    // Something that rings full screen (an alarm, a timer that ran out) is an alert, not a card that stays.
    if (f.hasFullScreenIntent) return false
    val bar = f.progressMax > 0 || f.indeterminate
    // A match that is on has no bar and often no clock either, only the score.
    val strong = f.promoted || f.category in liveCategories || isClockApp(f.packageName) || isSports(f)
    if (!strong && !bar && !f.showsChronometer) return false
    if (!strong && !bar) {
        // A notice that the app runs is not worth the room, even when it shows a clock.
        val said = listOfNotNull(f.title, f.text).joinToString(" ").lowercase()
        if (runningNotices.any { it in said }) return false
    }
    return true
}

private val navigationApps = listOf(
    "com.google.android.apps.maps", "com.waze", "com.here.app", "com.sygic", "net.osmand", "app.organicmaps",
    "com.mapswithme", "nl.flitsmeister", "com.tomtom", "com.citymapper",
)
private val deliveryApps = listOf(
    "com.ubercab.eats", "com.takeaway", "com.thuisbezorgd", "com.deliveroo", "com.glovoapp", "com.dd.doordash",
    "com.grubhub", "com.picnic", "nl.postnl", "com.dhl", "com.dpd", "com.ups", "com.fedex", "com.amazon.mshop",
)
private val rideApps = listOf("com.ubercab", "ee.mtakso", "me.lyft", "com.lyft", "taxi.android.client", "com.bolt")
private val recorderApps = listOf(
    "com.google.android.apps.recorder", "com.sec.android.app.voicenote", "com.android.soundrecorder",
    "com.sonymobile.androidapp.audiorecorder",
)
private val wordSplit = Regex("[^\\p{L}\\p{N}]+")

internal fun liveKindFor(f: NoticeFacts): LiveKind {
    val pkg = f.packageName.lowercase()
    val said = listOfNotNull(f.title, f.text, f.subText, f.channelId).joinToString(" ").lowercase()
        .replace(wordSplit, " ")
    val words = said.split(' ').filter { it.isNotEmpty() }
    // One word is a stem that may start a word ("deliver" finds "delivery"), more than one is a phrase.
    fun mentions(vararg stems: String) = stems.any { stem ->
        if (' ' in stem) " $said ".contains(" $stem") else words.any { it.startsWith(stem) }
    }
    val bar = f.progressMax > 0 || f.indeterminate
    return when {
        // Tandem's own bar says "Receiving" over a file name, which no word below would find.
        tandemRole(f) == TandemRole.TRANSFER -> LiveKind.DOWNLOAD
        f.category == "navigation" || navigationApps.any(pkg::startsWith) -> LiveKind.NAVIGATION
        // Uber Eats before Uber: the food app starts with the name of the ride app.
        deliveryApps.any(pkg::startsWith) -> LiveKind.DELIVERY
        rideApps.any(pkg::startsWith) -> LiveKind.RIDE
        isSports(f) -> LiveKind.SPORTS
        isClockApp(pkg) -> if (f.category == "stopwatch" || mentions("stopwatch") || (f.showsChronometer && !f.countDown)) {
            LiveKind.STOPWATCH
        } else {
            LiveKind.TIMER
        }
        f.category == "stopwatch" -> LiveKind.STOPWATCH
        // A workout counts up and may speak of a record, which is no recording.
        f.category == "workout" || f.category == "location_sharing" -> LiveKind.GENERIC
        recorderApps.any(pkg::startsWith) || mentions("record", "opname", "scherm opnemen") -> LiveKind.RECORDING
        mentions("deliver", "bezorg", "courier", "koerier", "bestelling", "parcel", "pakket", "order") -> LiveKind.DELIVERY
        mentions("ride", "rit", "taxi", "driver", "chauffeur") -> LiveKind.RIDE
        pkg.startsWith("com.android.providers.downloads") ||
            (bar && mentions("download", "upload", "install", "update", "bijwerk")) -> LiveKind.DOWNLOAD
        // After the words: "2 - 1" in a delivery or a download is more likely a count than a score.
        scoreIn(f) != null -> LiveKind.SPORTS
        f.countDown || mentions("timer") -> LiveKind.TIMER
        else -> LiveKind.GENERIC
    }
}

/** 0 to 1 when the notification has a bar that knows how far it is. */
internal fun liveProgress(f: NoticeFacts): Float? =
    if (f.progressMax > 0 && !f.indeterminate) (f.progress.toFloat() / f.progressMax).coerceIn(0f, 1f) else null

/**
 * The few characters a closed island has room for. Android 16's own chip text wins, then the percentage of a bar,
 * then the first short line with a number in it, like "3 min" or "150 m".
 */
internal fun shortTextFor(f: NoticeFacts, criticalText: String? = null): String? {
    criticalText?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    liveProgress(f)?.let { return "${(it * 100).roundToInt()}%" }
    return listOf(f.subText, f.title, f.text)
        .mapNotNull { it?.trim() }
        .firstOrNull { it.length <= 10 && it.any(Char::isDigit) }
}

/**
 * Android's own screenshot preview already pops up, and a second announcement of "Screenshot saved" on top of it is
 * noise. The system UI posts nothing else worth an alert either (that is what Drip's own system sources are for).
 */
internal fun isSystemChatter(packageName: String): Boolean {
    val pkg = packageName.lowercase()
    return pkg == "com.android.systemui" || "screenshot" in pkg || "screenrecord" in pkg
}

/** Whether a new notification opens the island, and as what. Null keeps it quiet. */
internal fun alertKindFor(f: NoticeFacts, rank: RankFacts, s: IslandSettings, ownPackage: String): AlertKind? {
    if (f.packageName == ownPackage || !s.showNotifications) return null
    if (isSystemChatter(f.packageName)) return null
    if (!s.notifyAllApps && f.packageName !in s.notifyApps) return null
    if (f.groupSummary || isMedia(f) || isCall(f)) return null
    val alarm = f.category == "alarm"
    // An alarm that goes off stays ongoing until it is stopped, so alarms are the one ongoing thing let through.
    if (f.ongoing && !alarm) return null
    if (f.title.isNullOrBlank() && f.text.isNullOrBlank()) return null
    // The app posts the conversation again with the reply in it; that is no news to the one who sent it.
    if (f.lastFromUser) return null
    if (rank.importance < IMPORTANCE_DEFAULT || rank.ambient || !rank.matchesFilter || rank.suspended) return null
    return if (alarm) AlertKind.ALARM else AlertKind.NOTIFICATION
}

private const val SEMANTIC_REPLY = 1
private const val SEMANTIC_MARK_AS_READ = 2
private const val SEMANTIC_MUTE = 6
private const val SEMANTIC_UNMUTE = 7

/**
 * What a button of an app does, so the island can draw its own icon for it. The semantic action wins when the app
 * set one; otherwise the title is read, in English and Dutch.
 */
internal fun actionKindFor(semantic: Int, title: String?, hasRemoteInput: Boolean, inCall: Boolean = false): ActionKind {
    when (semantic) {
        SEMANTIC_REPLY -> return ActionKind.REPLY
        SEMANTIC_MARK_AS_READ -> return ActionKind.MARK_READ
        SEMANTIC_MUTE, SEMANTIC_UNMUTE -> return ActionKind.MUTE
    }
    if (hasRemoteInput) return ActionKind.REPLY
    val t = title.orEmpty().trim().lowercase()
    fun has(vararg words: String) = words.any { it in t }
    return when {
        has("hang up", "hangup", "end call", "ophangen", "beëindig", "beeindig") || (inCall && t == "end") -> ActionKind.HANG_UP
        has("decline", "reject", "weiger", "afwijzen") -> ActionKind.DECLINE
        has("speaker", "luidspreker") -> ActionKind.SPEAKER
        has("mute", "dempen") -> ActionKind.MUTE
        has("answer", "opnemen", "aannemen") -> ActionKind.ANSWER
        // The Dutch phone app answers with "Beantwoorden", which is also how a mail app says reply.
        inCall && has("accept", "beantwoord") -> ActionKind.ANSWER
        has("mark as read", "mark read", "gelezen") -> ActionKind.MARK_READ
        has("reply", "antwoord", "reageer") -> ActionKind.REPLY
        has("open") -> ActionKind.OPEN
        else -> ActionKind.OTHER
    }
}

/** A segment or a point of an Android 16 progress style: a length or a position in the units of the bar, and a colour. */
data class BarPart(val amount: Int, val color: Int = 0)

/** Notification.COLOR_DEFAULT: the app left the colour to the system. */
private const val NO_COLOR = 0

/** The stretches of a bar as shares of the whole, so they add up to 1 whatever units the app counted in. */
internal fun progressSegments(parts: List<BarPart>): List<ProgressSegment> {
    val kept = parts.filter { it.amount > 0 }
    val total = kept.sumOf { it.amount.toLong() }
    if (total <= 0) return emptyList()
    return kept.map { ProgressSegment(it.amount.toFloat() / total, it.color.takeIf { c -> c != NO_COLOR }) }
}

/** The marks on a bar of length [max], from 0 to 1. Android leaves out a mark beyond the bar, so Drip does too. */
internal fun progressPoints(parts: List<BarPart>, max: Int): List<ProgressPoint> {
    if (max <= 0) return emptyList()
    return parts.filter { it.amount in 0..max }
        .map { ProgressPoint(it.amount.toFloat() / max, it.color.takeIf { c -> c != NO_COLOR }) }
        .sortedBy { it.position }
}

// Recent Android writes "2:32 PM" with a narrow no-break space and some apps put a no-break space in "200 m".
private const val GAP = "[\\s\\u00A0\\u202F]"
private val distancePattern = Regex(
    "(?<![\\p{N}.,])(\\d{1,4}(?:[.,]\\d{1,2})?)$GAP?(km|mi|ft|yd|m)(?![\\p{L}\\p{N}])",
    RegexOption.IGNORE_CASE,
)
private val clockPattern = Regex(
    "(?<![\\p{N}:])((?:[01]?\\d|2[0-3]):[0-5]\\d)(?:$GAP?([AaPp])\\.?$GAP?[Mm]\\.?(?![\\p{L}]))?(?![\\p{N}])",
)
private val partSplit = Regex("[\\u00B7\\u2022|\\n]")
private val arrivalStems = listOf("aankomst", "arriv", "eta")

/**
 * How far to the next turn: the first distance in the title or the text, where Maps and Waze put it. The sub text is
 * left alone, because that is where the whole route's distance goes.
 */
internal fun nextTurnDistance(f: NoticeFacts): String? = listOf(f.title, f.text).firstNotNullOfOrNull { line ->
    line?.let(distancePattern::find)?.let { "${it.groupValues[1]} ${it.groupValues[2].lowercase()}" }
}

private fun clockIn(line: String): String? = clockPattern.find(line)?.let {
    val half = it.groupValues[2]
    if (half.isEmpty()) it.groupValues[1] else "${it.groupValues[1]} ${half.uppercase()}M"
}

/**
 * The arrival time of a route as the app wrote it, like "14:32" or "4:32 PM". A part that says arrival wins; without
 * one, a time in the sub text, which is where Maps keeps it.
 */
internal fun arrivalTime(f: NoticeFacts): String? {
    val parts = listOfNotNull(f.subText, f.title, f.text).flatMap { it.split(partSplit) }
    parts.firstOrNull { part ->
        val words = part.lowercase().split(wordSplit)
        words.any { w -> arrivalStems.any { w.startsWith(it) && (it != "eta" || w == "eta") } } && clockIn(part) != null
    }?.let { return clockIn(it) }
    return f.subText?.let(::clockIn)
}

// A count with a unit after it ("10 - 15 min", "2-3 days") is a range, not a score.
private const val UNITS = "min|mins|minuten|m|km|mi|h|hr|hrs|hours|u|uur|d|day|days|dag|dagen|s|sec|wk|weeks|weken|files|bestanden|items|x"
private val scoreDash = Regex(
    "(?<![\\p{N}\\-\\u2013\\u2014/:.,])(\\d{1,3})$GAP?[-\\u2013\\u2014]$GAP?(\\d{1,3})" +
        "(?![\\p{N}\\-\\u2013\\u2014/:%]|[.,]\\p{N}|$GAP?(?:$UNITS)(?![\\p{L}]))",
    RegexOption.IGNORE_CASE,
)
private val scoreTeams = Regex("\\b[A-Z]{2,4}$GAP+(\\d{1,3})$GAP+(\\d{1,3})$GAP+[A-Z]{2,4}\\b")

/** "2 - 1", "2–1" or "NED 2 1 ESP" as "2-1"; null when the line holds no score. */
internal fun scoreOf(line: String?): String? {
    line ?: return null
    val m = scoreTeams.find(line) ?: scoreDash.find(line) ?: return null
    return "${m.groupValues[1]}-${m.groupValues[2]}"
}

internal fun scoreIn(f: NoticeFacts): String? = listOf(f.title, f.text, f.subText).firstNotNullOfOrNull(::scoreOf)

private val sportsApps = listOf(
    "com.espn", "de.motain.iliga", "com.onefootball", "com.mobilefootie", "com.fotmob", "com.livescore",
    "com.sofascore", "eu.livesport",
)
/** Apps that bring much more than sport, so only a score makes their notification a match. */
private val scoreCardApps = listOf("com.google.android.googlequicksearchbox", "nl.nos")

internal fun isSports(f: NoticeFacts): Boolean {
    val pkg = f.packageName.lowercase()
    return sportsApps.any(pkg::startsWith) || (scoreCardApps.any(pkg::startsWith) && scoreIn(f) != null)
}

/** The short text for [kind]: Android 16's chip text, else the distance to the turn or the score, else [shortTextFor]. */
internal fun liveShortText(f: NoticeFacts, kind: LiveKind, criticalText: String? = null): String? {
    criticalText?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    when (kind) {
        LiveKind.NAVIGATION -> nextTurnDistance(f)?.let { return it }
        LiveKind.SPORTS -> scoreIn(f)?.let { return it }
        else -> Unit
    }
    return shortTextFor(f)
}
