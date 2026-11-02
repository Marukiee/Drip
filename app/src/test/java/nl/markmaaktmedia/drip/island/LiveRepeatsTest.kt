package nl.markmaaktmedia.drip.island

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveRepeatsTest {

    private fun live(key: String, kind: LiveKind, title: String) = LiveInfo(key = key, packageName = "p", appLabel = "a", kind = kind, title = title)

    @Test fun oneInstallFromTwoSources() {
        val store = live("store", LiveKind.DOWNLOAD, "Spotify")
        val session = live("session", LiveKind.INSTALL, "spotify ")
        val timer = live("timer", LiveKind.TIMER, "Spotify")
        assertEquals(listOf("session", "timer"), withoutRepeats(listOf(store, session, timer)).map { it.key })
    }

    @Test fun differentAppsStay() {
        val a = live("a", LiveKind.DOWNLOAD, "Rushes.dmg")
        val b = live("b", LiveKind.INSTALL, "Tandem")
        assertEquals(listOf("a", "b"), withoutRepeats(listOf(a, b)).map { it.key })
    }
}
