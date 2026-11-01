package nl.markmaaktmedia.drip.sources

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.provider.CalendarContract
import android.text.format.DateFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.island.ActionKind
import nl.markmaaktmedia.drip.island.IslandAction
import nl.markmaaktmedia.drip.island.LiveInfo
import nl.markmaaktmedia.drip.island.LiveKind
import java.util.Date

/** One appointment as the calendar provider has it, an instance of it for a repeating one. */
data class CalendarEvent(
    val eventId: Long,
    val begin: Long,
    val end: Long,
    val title: String,
    val location: String? = null,
    val description: String? = null,
    val allDay: Boolean = false,
    /** Declined by the owner of the phone, or cancelled by the organiser: not worth a countdown. */
    val skipped: Boolean = false,
)

/** Which appointment the island counts down to, and when to look again. */
object CalendarPlan {
    /** An appointment stays this long after its start, for a late join. */
    const val GRACE_MS = 5 * 60_000L

    private fun usable(e: CalendarEvent) = !e.allDay && !e.skipped

    /** The earliest appointment between [leadMs] before its start and [GRACE_MS] after it, or null. */
    fun pick(events: List<CalendarEvent>, now: Long, leadMs: Long): CalendarEvent? =
        events.filter { usable(it) && now >= it.begin - leadMs && now < it.begin + GRACE_MS }.minByOrNull { it.begin }

    /** The next moment one of [events] comes or goes, or null when nothing will change. */
    fun nextChange(events: List<CalendarEvent>, now: Long, leadMs: Long): Long? =
        events.filter(::usable).flatMap { listOf(it.begin - leadMs, it.begin + GRACE_MS) }.filter { it > now }.minOrNull()
}

/** The link of a video call in the location or the description of an appointment. */
object MeetingLinks {
    // Only links that are a meeting, not the home page of the service in a signature.
    private val patterns = listOf(
        Regex("""https?://meet\.google\.com/[a-z0-9-]{3,}[^\s"'<>]*""", RegexOption.IGNORE_CASE),
        Regex("""https?://(?:[\w-]+\.)*zoom(?:gov)?\.us/(?:j|my|w|s|wc)/[^\s"'<>]+""", RegexOption.IGNORE_CASE),
        Regex("""https?://teams\.(?:microsoft|live)\.com/(?:l/meetup-join|meet)/[^\s"'<>]+""", RegexOption.IGNORE_CASE),
        Regex("""https?://(?:[\w-]+\.)*webex\.com/[^\s"'<>]+""", RegexOption.IGNORE_CASE),
    )

    fun find(vararg texts: String?): String? {
        for (text in texts) {
            if (text.isNullOrBlank()) continue
            val hit = patterns.mapNotNull { it.find(text) }.minByOrNull { it.range.first } ?: continue
            // A description in HTML escapes the ampersands of the query, and a sentence may end right after the link.
            return hit.value.replace("&amp;", "&").trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}')
        }
        return null
    }
}

/**
 * The next appointment, from [IslandSettings.calendarLeadMinutes] before its start until five minutes after it, as a
 * countdown on the island. Read again when the calendar changes and at the next moment something comes or goes, so it
 * needs no exact alarms. Without calendar access it looks once a minute whether that has changed.
 */
internal class CalendarPart(env: SourceEnv) : Part(env) {
    private val resolver = context.contentResolver
    private val apps = Apps(context)
    private var scope: CoroutineScope? = null
    private var job: Job? = null
    private var observing = false

    private val observer = object : ContentObserver(env.handler) {
        // A sync writes many rows in a burst; one read after it settles is enough.
        override fun onChange(selfChange: Boolean) {
            refresh(SETTLE_MS)
        }
    }

