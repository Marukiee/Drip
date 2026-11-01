package nl.markmaaktmedia.drip.sources

import nl.markmaaktmedia.drip.island.PlugType
import nl.markmaaktmedia.drip.sources.BatteryEvent.FULL
import nl.markmaaktmedia.drip.sources.BatteryEvent.LOW
import nl.markmaaktmedia.drip.sources.BatteryEvent.PLUGGED_IN
import nl.markmaaktmedia.drip.sources.BatteryEvent.UNPLUGGED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackersTest {

    @Test fun firstValueOnlySeeds() {
        val w = ChangeWatch<Int>()
        assertFalse(w.update(1))
        assertFalse(w.update(1))
        assertTrue(w.update(0))
        w.reset()
        assertFalse(w.update(5))
    }

    @Test fun lowWarnsOncePerDischarge() {
        val w = BatteryWatch()
        assertEquals(emptyList<BatteryEvent>(), w.update(40, plugged = false))
        assertEquals(emptyList<BatteryEvent>(), w.update(16, false))
        assertEquals(listOf(LOW), w.update(15, false))
        assertEquals(emptyList<BatteryEvent>(), w.update(14, false))
        assertEquals(emptyList<BatteryEvent>(), w.update(15, false))
        assertEquals(listOf(LOW), w.update(5, false))
        assertEquals(emptyList<BatteryEvent>(), w.update(4, false))
    }

    @Test fun startingLowStaysQuietUntilTheNextLevel() {
        val w = BatteryWatch()
        assertEquals(emptyList<BatteryEvent>(), w.update(10, plugged = false))
        assertEquals(emptyList<BatteryEvent>(), w.update(9, false))
        assertEquals(listOf(LOW), w.update(5, false))
    }

    @Test fun aBigDropWarnsOnce() {
        val w = BatteryWatch()
        w.update(16, false)
        assertEquals(listOf(LOW), w.update(4, false))
        assertEquals(emptyList<BatteryEvent>(), w.update(3, false))
    }

    @Test fun chargingRearmsTheWarnings() {
        val w = BatteryWatch()
        w.update(20, false)
        assertEquals(listOf(LOW), w.update(15, false))
        assertEquals(listOf(PLUGGED_IN), w.update(15, true))
        w.update(50, true)
        assertEquals(listOf(UNPLUGGED), w.update(50, false))
        assertEquals(listOf(LOW), w.update(15, false))
        assertEquals(listOf(LOW), w.update(5, false))
    }

    @Test fun unpluggingLowDoesNotWarnAtOnce() {
        val w = BatteryWatch()
        w.update(10, true)
        assertEquals(listOf(UNPLUGGED), w.update(12, false))
        assertEquals(listOf(LOW), w.update(5, false))
    }

    @Test fun fullOncePerCharge() {
        val w = BatteryWatch()
        w.update(90, true)
        assertEquals(emptyList<BatteryEvent>(), w.update(99, true))
        assertEquals(listOf(FULL), w.update(100, true))
        assertEquals(emptyList<BatteryEvent>(), w.update(100, true))
        assertEquals(emptyList<BatteryEvent>(), w.update(99, true))
        assertEquals(emptyList<BatteryEvent>(), w.update(100, true))
        assertEquals(listOf(UNPLUGGED), w.update(100, false))
        assertEquals(listOf(PLUGGED_IN), w.update(98, true))
        assertEquals(listOf(FULL), w.update(100, true))
    }

    @Test fun pluggingInWhenFullSaysNothingMore() {
        val w = BatteryWatch()
        w.update(100, false)
        assertEquals(listOf(PLUGGED_IN), w.update(100, true))
        assertEquals(emptyList<BatteryEvent>(), w.update(100, true))
    }

    @Test fun startingFullAndPluggedStaysQuiet() {
        val w = BatteryWatch()
        assertEquals(emptyList<BatteryEvent>(), w.update(100, true))
        assertEquals(emptyList<BatteryEvent>(), w.update(100, true))
    }

    @Test fun echoWindow() {
        val e = EchoWindow(600)
        assertFalse(e.isEcho(1_000))
        e.markOwn(1_000)
        assertTrue(e.isEcho(1_000))
        assertTrue(e.isEcho(1_600))
        assertFalse(e.isEcho(1_601))
    }

    @Test fun notificationMemory() {
        val m = NotificationMemory()
        m.seed("a", "hi")
        assertFalse(m.isFresh("a", "hi", onlyAlertOnce = false))
        assertTrue(m.isFresh("a", "hi again", onlyAlertOnce = false))
        assertFalse(m.isFresh("a", "and again", onlyAlertOnce = true))
        assertTrue(m.isFresh("b", "new", onlyAlertOnce = true))
        m.forget("b")
        assertTrue(m.isFresh("b", "new", onlyAlertOnce = true))
    }

    @Test fun plugTypes() {
        assertEquals(PlugType.NONE, plugTypeOf(0))
        assertEquals(PlugType.AC, plugTypeOf(1))
        assertEquals(PlugType.USB, plugTypeOf(2))
        assertEquals(PlugType.WIRELESS, plugTypeOf(4))
        assertEquals(PlugType.DOCK, plugTypeOf(8))
    }

    @Test fun volumeAndBrightness() {
        assertEquals(0.5f, volumeLevel(8, 0, 16, muted = false), 0.001f)
        assertEquals(0f, volumeLevel(8, 0, 16, muted = true), 0.001f)
        assertEquals(1f, volumeLevel(15, 0, 15, muted = false), 0.001f)
        assertEquals(8, volumeIndex(0.5f, 0, 16))
        assertEquals(1, volumeIndex(-1f, 1, 25))
        assertEquals(0f, brightnessSliderLevel(1), 0.001f)
        assertEquals(1f, brightnessSliderLevel(255), 0.001f)
        // The curve lifts the low end, like the system slider: a twelfth of the light is half the bar.
        assertEquals(0.5f, brightnessSliderLevel(1 + 254 / 12), 0.01f)
    }
}
