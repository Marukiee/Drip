package nl.markmaaktmedia.drip.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FitProfileTest {

    @Test fun eachWayKeepsItsOwnPlace() {
        // A phone without a camera hole, as the Xperia: Auto is a notch, Camera is a pill.
        val start = IslandSettings()
        val pill = start.withFit(FitMode.CUTOUT, hasCutout = false).copy(offsetYDp = 12f, offsetXDp = -4f)
        val back = pill.withFit(FitMode.AUTO, hasCutout = false)
        assertEquals(0f, back.offsetYDp)
        assertEquals(0f, back.offsetXDp)
        val again = back.withFit(FitMode.CUTOUT, hasCutout = false)
        assertEquals(12f, again.offsetYDp)
        assertEquals(-4f, again.offsetXDp)
    }

    @Test fun sameWayChangesNothing() {
        val moved = IslandSettings(offsetYDp = 5f)
        assertEquals(5f, moved.withFit(FitMode.NOTCH, hasCutout = false).offsetYDp)
    }
}
