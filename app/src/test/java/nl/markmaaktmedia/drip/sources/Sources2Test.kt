package nl.markmaaktmedia.drip.sources

import android.os.BatteryManager
import nl.markmaaktmedia.drip.island.LiveInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnLiveTest {
    private fun live(key: String, title: String = key) = LiveInfo(key = key, packageName = "p", appLabel = "a", title = title)

    @Test fun ownEntriesAreReplacedInPlaceAndOthersStay() {
        val before = listOf(live("timer"), live("drip:install:1", "old"), live("ride"))
        val after = before.withOwn("drip:install:", listOf(live("drip:install:1", "new")))
        assertEquals(listOf("timer", "drip:install:1", "ride"), after.map { it.key })
        assertEquals("new", after[1].title)
    }

    @Test fun aNewEntryGoesInFrontAndAGoneOneIsRemoved() {
        val before = listOf(live("timer"), live("drip:install:1"))
        val after = before.withOwn("drip:install:", listOf(live("drip:install:2")))
        assertEquals(listOf("drip:install:2", "timer"), after.map { it.key })
    }

    @Test fun anotherPrefixOfDripIsLeftAlone() {
        val before = listOf(live("drip:calendar:9:1"), live("drip:install:1"))
        val after = before.withOwn("drip:install:", emptyList())
        assertEquals(listOf("drip:calendar:9:1"), after.map { it.key })
    }
}

class CalendarPlanTest {
    private val min = 60_000L
    private fun event(id: Long, begin: Long, allDay: Boolean = false, skipped: Boolean = false) =
        CalendarEvent(id, begin, begin + 30 * min, "Meeting $id", allDay = allDay, skipped = skipped)

    @Test fun showsFromTheLeadUntilFiveMinutesAfterTheStart() {
        val e = listOf(event(1, 100 * min))
        assertNull(CalendarPlan.pick(e, 89 * min, 10 * min))
        assertEquals(1L, CalendarPlan.pick(e, 90 * min, 10 * min)?.eventId)
        assertEquals(1L, CalendarPlan.pick(e, 104 * min, 10 * min)?.eventId)
        assertNull(CalendarPlan.pick(e, 105 * min, 10 * min))
    }

    @Test fun allDayDeclinedAndCancelledAreSkipped() {
        val e = listOf(event(1, 100 * min, allDay = true), event(2, 100 * min, skipped = true))
        assertNull(CalendarPlan.pick(e, 95 * min, 10 * min))
        assertNull(CalendarPlan.nextChange(e, 0, 10 * min))
    }

    @Test fun theEarliestWinsAndTheNextChangeIsTheNearestEdge() {
        val e = listOf(event(2, 120 * min), event(1, 100 * min))
        assertEquals(1L, CalendarPlan.pick(e, 98 * min, 10 * min)?.eventId)
        assertEquals(90 * min, CalendarPlan.nextChange(e, 50 * min, 10 * min))
        assertEquals(105 * min, CalendarPlan.nextChange(e, 98 * min, 10 * min))
        assertEquals(110 * min, CalendarPlan.nextChange(e, 106 * min, 10 * min))
    }
}

class MeetingLinksTest {
    @Test fun findsTheCommonServices() {
        assertEquals("https://meet.google.com/abc-defg-hij", MeetingLinks.find(null, "Join: https://meet.google.com/abc-defg-hij."))
        assertEquals("https://us02web.zoom.us/j/123456789?pwd=abc", MeetingLinks.find("https://us02web.zoom.us/j/123456789?pwd=abc"))
        assertEquals(
            "https://teams.microsoft.com/l/meetup-join/19%3ameeting_X%40thread.v2/0?context=%7b%7d",
            MeetingLinks.find(null, "<a href=\"https://teams.microsoft.com/l/meetup-join/19%3ameeting_X%40thread.v2/0?context=%7b%7d\">Join</a>"),
        )
        assertEquals("https://acme.webex.com/meet/jan", MeetingLinks.find("Room 4", "(https://acme.webex.com/meet/jan)"))
    }

    @Test fun theLocationComesFirstAndHtmlAmpersandsAreUndone() {
        assertEquals(
            "https://zoom.us/j/1?pwd=a&uname=b",
            MeetingLinks.find("https://zoom.us/j/1?pwd=a&amp;uname=b", "https://meet.google.com/xyz-abcd-efg"),
        )
    }

