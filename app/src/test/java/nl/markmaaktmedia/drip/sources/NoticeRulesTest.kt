package nl.markmaaktmedia.drip.sources

import nl.markmaaktmedia.drip.data.IslandSettings
import nl.markmaaktmedia.drip.island.ActionKind
import nl.markmaaktmedia.drip.island.AlertKind
import nl.markmaaktmedia.drip.island.LiveKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeRulesTest {
    private val own = "nl.markmaaktmedia.drip"
    private val loud = RankFacts(importance = 4)
    private val defaults = IslandSettings()

    private fun chat(pkg: String = "com.whatsapp") = NoticeFacts(pkg, category = "msg", title = "Teun", text = "Ben je er?")

    @Test fun liveKinds() {
        assertEquals(LiveKind.TIMER, liveKindFor(NoticeFacts("com.google.android.deskclock", ongoing = true, showsChronometer = true, countDown = true, title = "Timer")))
        assertEquals(LiveKind.STOPWATCH, liveKindFor(NoticeFacts("com.google.android.deskclock", ongoing = true, showsChronometer = true, title = "Stopwatch")))
        assertEquals(LiveKind.STOPWATCH, liveKindFor(NoticeFacts("com.example.sport", category = "stopwatch", showsChronometer = true)))
        assertEquals(LiveKind.NAVIGATION, liveKindFor(NoticeFacts("com.google.android.apps.maps", title = "150 m", text = "Turn left")))
        assertEquals(LiveKind.NAVIGATION, liveKindFor(NoticeFacts("com.example.nav", category = "navigation")))
        assertEquals(LiveKind.DELIVERY, liveKindFor(NoticeFacts("com.ubercab.eats", title = "Your driver is on the way")))
        assertEquals(LiveKind.RIDE, liveKindFor(NoticeFacts("com.ubercab", title = "Your driver is on the way")))
        assertEquals(LiveKind.DELIVERY, liveKindFor(NoticeFacts("com.example.food", title = "Je bestelling wordt bezorgd")))
        assertEquals(LiveKind.RIDE, liveKindFor(NoticeFacts("com.example.cab", title = "Your ride arrives in 3 min")))
        assertEquals(LiveKind.RECORDING, liveKindFor(NoticeFacts("com.android.systemui", showsChronometer = true, title = "Recording screen")))
        assertEquals(LiveKind.DOWNLOAD, liveKindFor(NoticeFacts("com.android.vending", progress = 3, progressMax = 10, title = "Downloading Spotify")))
        assertEquals(LiveKind.TIMER, liveKindFor(NoticeFacts("com.example.oven", showsChronometer = true, countDown = true, title = "Pizza")))
        assertEquals(LiveKind.GENERIC, liveKindFor(NoticeFacts("com.strava", category = "workout", showsChronometer = true, title = "New record")))
        assertEquals(LiveKind.GENERIC, liveKindFor(NoticeFacts("com.example.app", progress = 1, progressMax = 2, title = "Copying photos")))
        // "Override" holds "ride" but not at the start of a word.
        assertEquals(LiveKind.GENERIC, liveKindFor(NoticeFacts("com.example.app", showsChronometer = true, title = "Override active")))
    }

    @Test fun liveNeedsSomethingToShow() {
        val timer = NoticeFacts("com.example.oven", ongoing = true, showsChronometer = true, countDown = true, title = "Pizza")
        assertTrue(isLive(timer, own))
        assertFalse(isLive(timer.copy(ongoing = false), own))
        assertFalse(isLive(NoticeFacts("com.example.vpn", ongoing = true, title = "VPN connected"), own))
        assertFalse(isLive(NoticeFacts("com.example.app", ongoing = true, showsChronometer = true, text = "Drip is running"), own))
        assertTrue(isLive(NoticeFacts("com.example.app", ongoing = true, promoted = true, title = "Boarding"), own))
        assertTrue(isLive(NoticeFacts("com.example.nav", ongoing = true, category = "navigation"), own))
        assertTrue(isLive(NoticeFacts("com.example.app", ongoing = true, indeterminate = true, title = "Backing up"), own))
        assertFalse(isLive(timer.copy(packageName = own), own))
        assertFalse(isLive(timer.copy(template = "MediaStyle"), own))
        assertFalse(isLive(timer.copy(category = "call"), own))
        // An alarm going off rings full screen: that is an alert.
        assertFalse(isLive(NoticeFacts("com.google.android.deskclock", ongoing = true, category = "alarm", hasFullScreenIntent = true), own))
    }

    @Test fun shortText() {
        assertEquals("40%", shortTextFor(NoticeFacts("p", progress = 4, progressMax = 10)))
        assertEquals("3 min", shortTextFor(NoticeFacts("p", title = "3 min", text = "Head north on Damrak")))
        assertEquals("ETA 12:30", shortTextFor(NoticeFacts("p", progress = 4, progressMax = 10), criticalText = "ETA 12:30"))
        assertNull(shortTextFor(NoticeFacts("p", title = "Recording", indeterminate = true)))
    }

    @Test fun calls() {
        val ringing = NoticeFacts("com.google.android.dialer", category = "call", ongoing = true, hasFullScreenIntent = true)
        assertTrue(isCall(ringing))
        assertTrue(isIncomingCall(ringing))
        assertFalse(isIncomingCall(ringing.copy(showsChronometer = true)))
        assertTrue(isIncomingCall(NoticeFacts("p", callType = 1, ongoing = true, showsChronometer = true)))
        assertFalse(isIncomingCall(NoticeFacts("p", callType = 2, ongoing = true, hasFullScreenIntent = true)))
        assertTrue(isCall(NoticeFacts("com.whatsapp", template = "CallStyle", callType = 2)))
        // A missed call some app filed under "call" is a plain notification.
        assertFalse(isCall(NoticeFacts("com.example.voip", category = "call", title = "Missed call")))
    }

    @Test fun alertWorthiness() {
        assertEquals(AlertKind.NOTIFICATION, alertKindFor(chat(), loud, defaults, own))
        assertNull(alertKindFor(chat(), RankFacts(importance = 2), defaults, own))
        assertNull(alertKindFor(chat(), loud.copy(ambient = true), defaults, own))
        assertNull(alertKindFor(chat(), loud.copy(matchesFilter = false), defaults, own))
        assertNull(alertKindFor(chat(), loud.copy(suspended = true), defaults, own))
        assertNull(alertKindFor(chat().copy(groupSummary = true), loud, defaults, own))
        assertNull(alertKindFor(chat().copy(ongoing = true), loud, defaults, own))
        assertNull(alertKindFor(chat().copy(hasMediaSession = true), loud, defaults, own))
        assertNull(alertKindFor(chat(own), loud, defaults, own))
        assertNull(alertKindFor(chat().copy(title = null, text = " "), loud, defaults, own))
        assertNull(alertKindFor(NoticeFacts("com.google.android.dialer", category = "call", ongoing = true, hasFullScreenIntent = true, title = "Mam"), loud, defaults, own))
    }

    @Test fun alarmsGetThroughAlthoughOngoing() {
        val alarm = NoticeFacts("com.google.android.deskclock", category = "alarm", ongoing = true, hasFullScreenIntent = true, title = "Alarm", text = "07:00")
        assertEquals(AlertKind.ALARM, alertKindFor(alarm, loud, defaults, own))
    }

    @Test fun alertSettings() {
        assertNull(alertKindFor(chat(), loud, defaults.copy(showNotifications = false), own))
        val picked = defaults.copy(notifyAllApps = false, notifyApps = setOf("org.telegram.messenger"))
        assertNull(alertKindFor(chat(), loud, picked, own))
        assertEquals(AlertKind.NOTIFICATION, alertKindFor(chat("org.telegram.messenger"), loud, picked, own))
    }

    @Test fun actionKinds() {
        assertEquals(ActionKind.REPLY, actionKindFor(1, "Whatever", false))
        assertEquals(ActionKind.MARK_READ, actionKindFor(2, "Done", false))
        assertEquals(ActionKind.MUTE, actionKindFor(7, "Unmute", false))
        assertEquals(ActionKind.REPLY, actionKindFor(0, "Send", hasRemoteInput = true))
        assertEquals(ActionKind.SPEAKER, actionKindFor(0, "Speaker", false, inCall = true))
        assertEquals(ActionKind.SPEAKER, actionKindFor(0, "Luidspreker", false, inCall = true))
        assertEquals(ActionKind.MUTE, actionKindFor(0, "Dempen", false, inCall = true))
        assertEquals(ActionKind.HANG_UP, actionKindFor(0, "Hang up", false, inCall = true))
        assertEquals(ActionKind.HANG_UP, actionKindFor(0, "Ophangen", false, inCall = true))
        assertEquals(ActionKind.HANG_UP, actionKindFor(0, "Gesprek beëindigen", false, inCall = true))
        assertEquals(ActionKind.ANSWER, actionKindFor(0, "Answer", false, inCall = true))
        assertEquals(ActionKind.ANSWER, actionKindFor(0, "Opnemen", false, inCall = true))
        assertEquals(ActionKind.ANSWER, actionKindFor(0, "Beantwoorden", false, inCall = true))
        assertEquals(ActionKind.DECLINE, actionKindFor(0, "Decline", false, inCall = true))
        assertEquals(ActionKind.DECLINE, actionKindFor(0, "Weigeren", false, inCall = true))
        assertEquals(ActionKind.REPLY, actionKindFor(0, "Beantwoorden", false, inCall = false))
        assertEquals(ActionKind.MARK_READ, actionKindFor(0, "Mark as read", false))
        assertEquals(ActionKind.MARK_READ, actionKindFor(0, "Markeren als gelezen", false))
        assertEquals(ActionKind.OPEN, actionKindFor(0, "Openen", false))
        assertEquals(ActionKind.OTHER, actionKindFor(0, "Archive", false))
        assertEquals(ActionKind.OTHER, actionKindFor(0, null, false))
    }
}
