package nl.markmaaktmedia.drip.sources

import nl.markmaaktmedia.drip.island.LiveKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The channel ids and texts are copied from Tandem's code; a change there has to be made here too. */
class TandemNoticesTest {
    private val tandem = "nl.markmaaktmedia.tandem"

    @Test fun channelIds() {
        assertEquals("connection", TANDEM_CHANNEL_CONNECTION)
        assertEquals("transfers", TANDEM_CHANNEL_TRANSFERS)
        assertEquals("quickshare_progress", TANDEM_CHANNEL_QUICKSHARE)
        assertTrue(isTandem("nl.markmaaktmedia.tandem.debug"))
    }

    @Test fun transfers() {
        // TandemService.postProgress: the file name over "Receiving", a bar of 100.
        val bar = NoticeFacts(tandem, channelId = "transfers", ongoing = true, progress = 40, progressMax = 100, title = "photo.jpg", text = "Receiving")
        assertEquals(TandemRole.TRANSFER, tandemRole(bar))
        assertEquals(LiveKind.DOWNLOAD, liveKindFor(bar))
        assertTrue(isLive(bar, "nl.markmaaktmedia.drip"))
        assertEquals("40%", liveShortText(bar, LiveKind.DOWNLOAD))
        // ReadProgress: "45% van MacBook".
        assertEquals(TandemRole.TRANSFER, tandemRole(bar.copy(packageName = "$tandem.debug", text = "45% van MacBook")))
        assertEquals(TandemRole.TRANSFER, tandemRole(bar.copy(progress = 0, progressMax = 0, indeterminate = true)))
        assertEquals(TandemRole.TRANSFER, tandemRole(bar.copy(channelId = "quickshare_progress", title = "Receiving from Pixel")))
        assertNull(tandemRole(bar.copy(packageName = "com.example.app")))
        // A received file on "incoming" is a plain notification.
        assertNull(tandemRole(NoticeFacts(tandem, channelId = "incoming", title = "Received photo.jpg", text = "From MacBook")))
    }

    @Test fun clipboard() {
        val copied = NoticeFacts(tandem, channelId = "quickshare_progress", title = "Received from Pixel", text = "Copied to the clipboard")
        assertEquals(TandemRole.CLIPBOARD, tandemRole(copied))
        assertEquals(TandemRole.CLIPBOARD, tandemRole(copied.copy(title = "Ontvangen van Pixel", text = "Gekopieerd naar het klembord")))
        assertNull(tandemRole(copied.copy(text = "photo.jpg")))
    }

    @Test fun connection() {
        val searching = NoticeFacts(tandem, channelId = "connection", ongoing = true, title = "Tandem", text = "Looking for your devices")
        assertEquals(TandemRole.CONNECTION, tandemRole(searching))
        assertEquals(emptyList<String>(), tandemConnectedTo(searching.text))
        assertEquals(emptyList<String>(), tandemConnectedTo("Zoeken naar je apparaten"))
        assertEquals(listOf("MacBook Pro"), tandemConnectedTo("Connected to MacBook Pro"))
        assertEquals(listOf("MacBook Pro", "Gaming PC"), tandemConnectedTo("Verbonden met MacBook Pro, Gaming PC"))
        // Only a bar is a card; the connection never is.
        assertEquals(false, isLive(searching, "nl.markmaaktmedia.drip"))
    }

    @Test fun onlyNewDevicesAreNews() {
        val w = TandemWatch()
        assertEquals(emptyList<String>(), w.joined("k", listOf("Mac"), seed = true))
        assertEquals(emptyList<String>(), w.joined("k", listOf("Mac"), seed = false))
        assertEquals(listOf("PC"), w.joined("k", listOf("Mac", "PC"), seed = false))
        assertEquals(emptyList<String>(), w.joined("k", emptyList(), seed = false))
        assertEquals(listOf("Mac"), w.joined("k", listOf("Mac"), seed = false))
        w.forget("k")
        assertEquals(listOf("Mac"), w.joined("k", listOf("Mac"), seed = false))
    }
}