    @Test fun aHomePageIsNotAMeeting() {
        assertNull(MeetingLinks.find("Office", "Sent with https://zoom.us and https://www.example.com/meet"))
        assertNull(MeetingLinks.find(null, null))
    }
}

class InstallShownTest {
    @Test fun runningOrJustMovedIsShown() {
        assertTrue(installShown(active = true, progress = 0f, sinceChangeMs = 0))
        assertTrue(installShown(active = false, progress = 0.4f, sinceChangeMs = 5_000))
        assertFalse(installShown(active = false, progress = 0.4f, sinceChangeMs = INSTALL_QUIET_MS))
        assertFalse(installShown(active = false, progress = 0f, sinceChangeMs = 0))
        // A system update waiting for the restart, and an install that stopped moving long ago.
        assertFalse(installShown(active = true, progress = 0.75f, sinceChangeMs = 0, staged = true))
        assertFalse(installShown(active = true, progress = 0.75f, sinceChangeMs = INSTALL_STUCK_MS))
    }
}

class EarbudsNameTest {
    @Test fun inEarModelsAreEarbuds() {
        listOf("WF-1000XM5", "Galaxy Buds2 Pro", "AirPods Pro", "Pixel Buds Pro", "LinkBuds S").forEach {
            assertTrue(it, looksLikeEarbuds(it))
        }
        listOf("WH-1000XM5", "Bose QC45", "Headphones").forEach { assertFalse(it, looksLikeEarbuds(it)) }
    }
}

class ChargeTraitsTest {
    private val charging = BatteryManager.BATTERY_STATUS_CHARGING
    private val notCharging = BatteryManager.BATTERY_STATUS_NOT_CHARGING
    private val full = BatteryManager.BATTERY_STATUS_FULL

    @Test fun aPlainChargerAt80IsNeitherFastNorLimited() {
        val r = ChargeTraits().update(true, charging, 80, 2_000_000, 5_000_000, 1_200_000)
        assertFalse(r.fast)
        assertNull(r.limit)
    }

    @Test fun aChargerOfFifteenWattsOrMoreIsFast() {
        assertTrue(ChargeTraits().update(true, charging, 40, 3_000_000, 9_000_000, null).fast)
        assertTrue(ChargeTraits().update(true, charging, 40, 3_000_000, 0, null).fast)
        assertFalse(ChargeTraits().update(true, charging, 40, 2_000_000, 0, null).fast)
    }

    @Test fun aHighCurrentIsFastInEitherUnitAndSign() {
        assertTrue(ChargeTraits().update(true, charging, 40, 0, 0, 3_000_000).fast)
        assertTrue(ChargeTraits().update(true, charging, 40, 0, 0, -3_000_000).fast)
        assertTrue(ChargeTraits().update(true, charging, 40, 0, 0, 3_000).fast)
        assertFalse(ChargeTraits().update(true, charging, 95, 0, 0, 3_000).fast)
    }

    @Test fun fastStaysWhileTheCurrentDipsAndGoesWithThePlug() {
        val t = ChargeTraits()
        assertTrue(t.update(true, charging, 40, 0, 0, 3_000_000).fast)
        assertTrue(t.update(true, charging, 70, 0, 0, 900_000).fast)
        assertFalse(t.update(false, BatteryManager.BATTERY_STATUS_DISCHARGING, 70, 0, 0, -400_000).fast)
        assertFalse(t.update(true, charging, 70, 0, 0, 900_000).fast)
    }

    @Test fun pluggedAndNotChargingBelowFullIsALimit() {
        val t = ChargeTraits()
        assertEquals(80, t.update(true, notCharging, 81, 0, 0, null).limit)
        // Topping up again after slipping a little is still the same limit.
        assertEquals(80, t.update(true, charging, 79, 0, 0, null).limit)
        assertNull(t.update(false, BatteryManager.BATTERY_STATUS_DISCHARGING, 79, 0, 0, null).limit)
        assertEquals(83, ChargeTraits().update(true, notCharging, 83, 0, 0, null).limit)
    }

    @Test fun fullAtAHundredAndUnpluggedAreNoLimit() {
        assertNull(ChargeTraits().update(true, full, 100, 0, 0, null).limit)
        assertNull(ChargeTraits().update(false, notCharging, 80, 0, 0, null).limit)
        assertEquals(90, ChargeTraits().update(true, full, 90, 0, 0, null).limit)
        assertNull(ChargeTraits().update(true, full, 99, 0, 0, null).limit)
        assertEquals(95, ChargeTraits().update(true, notCharging, 99, 0, 0, null).limit)
    }
}
