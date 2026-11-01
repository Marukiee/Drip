package nl.markmaaktmedia.drip.sources

import nl.markmaaktmedia.drip.data.IslandSettings
import nl.markmaaktmedia.drip.island.LiveKind
import nl.markmaaktmedia.drip.island.ProgressPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveUpdateRulesTest {
    private val own = "nl.markmaaktmedia.drip"

    @Test fun segmentsAreSharesOfTheWhole() {
        val s = progressSegments(listOf(BarPart(300, 0xFF00FF00.toInt()), BarPart(0, 5), BarPart(100), BarPart(-4)))
        assertEquals(2, s.size)
        assertEquals(0.75f, s[0].length, 1e-6f)
        assertEquals(0xFF00FF00.toInt(), s[0].color)
        assertEquals(0.25f, s[1].length, 1e-6f)
        assertNull(s[1].color)
        val thirds = progressSegments(List(3) { BarPart(1) })
        assertEquals(1f, thirds.sumOf { it.length.toDouble() }.toFloat(), 1e-6f)
        assertTrue(progressSegments(listOf(BarPart(0))).isEmpty())
        assertTrue(progressSegments(emptyList()).isEmpty())
    }

    @Test fun pointsOnTheBar() {
        val p = progressPoints(listOf(BarPart(75, 7), BarPart(0), BarPart(101), BarPart(-1), BarPart(25)), 100)
        assertEquals(listOf(ProgressPoint(0f), ProgressPoint(0.25f), ProgressPoint(0.75f, 7)), p)
        assertTrue(progressPoints(listOf(BarPart(5)), 0).isEmpty())
    }

    @Test fun navigationDistance() {
        assertEquals("200 m", nextTurnDistance(NoticeFacts("p", title = "200 m", text = "Turn left onto Damrak")))
        assertEquals("1,2 km", nextTurnDistance(NoticeFacts("p", title = "Rechtsaf over 1,2km")))
        assertEquals("0.3 mi", nextTurnDistance(NoticeFacts("p", title = "Keep right", text = "In 0.3 mi take exit 4")))
        // "15 min" is no distance, and the sub text holds the whole route.
        assertNull(nextTurnDistance(NoticeFacts("p", title = "15 min", subText = "3.2 km")))
    }

    @Test fun navigationArrival() {
        assertEquals("14:32", arrivalTime(NoticeFacts("p", subText = "15 min · 3,2 km · Aankomst 14:32")))
        assertEquals("4:32 PM", arrivalTime(NoticeFacts("p", text = "Arriving at 4:32 pm")))
        assertEquals("9:05", arrivalTime(NoticeFacts("p", title = "ETA 9:05", subText = "12 min")))
        assertEquals("14:32", arrivalTime(NoticeFacts("p", subText = "14:32")))
        // A time in the text without a word for arrival is something else.
        assertNull(arrivalTime(NoticeFacts("p", title = "Turn left", text = "Closed until 18:00")))
        assertNull(arrivalTime(NoticeFacts("p", subText = "Beta 25:61")))
    }

    @Test fun navigationShortText() {
        val nav = NoticeFacts("com.google.android.apps.maps", ongoing = true, title = "200 m", text = "Turn left", subText = "Aankomst 14:32")
        assertEquals("200 m", liveShortText(nav, LiveKind.NAVIGATION))
        assertEquals("Now", liveShortText(nav, LiveKind.NAVIGATION, criticalText = "Now"))
    }

    @Test fun scores() {
        assertEquals("2-1", scoreOf("Ajax 2 - 1 PSV"))
        assertEquals("2-1", scoreOf("2–1"))
        assertEquals("3-0", scoreOf("Feyenoord 3—0 Heracles"))
        assertEquals("2-1", scoreOf("NED 2 1 ESP"))
        assertNull(scoreOf("Arrives 14:32"))
        assertNull(scoreOf("10 - 15 min"))
        assertNull(scoreOf("Delivery in 2-3 days"))
        assertNull(scoreOf("Tomorrow 12-15\u00B0"))
        assertNull(scoreOf("2026-11-01"))
        assertNull(scoreOf("Call 06-12345678"))
        assertNull(scoreOf("14:00 - 15:00"))
        assertNull(scoreOf(null))
    }

    @Test fun sportsKinds() {
        val match = NoticeFacts("com.google.android.googlequicksearchbox", ongoing = true, title = "Ajax vs PSV", text = "2 - 1 · 67'")
        assertTrue(isSports(match))
        assertTrue(isLive(match, own))
        assertEquals(LiveKind.SPORTS, liveKindFor(match))
        assertEquals("2-1", liveShortText(match, LiveKind.SPORTS))
        // The Google app without a score is no match.
        assertFalse(isSports(match.copy(text = "Rain at 14:00")))
        assertTrue(isSports(NoticeFacts("de.motain.iliga", title = "Kick-off")))
        assertTrue(isSports(NoticeFacts("com.mobilefootie.wc2010", title = "Half time")))
        assertEquals(LiveKind.SPORTS, liveKindFor(NoticeFacts("com.example.app", ongoing = true, promoted = true, title = "NED 2 1 ESP")))
        // A count in a download stays a download.
        assertEquals(LiveKind.DOWNLOAD, liveKindFor(NoticeFacts("com.example.app", progress = 1, progressMax = 5, title = "Downloading 2 - 5")))
    }

    @Test fun ownReplyIsNoNews() {
        val chat = NoticeFacts("com.whatsapp", category = "msg", title = "Teun", text = "Ok")
        assertNull(alertKindFor(chat.copy(lastFromUser = true), RankFacts(importance = 4), IslandSettings(), own))
    }

    @Test fun noScreenshotAnnouncements() {
        val loud = RankFacts(importance = 4)
        val saved = NoticeFacts("com.android.systemui", channelId = "SCN_HEADSUP", title = "Screenshot saved", text = "Tap to view")
        assertNull(alertKindFor(saved, loud, IslandSettings(), own))
        assertNull(alertKindFor(NoticeFacts("com.android.systemui", category = "status", title = "Screen recording"), loud, IslandSettings(), own))
        assertNull(alertKindFor(NoticeFacts("com.sonymobile.screenshot", title = "Screenshot saved"), loud, IslandSettings(), own))
        assertNull(alertKindFor(NoticeFacts("com.example.screenrecorder", title = "Recording saved"), loud, IslandSettings(), own))
        assertTrue(isSystemChatter("com.android.systemui"))
        assertFalse(isSystemChatter("com.whatsapp"))
    }
}
