package nl.markmaaktmedia.drip.sources

import nl.markmaaktmedia.drip.island.LyricLine
import nl.markmaaktmedia.drip.island.currentLineIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsRulesTest {

    @Test fun readsLrc() {
        val lines = parseLrc(
            """
            [ar:John Woodward]
            [00:12.30]And I wait, yeah I wait
            [00:15.5]Hey hey, I'm ok
            [00:20.123][01:40.00]I don't need this anyway
            [00:25.00]
            [00:26.00]<00:26.00>What's <00:26.50>yours
            """.trimIndent(),
        )
        assertEquals(LyricLine(12_300, "And I wait, yeah I wait"), lines[0])
        assertEquals(LyricLine(15_500, "Hey hey, I'm ok"), lines[1])
        assertEquals(LyricLine(20_123, "I don't need this anyway"), lines[2])
        assertEquals(LyricLine(25_000, ""), lines[3])
        assertEquals(LyricLine(26_000, "What's yours"), lines[4])
        assertEquals(LyricLine(100_000, "I don't need this anyway"), lines.last())
    }

    @Test fun tellsSyncedFromPlain() {
        assertTrue(looksSynced("[00:01.00]a\n[00:02.00]b\n[00:03.00]c"))
        assertFalse(looksSynced("a\nb\nc"))
    }

    @Test fun plainKeepsOneBreak() {
        assertEquals(listOf("a", "", "b"), plainLines("\na\n\n\nb\n\n").map { it.text })
    }

    @Test fun followsTheSong() {
        val lines = listOf(LyricLine(1000, "a"), LyricLine(5000, "b"), LyricLine(9000, "c"))
        assertEquals(-1, currentLineIndex(lines, 0))
        assertEquals(0, currentLineIndex(lines, 900))
        assertEquals(1, currentLineIndex(lines, 6000))
        assertEquals(2, currentLineIndex(lines, 60_000))
    }

    @Test fun cleansTitles() {
        assertEquals("Song", cleanTitle("Song (feat. Someone)"))
        assertEquals("Song", cleanTitle("Song - Remastered 2011"))
        assertEquals("Song", cleanTitle("Song [Live]"))
        assertEquals("Love - Me", cleanTitle("Love - Me"))
        assertEquals("A", firstArtist("A, B & C"))
    }
}
