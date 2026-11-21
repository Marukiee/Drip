package nl.markmaaktmedia.drip.ui.update

import nl.markmaaktmedia.drip.update.ReleaseInfo
import nl.markmaaktmedia.drip.update.UpdateState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class UpdateCardModelTest {
    private val release = ReleaseInfo("v0.1.30", "0.1.30", "", "", null, 8_228_233, null, "", "")

    @Test fun eachStateHasItsLook() {
        assertEquals(UpdateCardPhase.Rest, updateCardPhase(UpdateState.Available(release)))
        assertEquals(UpdateCardPhase.Rest, updateCardPhase(UpdateState.NeedsPermission(release)))
        assertEquals(UpdateCardPhase.Downloading, updateCardPhase(UpdateState.Downloading(release, 0.4f)))
        assertEquals(UpdateCardPhase.Installing, updateCardPhase(UpdateState.ReadyToInstall(release, "x")))
        assertEquals(UpdateCardPhase.Installing, updateCardPhase(UpdateState.Installing(release)))
        assertEquals(UpdateCardPhase.Installing, updateCardPhase(UpdateState.AwaitingConfirmation(release)))
        assertEquals(UpdateCardPhase.Failed, updateCardPhase(UpdateState.Failed("no")))
        assertEquals(null, updateCardPhase(UpdateState.UpToDate))
        assertEquals(null, updateCardPhase(UpdateState.Checking))
    }

    @Test fun theNotesAreTheBulletsOfTheSummary() {
        val summary = "Cards stay open\n\nBetter\n• An opened card stays\n\nFixed\n• Typing no longer closes it\n• A third"
        assertEquals(listOf("An opened card stays", "Typing no longer closes it"), updateNotes(summary))
    }

    @Test fun aSummaryWithoutBulletsGivesItsFirstLines() {
        assertEquals(listOf("One", "Two"), updateNotes("One\n\nTwo\nThree"))
    }

    @Test fun theChangelogGivesBothLanguagesNewThingsFirst() {
        val json = """
            [{"version": "0.1.30", "en": {"title": "T", "better": ["Better one"], "new": ["New one"], "fixed": ["Fixed one"]},
              "nl": {"title": "T", "fixed": ["Opgelost"]}},
             {"version": "0.1.29", "en": {"title": "Old", "fixed": ["Old fix"]}}]
        """.trimIndent()
        val notes = changelogNotes(json, "0.1.30")
        assertEquals(listOf("New one", "Better one"), notes["en"])
        assertEquals(listOf("Opgelost"), notes["nl"])
        assertEquals(listOf("Opgelost"), notesFor(notes, "nl"))
        assertEquals(listOf("New one", "Better one"), notesFor(notes, "de"))
    }

    @Test fun anUnknownVersionOrBrokenFileGivesNothing() {
        assertEquals(emptyMap<String, List<String>>(), changelogNotes("""[{"version": "0.1.29", "en": {"fixed": ["x"]}}]""", "0.1.30"))
        assertEquals(emptyMap<String, List<String>>(), changelogNotes("not json", "0.1.30"))
    }

    @Test fun megabytesHaveOneDecimalInTheLanguageOfThePhone() {
        assertEquals("8,2", megabytes(8_228_233, Locale.forLanguageTag("nl")))
        assertEquals("8.2", megabytes(8_228_233, Locale.ENGLISH))
    }
}
