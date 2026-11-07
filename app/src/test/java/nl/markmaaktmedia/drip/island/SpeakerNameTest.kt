package nl.markmaaktmedia.drip.island

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeakerNameTest {
    @Test fun twoSpeakersShowTheFirstAndOneMore() {
        assertEquals("Marks kamer 2 +1", shortSpeakerName("Marks kamer 2 + Marks kamer"))
    }

    @Test fun threeSpeakersShowTwoMore() {
        assertEquals("Living room +2", shortSpeakerName("Living room + Kitchen + Bathroom"))
    }

    @Test fun oneSpeakerStaysAsItIs() {
        assertEquals("Marks kamer", shortSpeakerName("Marks kamer"))
    }

    @Test fun aNameTheSonosAppMadeShortStays() {
        assertEquals("Living room + 1", shortSpeakerName("Living room + 1"))
    }

    @Test fun aPlusInsideANameIsNotAGroup() {
        assertEquals("Hi+Fi", shortSpeakerName("Hi+Fi"))
    }
}
