package nl.markmaaktmedia.drip.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GestureDefaultsTest {
    private val pullDown = Gesture.SWIPE_DOWN

    @Test fun aMessagePulledDownOpensItsCard() {
        assertEquals(GestureAction.EXPAND, IslandSettings().actionFor(GestureScope.NOTICE, pullDown))
    }

    @Test fun everythingElseStillOpensTheShade() {
        GestureScope.entries.filter { it != GestureScope.NOTICE }.forEach {
            assertEquals(GestureAction.NOTIFICATIONS, IslandSettings().actionFor(it, pullDown))
        }
    }

    @Test fun aChoiceOfTheirOwnWins() {
        val forMessages = IslandSettings().withGesture(GestureScope.NOTICE, pullDown, GestureAction.NOTIFICATIONS)
        assertEquals(GestureAction.NOTIFICATIONS, forMessages.actionFor(GestureScope.NOTICE, pullDown))
        val everywhere = IslandSettings(gestureSwipeDown = GestureAction.NONE)
        assertEquals(GestureAction.NONE, everywhere.actionFor(GestureScope.NOTICE, pullDown))
    }
}
