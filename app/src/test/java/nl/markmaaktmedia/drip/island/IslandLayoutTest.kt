package nl.markmaaktmedia.drip.island

import nl.markmaaktmedia.drip.data.FitMode
import nl.markmaaktmedia.drip.data.IslandSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandLayoutTest {
    private val xperia = DeviceFit(1080, 2340, 2.625f, 76, null, 92, false)

    @Test fun noCameraHoleMeansNotch() {
        val layout = IslandLayout(xperia, IslandSettings())
        assertTrue(layout.notch)
        val idle = layout.geometry(Stage.IDLE)
        assertEquals(0f, idle.top, 0.01f)
        // Hangs from the edge with inward shoulders.
        assertTrue(idle.topCorner > 0f)
    }

    @Test fun openedTopCornersAreConcentricWithTheScreen() {
        val s = IslandSettings(expandedMarginDp = 8f)
        val layout = IslandLayout(xperia, s)
        val open = layout.geometry(Stage.EXPANDED, 400f)
        val margin = 8f * xperia.density
        assertEquals(margin, open.top, 0.01f)
        assertEquals(-(92f - margin), open.topCorner, 0.01f)
        assertEquals(xperia.screenWidth - 2 * margin, open.width, 0.01f)
    }

    @Test fun bottomButtonsAreConcentricWithTheIsland() {
        val layout = IslandLayout(xperia, IslandSettings(expandedBottomRadiusDp = 40f))
        assertEquals((40f - 16f) * xperia.density, layout.buttonOuterRadius, 0.01f)
    }

    @Test fun pillHugsTheCameraHole() {
        // Roughly a Pixel: a hole in the middle of the top edge.
        val hole = Hole(510, 30, 570, 90)
        val pixel = DeviceFit(1080, 2424, 2.625f, 110, hole, 110, false)
        val layout = IslandLayout(pixel, IslandSettings(cutoutPaddingDp = 4f))
        assertFalse(layout.notch)
        val idle = layout.geometry(Stage.IDLE)
        assertEquals(540f, idle.centerX, 0.01f)
        assertEquals(60f, idle.top + idle.height / 2, 0.01f)
        assertTrue(idle.topCorner < 0f)
    }

    @Test fun forcedNotchIgnoresTheHole() {
        val pixel = DeviceFit(1080, 2424, 2.625f, 110, Hole(510, 30, 570, 90), 110, false)
        assertTrue(IslandLayout(pixel, IslandSettings(fitMode = FitMode.NOTCH)).notch)
    }

    @Test fun cornersNeverOverlap() {
        val g = IslandGeometry(100f, 0f, 40f, 20f, 30f, 30f).clamped()
        assertTrue(g.topCorner + g.bottomRadius <= g.height + 0.01f)
        assertTrue(g.bottomRadius <= g.width / 2 + 0.01f)
    }
}
