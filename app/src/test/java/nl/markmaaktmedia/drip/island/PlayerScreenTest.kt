package nl.markmaaktmedia.drip.island

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerScreenTest {
    private val screen = 1080

    @Test fun theBigPlayersBarCounts() {
        assertTrue(isSeekBar("android.widget.SeekBar", draggable = true, width = 953, screenWidth = screen))
        // Lyrics keep a shorter bar beside their pause button.
        assertTrue(isSeekBar("android.widget.SeekBar", draggable = true, width = 674, screenWidth = screen))
    }

    @Test fun aBarThatCannotBeDraggedIsTheMiniPlayers() {
        assertFalse(isSeekBar("android.widget.ProgressBar", draggable = false, width = 1000, screenWidth = screen))
    }

    @Test fun aSmallSliderIsNoSeekBar() {
        assertFalse(isSeekBar("android.widget.SeekBar", draggable = true, width = 200, screenWidth = screen))
    }

    @Test fun aComposeBarWithoutTheClassNameStillCounts() {
        assertTrue(isSeekBar("android.view.View", draggable = true, width = 900, screenWidth = screen))
    }

    @Test fun theTitleMatchesHoweverItIsWritten() {
        assertTrue(isSongTitle("Venstrå", "Venstrå"))
        assertTrue(isSongTitle(" VENSTRÅ ", "venstrå"))
        assertFalse(isSongTitle("Venstrå (live)", "Venstrå"))
        assertFalse(isSongTitle(null, "Venstrå"))
    }
}
