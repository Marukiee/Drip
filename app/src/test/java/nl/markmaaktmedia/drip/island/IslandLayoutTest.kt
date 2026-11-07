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

    @Test fun openedCardTextStartsBelowTheCamera() {
        val hole = Hole(507, 33, 573, 99)
        val pixel = DeviceFit(1080, 2400, 2.625f, 132, hole, 110, false)
        val layout = IslandLayout(pixel, IslandSettings(expandedMarginDp = 8f, cutoutPaddingDp = 3f))
        val open = layout.geometry(Stage.EXPANDED, 400f)
        val textTop = open.top + layout.cardTopInset + layout.cardPadding
        assertEquals(hole.bottom + 3f * pixel.density, textTop, 0.01f)
        // The card grows by what it leaves free, so nothing at the bottom is cut off.
        assertEquals(400f + layout.cardTopInset, open.height, 0.01f)
    }

    @Test fun aSmallHoleHighUpTakesNoRoomFromTheCard() {
        val hole = Hole(530, 4, 550, 24)
        val pixel = DeviceFit(1080, 2400, 2.625f, 132, hole, 110, false)
        assertEquals(0f, IslandLayout(pixel, IslandSettings(expandedMarginDp = 8f)).cardTopInset, 0.01f)
    }

    @Test fun noHoleLeavesAPillCardAsItWas() {
        val layout = IslandLayout(xperia, IslandSettings(fitMode = FitMode.CUTOUT))
        assertEquals(0f, layout.cardTopInset, 0.01f)
    }

    @Test fun anAttachedNotchKeepsTheStatusBarFree() {
        val layout = IslandLayout(xperia, IslandSettings(expandedAttached = true))
        assertEquals(76f, layout.cardTopInset, 0.01f)
    }

    private val pixelHole = Hole(507, 33, 573, 99)
    private val pixel = DeviceFit(1080, 2400, 2.625f, 132, pixelHole, 110, false)

    /** Where the two lines of an announcement start. */
    private fun bannerWordsTop(g: IslandGeometry, density: Float) =
        g.top + g.height - g.bottomRadius - IslandLayout.ALERT_WORDS_ABOVE_MIDDLE_DP * density

    @Test fun bannerWordsStartBelowTheCamera() {
        val g = IslandLayout(pixel, IslandSettings()).geometry(Stage.ALERT)
        assertEquals(pixelHole.bottom + 3f * pixel.density, bannerWordsTop(g, pixel.density), 0.01f)
        // Its round bottom stays what it was, so the photo stays concentric with it.
        assertEquals(37f * pixel.density, g.bottomRadius, 0.01f)
    }

    @Test fun bannerWithoutACameraStaysAsItWas() {
        val g = IslandLayout(xperia, IslandSettings()).geometry(Stage.ALERT)
        assertEquals(0f, g.top, 0.01f)
        assertEquals(74f * xperia.density, g.height, 0.01f)
        val floating = IslandLayout(xperia, IslandSettings(fitMode = FitMode.CUTOUT)).geometry(Stage.ALERT)
        assertEquals(74f * xperia.density, floating.height, 0.01f)
    }

    @Test fun aCameraNearACornerLeavesTheBannerOnScreen() {
        val corner = pixel.copy(cutout = Hole(60, 33, 126, 99))
        val g = IslandLayout(corner, IslandSettings()).geometry(Stage.ALERT)
        assertEquals(540f, g.centerX, 0.01f)
        assertTrue(g.left >= 0f)
    }

    @Test fun aCameraInACornerIsNoticedOnBothSides() {
        assertTrue(pixel.copy(cutout = Hole(60, 33, 126, 99)).cameraInCorner)
        assertTrue(pixel.copy(cutout = Hole(954, 33, 1020, 99)).cameraInCorner)
    }

    @Test fun aCameraWhereThePillFitsIsNotInACorner() {
        assertFalse(pixel.cameraInCorner)
        assertFalse(pixel.copy(cutout = null).cameraInCorner)
        // Off centre, with room for the whole pill on both sides.
        assertFalse(pixel.copy(cutout = Hole(330, 33, 396, 99)).cameraInCorner)
    }

    @Test fun notchKeepsTheClosedIslandInTheMiddleWithACameraInACorner() {
        val corner = pixel.copy(cutout = Hole(60, 33, 126, 99))
        val g = IslandLayout(corner, IslandSettings(fitMode = FitMode.NOTCH)).geometry(Stage.COMPACT)
        assertEquals(540f, g.centerX, 0.01f)
        assertTrue(g.left >= 0f && g.right <= corner.screenWidth)
    }

    @Test fun aForcedNotchKeepsClearOfTheCameraToo() {
        val layout = IslandLayout(pixel, IslandSettings(fitMode = FitMode.NOTCH))
        val clear = pixelHole.bottom + 3f * pixel.density
        assertEquals(clear, bannerWordsTop(layout.geometry(Stage.ALERT), pixel.density), 0.01f)
        val open = layout.geometry(Stage.EXPANDED, 400f)
        assertEquals(clear, open.top + layout.cardTopInset + layout.cardPadding, 0.01f)
        assertTrue(layout.holeSpan(layout.geometry(Stage.COMPACT)) != null)
    }

    @Test fun anAttachedNotchGoesBelowACameraThatReachesPastTheStatusBar() {
        val deep = pixel.copy(statusBarHeight = 40, cutout = Hole(507, 40, 573, 120))
        val layout = IslandLayout(deep, IslandSettings(fitMode = FitMode.NOTCH, expandedAttached = true))
        assertEquals(120f + 3f * pixel.density, layout.cardTopInset + layout.cardPadding, 0.01f)
    }

    @Test fun aClockFitsBesideTheCameraInTheClosedIsland() {
        val layout = IslandLayout(pixel, IslandSettings())
        val g = layout.geometry(Stage.COMPACT)
        val span = layout.holeSpan(g)!!
        assertEquals(pixelHole.left - 3f * pixel.density - g.left, span.start, 0.01f)
        assertEquals(pixelHole.right + 3f * pixel.density - g.left, span.endInclusive, 0.01f)
        val beside = g.width - span.endInclusive - (layout.compactHeight / 2f - 4f * pixel.density)
        assertTrue(beside >= 50f * pixel.density)
    }

    @Test fun aWideCameraGrowsTheClosedIslandButNotAWidthSetByHand() {
        val wide = pixel.copy(cutout = Hole(400, 0, 680, 90))
        val layout = IslandLayout(wide, IslandSettings())
        val g = layout.geometry(Stage.COMPACT)
        assertTrue(g.width - layout.holeSpan(g)!!.endInclusive >= 50f * pixel.density)
        assertEquals(120f * pixel.density, IslandLayout(wide, IslandSettings(compactWidthDp = 120f)).compactWidth, 0.01f)
    }

    @Test fun aCameraBesideTheShapeIsNotBehindIt() {
        val layout = IslandLayout(pixel.copy(cutout = Hole(60, 33, 126, 99)), IslandSettings(fitMode = FitMode.NOTCH))
        assertEquals(null, layout.holeSpan(layout.geometry(Stage.COMPACT)))
    }

    @Test fun aRingPlacedByHandCountsAsTheCamera() {
        val s = IslandSettings(fitMode = FitMode.CUTOUT, idleWidthDp = 36f, idleHeightDp = 36f, offsetYDp = 10f, cutoutPaddingDp = 3f)
        val layout = IslandLayout(xperia, s)
        val hole = layout.hole!!
        val d = xperia.density
        assertEquals(30f * d, hole.width().toFloat(), 1f)
        assertEquals(30f * d, hole.height().toFloat(), 1f)
        assertEquals(540f, hole.exactCenterX(), 1f)
        assertEquals(38f + 10f * d, hole.exactCenterY(), 1f)
        assertEquals(hole.bottom + 3f * d, bannerWordsTop(layout.geometry(Stage.ALERT), d), 0.01f)
        assertTrue(layout.holeSpan(layout.geometry(Stage.COMPACT)) != null)
    }

    @Test fun aRingMovedByHandIsRoundUnlessItWasMadeWider() {
        val s = IslandSettings(fitMode = FitMode.CUTOUT, offsetXDp = 4f)
        val layout = IslandLayout(xperia, s)
        assertEquals(layout.hole!!.height().toFloat(), layout.hole!!.width().toFloat(), 1f)
    }

    @Test fun nothingPlacedOrAskedMeansNoCamera() {
        assertEquals(null, IslandLayout(xperia, IslandSettings(fitMode = FitMode.CUTOUT)).hole)
        assertEquals(null, IslandLayout(xperia, IslandSettings(idleWidthDp = 60f)).hole)
        assertEquals(null, IslandLayout(xperia, IslandSettings(fitMode = FitMode.NOTCH, idleWidthDp = 60f)).hole)
    }

    @Test fun whatTheDisplayReportsBeatsAPlacedRing() {
        val s = IslandSettings(fitMode = FitMode.CUTOUT, offsetXDp = 20f)
        assertEquals(pixelHole, IslandLayout(pixel, s).hole)
    }

    @Test fun aRingIsNotACameraOnItsSide() {
        val s = IslandSettings(fitMode = FitMode.CUTOUT, idleWidthDp = 36f)
        assertEquals(null, IslandLayout(xperia.copy(landscape = true), s).hole)
    }

    @Test fun onItsSideTheCardsStayClearOfTheCamera() {
        val side = DeviceFit(2400, 1080, 2.625f, 63, null, 110, true, sideHole = 99)
        val layout = IslandLayout(side, IslandSettings())
        val clear = 99f + 3f * side.density
        for (stage in listOf(Stage.ALERT, Stage.EXPANDED)) {
            val g = layout.geometry(stage, 400f)
            assertTrue(g.left >= clear - 0.01f)
            assertTrue(g.right <= 2400f - clear + 0.01f)
        }
    }

    @Test fun aRoomyMarginIsNotNarrowedForASideCamera() {
        val side = DeviceFit(2400, 1080, 2.625f, 63, null, 110, true, sideHole = 4)
        val margin = 8f * side.density
        assertEquals(2400f - 2 * margin, IslandLayout(side, IslandSettings(expandedMarginDp = 8f)).fullWidth, 0.01f)
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
