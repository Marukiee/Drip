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
        assertNull(likedStatedBy("TOGGLE_FAVORITE", "Favorite"))
    }

    @Test fun heartFollowsPressWhenPlayerSaysNothingNew() {
        // Spotify-like: the action reads "add" in both states and does not change after a press.
        assertTrue(heartOf(stated = false, pressed = true, pressedJustNow = false, actionUnchanged = true))
        // The player swapped its action after the press: it now speaks for itself.
        assertFalse(heartOf(stated = false, pressed = true, pressedJustNow = false, actionUnchanged = false))
        // Right after the press, before the player answers.
        assertTrue(heartOf(stated = false, pressed = true, pressedJustNow = true, actionUnchanged = false))
        // Never pressed here.
        assertTrue(heartOf(stated = true, pressed = null, pressedJustNow = false, actionUnchanged = true))
        assertFalse(heartOf(stated = null, pressed = null, pressedJustNow = false, actionUnchanged = true))
    }

    @Test fun shuffleAndRepeatFromIcons() {
        assertEquals(true, shuffleOfMedia3Icon(0xe043))
        assertEquals(false, shuffleOfMedia3Icon(0xfe044))
        assertEquals(RepeatMode.ONE, repeatOfMedia3Icon(0xe041))
        assertEquals(RepeatMode.OFF, repeatOfMedia3Icon(0xfe040))
        assertEquals(true, shuffleOfIconName("ic_shuffle_on"))
        assertEquals(false, shuffleOfIconName("shuffleOff"))
        assertNull(shuffleOfIconName("ic_shuffle"))
        assertEquals(RepeatMode.ONE, repeatOfIconName("ic_repeat_one"))
        assertNull(repeatOfIconName("ic_repeat"))
        assertNull(shuffleStatedBy("SHUFFLE", "Shuffle"))
        // A bare "Shuffle" pressed on: on, as long as the player keeps that same action.
        assertEquals(true, followPress(null, true, pressedJustNow = false, actionUnchanged = true))
        assertEquals(false, followPress(false, true, pressedJustNow = false, actionUnchanged = false))
    }

    @Test fun likeStateFromIcons() {
        assertEquals(true, likedOfMedia3Icon(0xfe87d))
        assertEquals(false, likedOfMedia3Icon(0xe87d))
        assertEquals(true, likedOfMedia3Icon(0xfe8dc))
        assertNull(likedOfMedia3Icon(0xe145))
        assertEquals(false, likedOfIconName("ic_favorite_border"))
        assertEquals(false, likedOfIconName("ic_heart_outline_24"))
        assertEquals(false, likedOfIconName("heartUnfilled"))
        assertEquals(true, likedOfIconName("ic_heart_filled"))
        assertEquals(true, likedOfIconName("icon_heart_active"))
        assertNull(likedOfIconName("ic_favorite"))
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
