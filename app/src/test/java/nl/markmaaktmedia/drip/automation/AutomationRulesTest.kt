package nl.markmaaktmedia.drip.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationRulesTest {

    private fun show(vararg pairs: Pair<String, Any?>) = parseAutomation(ACTION_SHOW, mapOf(*pairs)) as AutomationCommand.Show?

    @Test fun showWithEverything() {
        val c = show(
            "id" to "oven", "title" to "Pizza", "text" to "Almost", "icon" to "Timer", "color" to "#FF8800",
            "progress" to "40", "seconds" to 90, "duration" to "5000", "kind" to "live", "package" to "net.dinglisch.android.taskerm",
        )!!
        assertEquals("oven", c.id)
        assertEquals("Pizza", c.title)
        assertEquals("Almost", c.text)
        assertEquals("timer", c.icon)
        assertEquals(0xFFFF8800.toInt(), c.color)
        assertEquals(0.4f, c.progress!!, 1e-6f)
        assertEquals(90L, c.seconds)
        assertEquals(5000L, c.durationMs)
        assertTrue(c.lasting)
        assertEquals("net.dinglisch.android.taskerm", c.packageName)
        assertEquals("auto:oven", automationKey(c.id))
        assertTrue(isAutomationKey("auto:oven"))
        assertFalse(isAutomationKey("0|com.whatsapp|1|null|10123"))
    }

    @Test fun clampsAndDropsBadValues() {
        val c = show(
            "id" to "x", "title" to "T".repeat(500), "icon" to "rocket", "color" to "orange", "progress" to 250,
            "seconds" to -5, "duration" to 10, "kind" to "forever", "package" to "not a package",
        )!!
        assertEquals(80, c.title.length)
        assertNull(c.icon)
        assertNull(c.color)
        assertEquals(1f, c.progress!!, 0f)
        assertNull(c.seconds)
        assertEquals(1000L, c.durationMs)
        assertFalse(c.lasting)
        assertNull(c.packageName)
        assertEquals(0f, show("id" to "x", "title" to "t", "progress" to "-3")!!.progress!!, 0f)
        assertNull(show("id" to "x", "title" to "t", "progress" to "NaN")!!.progress)
        assertEquals(7L * 24 * 3600, show("id" to "x", "title" to "t", "seconds" to "99999999")!!.seconds)
        assertEquals(60_000L, show("id" to "x", "title" to "t", "duration" to 1e12)!!.durationMs)
        assertEquals(0.505f, show("id" to "x", "title" to "t", "progress" to "50,5")!!.progress!!, 1e-6f)
    }

    @Test fun needsAnIdAndSomethingToRead() {
        assertNull(show("title" to "No id"))
        assertNull(show("id" to " ", "title" to "Blank id"))
        assertNull(show("id" to "x".repeat(65), "title" to "Long id"))
        assertNull(show("id" to "x"))
        val textOnly = show("id" to "x", "text" to "Only text")!!
        assertEquals("Only text", textOnly.title)
        assertNull(textOnly.text)
        assertNull(parseAutomation("com.example.OTHER", mapOf("id" to "x", "title" to "t")))
        assertNull(parseAutomation(null, mapOf("id" to "x", "title" to "t")))
        assertNull(parseAutomation(ACTION_SHOW, mapOf("id" to listOf(1, 2), "title" to "t")))
    }

    @Test fun hide() {
        assertEquals(AutomationCommand.Hide("oven"), parseAutomation(ACTION_HIDE, mapOf("id" to "oven")))
        assertEquals(AutomationCommand.Hide("7"), parseAutomation(ACTION_HIDE, mapOf("id" to 7)))
        assertNull(parseAutomation(ACTION_HIDE, emptyMap()))
    }

    @Test fun colors() {
        assertEquals(0xFF112233.toInt(), parseColor("#112233"))
        assertEquals(0xFF112233.toInt(), parseColor("112233"))
        assertEquals(0x80112233.toInt(), parseColor("#80112233"))
        assertNull(parseColor("#12345"))
        assertNull(parseColor("#GGGGGG"))
        assertNull(parseColor(null))
    }
}