    override fun start() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        refresh()
    }

    override fun stop() {
        scope?.cancel()
        scope = null
        job = null
        if (observing) safely("calendar observer") { resolver.unregisterContentObserver(observer) }
        observing = false
        hub.putOwnLive(PREFIX, emptyList())
    }

    override fun onSettings() = refresh()

    private fun allowed() = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** The observer can only be registered with the permission: without it the provider throws. */
    private fun ensure() {
        if (observing || !allowed()) return
        safely("calendar observer") {
            resolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
            observing = true
        }
    }

    private fun refresh(settleMs: Long = 0) {
        val sc = scope ?: return
        job?.cancel()
        job = sc.launch {
            if (settleMs > 0) delay(settleMs)
            while (isActive) {
                val s = settings ?: return@launch
                if (!s.showCalendar) {
                    hub.putOwnLive(PREFIX, emptyList())
                    return@launch
                }
                ensure()
                if (!allowed()) {
                    hub.putOwnLive(PREFIX, emptyList())
                    delay(NO_ACCESS_RETRY_MS)
                    continue
                }
                val lead = s.calendarLeadMinutes.coerceAtLeast(0) * 60_000L
                val start = System.currentTimeMillis()
                val events = withContext(Dispatchers.IO) { safely("calendar query") { query(start, lead) } }.orEmpty()
                val now = System.currentTimeMillis()
                val shown = CalendarPlan.pick(events, now, lead)
                hub.putOwnLive(PREFIX, listOfNotNull(shown?.let(::toLive)))
                val next = CalendarPlan.nextChange(events, now, lead) ?: Long.MAX_VALUE
                // Capped, so a change the observer missed (a sync that came in quietly) still turns up.
                delay((minOf(next, now + MAX_SLEEP_MS) - now).coerceAtLeast(1_000L))
            }
        }
    }

    private fun query(now: Long, lead: Long): List<CalendarEvent> {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now - CalendarPlan.GRACE_MS)
            ContentUris.appendId(it, now + maxOf(lead, 0L) + LOOK_AHEAD_MS)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
            CalendarContract.Instances.STATUS,
        )
        val selection = "${CalendarContract.Instances.VISIBLE} = 1 AND ${CalendarContract.Instances.ALL_DAY} = 0"
        val out = ArrayList<CalendarEvent>()
        resolver.query(uri, projection, selection, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext() && out.size < MAX_EVENTS) {
                val declined = !c.isNull(7) && c.getInt(7) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED
                val cancelled = !c.isNull(8) && c.getInt(8) == CalendarContract.Events.STATUS_CANCELED
                out += CalendarEvent(
                    eventId = c.getLong(0),
                    begin = c.getLong(1),
                    end = c.getLong(2),
                    title = c.getString(3).orEmpty().trim(),
                    location = c.getString(4)?.trim()?.takeIf { it.isNotEmpty() },
                    description = c.getString(5),
                    allDay = c.getInt(6) != 0,
                    skipped = declined || cancelled,
                )
            }
        }
        return out
    }

    private fun toLive(e: CalendarEvent): LiveInfo {
        val view = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.eventId))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.end)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val calendarApp = calendarPackage(view)
        val time = DateFormat.getTimeFormat(context).format(Date(e.begin))
        val link = MeetingLinks.find(e.location, e.description)
        val join = link?.let {
            IslandAction(env.text(R.string.src2_join), ActionKind.OPEN) {
                safely("join meeting") {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
        // A link as the location says nothing a person reads, the button already carries it.
        val place = e.location?.takeUnless { link != null && it.contains(link) }
        return LiveInfo(
            key = "$PREFIX${e.eventId}:${e.begin}",
            packageName = calendarApp ?: context.packageName,
            appLabel = calendarApp?.let(apps::label) ?: env.text(R.string.src2_calendar),
            appIcon = calendarApp?.let(apps::icon),
            kind = LiveKind.CALENDAR,
            title = e.title.ifEmpty { env.text(R.string.src2_appointment) },
            detail = if (place != null) env.text(R.string.src2_time_place, time, place) else time,
            chronometerBase = e.begin,
            countDown = true,
            actions = listOfNotNull(join),
            open = { safely("open appointment") { context.startActivity(view) } },
        )
    }

    /** The app that opens appointments; with more than one and no default, the first of them. */
    private fun calendarPackage(view: Intent): String? = safely("calendar app") {
        val pm = context.packageManager
        pm.resolveActivity(view, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName?.takeIf { it != "android" }
            ?: pm.queryIntentActivities(view, PackageManager.MATCH_DEFAULT_ONLY).firstOrNull()?.activityInfo?.packageName
    }

    private companion object {
        const val PREFIX = "${OWN_LIVE_PREFIX}calendar:"
        const val LOOK_AHEAD_MS = 24 * 60 * 60_000L
        const val MAX_SLEEP_MS = 15 * 60_000L
        const val NO_ACCESS_RETRY_MS = 60_000L
        const val MAX_EVENTS = 50
        const val SETTLE_MS = 800L
    }
}
