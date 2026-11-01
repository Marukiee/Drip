package nl.markmaaktmedia.drip.sources

import nl.markmaaktmedia.drip.island.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaRulesTest {

    @Test fun customKinds() {
        assertEquals(CustomKind.SHUFFLE, customKindOf("com.app.ACTION_TOGGLE_SHUFFLE", "Shuffle"))
        assertEquals(CustomKind.REPEAT, customKindOf("repeatMode", "Repeat all"))
        assertEquals(CustomKind.REPEAT, customKindOf("x", "Herhalen"))
        assertEquals(CustomKind.LIKE, customKindOf("ADD_TO_COLLECTION", "Add to Liked Songs"))
        assertEquals(CustomKind.LIKE, customKindOf("thumbs_up", "Thumbs up"))
        assertEquals(CustomKind.LIKE, customKindOf("fav", "Vind ik leuk"))
        assertNull(customKindOf("thumbs_down", "Thumbs down"))
        assertNull(customKindOf("x", "Vind ik niet leuk"))
        assertNull(customKindOf("SEEK_FORWARD_30", "Forward 30 seconds"))
    }

    @Test fun repeatModes() {
        assertEquals(RepeatMode.ONE, repeatModeOf("repeatOne", null))
        assertEquals(RepeatMode.ONE, repeatModeOf("REPEAT_MODE_ONE", "Repeat one"))
        assertEquals(RepeatMode.ALL, repeatModeOf("REPEAT_MODE_ALL", "Repeat all"))
        assertEquals(RepeatMode.ALL, repeatModeOf("x", "Herhaal alle nummers"))
        assertEquals(RepeatMode.OFF, repeatModeOf("REPEAT_MODE_OFF", "Repeat off"))
        assertEquals(RepeatMode.OFF, repeatModeOf("repeat", "Repeat"))
    }

    @Test fun shuffleState() {
        assertTrue(shuffleOnOf("x", "Shuffle on"))
        assertFalse(shuffleOnOf("x", "Shuffle off"))
        assertTrue(shuffleOnOf("x", "Turn off shuffle"))
        assertFalse(shuffleOnOf("x", "Enable shuffle"))
        assertFalse(shuffleOnOf("x", "Shuffle"))
    }

    @Test fun likeState() {
        assertTrue(likedOf("REMOVE_FROM_COLLECTION", "Remove from Liked Songs"))
        assertFalse(likedOf("ADD_TO_COLLECTION", "Add to Liked Songs"))
        assertTrue(likedOf("x", "Unlike"))
        assertFalse(likedOf("x", "Like"))
        assertTrue(likedOf("x", "Added to Liked Songs"))
    }

    @Test fun playingSessionWins() {
        val sessions = listOf(
            SessionView(playing = false, hasMetadata = true, lastPlayingAt = 50),
            SessionView(playing = true, hasMetadata = true, lastPlayingAt = 10),
        )
        assertEquals(1, pickSession(sessions))
    }

    @Test fun newestPlayerWinsAmongPlaying() {
        val sessions = listOf(
            SessionView(playing = true, hasMetadata = true, lastPlayingAt = 10),
            SessionView(playing = true, hasMetadata = true, lastPlayingAt = 20),
        )
        assertEquals(1, pickSession(sessions))
    }

    @Test fun lastPlayedWhenNothingPlays() {
        val sessions = listOf(
            SessionView(playing = false, hasMetadata = true, lastPlayingAt = 10),
            SessionView(playing = false, hasMetadata = true, lastPlayingAt = 30),
            SessionView(playing = false, hasMetadata = false, lastPlayingAt = 90),
            SessionView(playing = false, hasMetadata = true, usable = false, lastPlayingAt = 80),
        )
        assertEquals(1, pickSession(sessions))
    }

    @Test fun tieGoesToAndroidsOrder() {
        val sessions = listOf(
            SessionView(playing = false, hasMetadata = true),
            SessionView(playing = false, hasMetadata = true),
        )
        assertEquals(0, pickSession(sessions))
    }

    @Test fun nothingToShow() {
        assertNull(pickSession(emptyList()))
        assertNull(pickSession(listOf(SessionView(playing = true, hasMetadata = false))))
    }

    @Test fun nextInTheQueue() {
        val queue = listOf(QueueEntry(10, "One"), QueueEntry(11, "Two"), QueueEntry(12, " "), QueueEntry(13, "Four"))
        assertEquals("Two", nextInQueue(queue, 10))
        assertNull(nextInQueue(queue, 11))
        assertNull(nextInQueue(queue, 13))
        assertNull(nextInQueue(queue, 99))
        assertNull(nextInQueue(queue, -1))
        assertNull(nextInQueue(emptyList(), 10))
    }

    @Test fun spotifyHeart() {
        assertEquals(CustomKind.LIKE, customKindOf("com.spotify.music.ADD_TO_COLLECTION", "Add to Your Library"))
        assertEquals(CustomKind.LIKE, customKindOf("REMOVE_FROM_COLLECTION", "Remove from Your Library"))
        assertEquals(CustomKind.LIKE, customKindOf("addToLibrary", "Opslaan in je bibliotheek"))
        assertEquals(CustomKind.LIKE, customKindOf("x", "Toevoegen aan Leuk gevonden"))
        assertEquals(CustomKind.LIKE, customKindOf("SAVE", null))
        assertEquals(CustomKind.LIKE, customKindOf("x", "Add to"))
        assertNull(customKindOf("ADD_TO_PLAYLIST", "Add to playlist"))
        assertNull(customKindOf("x", "Toevoegen aan wachtrij"))
        assertFalse(likedOf("com.spotify.music.ADD_TO_COLLECTION", "Add to Your Library"))
        assertTrue(likedOf("REMOVE_FROM_COLLECTION", "Remove from Your Library"))
        assertFalse(likedOf("addToLibrary", "Opslaan in je bibliotheek"))
        assertTrue(likedOf("x", "Verwijderen uit je bibliotheek"))
        assertTrue(likedOf("x", "Leuk gevonden"))
        assertFalse(likedOf("x", "Toevoegen aan Leuk gevonden"))
        assertEquals(CustomKind.SHUFFLE, customKindOf("com.spotify.music.TOGGLE_SHUFFLE", "Willekeurig afspelen"))
        assertEquals(CustomKind.REPEAT, customKindOf("TOGGLE_REPEAT", "Herhaling"))
        assertEquals(CustomKind.REPEAT, customKindOf("x", "Herhalen"))
    }

    @Test fun heartByRating() {
        assertTrue(likesByRating(RATING_HEART, 128L or 4L))
        assertTrue(likesByRating(RATING_THUMB_UP_DOWN, 128L))
        assertFalse(likesByRating(RATING_HEART, 4L))
        // Stars or percentages are no heart.
        assertFalse(likesByRating(3, 128L))
        assertFalse(likesByRating(0, 128L))
    }
}
