package nl.markmaaktmedia.drip

import nl.markmaaktmedia.drip.engine.IslandKeeper
import nl.markmaaktmedia.drip.engine.LauncherApp
import nl.markmaaktmedia.drip.engine.LauncherApps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandKeeperTest {
    private val island = "nl.markmaaktmedia.drip/nl.markmaaktmedia.drip.island.IslandService"
    private val other = "com.example.reader/com.example.reader.Speak"

    @Test fun anEmptySettingGetsOnlyTheIsland() {
        assertEquals(island, IslandKeeper.withService(null, island))
        assertEquals(island, IslandKeeper.withService("", island))
        assertEquals(island, IslandKeeper.withService(" : ", island))
    }

    @Test fun otherServicesStayInTheirOrder() {
        val tts = "com.example.tts/.Service"
        assertEquals("$other:$tts:$island", IslandKeeper.withService("$other:$tts", island))
    }

    @Test fun anIslandAlreadyThereIsNotAddedTwice() {
        assertEquals("$other:$island", IslandKeeper.withService("$other:$island", island))
    }

    @Test fun theShortFormCountsAsTheSameService() {
        val short = "nl.markmaaktmedia.drip/.island.IslandService"
        assertTrue(IslandKeeper.containsService("$other:$short", island))
        assertEquals("$other:$short", IslandKeeper.withService("$other:$short", island))
    }

    @Test fun theDebugBuildIsADifferentApp() {
        val debug = "nl.markmaaktmedia.drip.debug/nl.markmaaktmedia.drip.island.IslandService"
        assertFalse(IslandKeeper.containsService(island, debug))
        assertEquals("$island:$debug", IslandKeeper.withService(island, debug))
    }

    @Test fun blanksBetweenEntriesAreDropped() {
        assertEquals("$other:$island", IslandKeeper.withService("$other::", island))
    }
}

class LauncherAppsTest {
    private val apps = listOf(
        LauncherApp("com.spotify.music", "Spotify"),
        LauncherApp("com.google.android.apps.maps", "Maps"),
        LauncherApp("nl.cafe.app", "Café Nero"),
        LauncherApp("com.google.android.youtube", "YouTube Music"),
        LauncherApp("org.telegram.messenger", "Telegram"),
    )

    private fun labels(query: String) = LauncherApps.filter(apps, query).map { it.label }

    @Test fun anEmptyQueryKeepsEverything() {
        assertEquals(apps, LauncherApps.filter(apps, ""))
        assertEquals(apps, LauncherApps.filter(apps, "   "))
    }

    @Test fun caseAndAccentsDoNotCount() {
        assertEquals(listOf("Café Nero"), labels("CAFE"))
        assertEquals(listOf("Café Nero"), labels("café"))
    }

    @Test fun aNameThatStartsWithTheQueryComesFirst() {
        assertEquals(listOf("Maps"), labels("map"))
        // "music" starts a word of YouTube Music and only sits inside the package of Spotify.
        assertEquals(listOf("YouTube Music", "Spotify"), labels("music"))
    }

    @Test fun thePackageNameIsSearchedToo() {
        assertEquals(listOf("Telegram"), labels("messenger"))
    }

    @Test fun everyWordHasToMatch() {
        assertEquals(listOf("YouTube Music"), labels("you mus"))
        assertEquals(emptyList<String>(), labels("spotify maps"))
    }

    @Test fun chosenAppsComeFirst() {
        val ordered = LauncherApps.chosenFirst(apps, setOf("org.telegram.messenger", "com.google.android.apps.maps"))
        assertEquals(listOf("Maps", "Telegram", "Spotify", "Café Nero", "YouTube Music"), ordered.map { it.label })
    }
}
