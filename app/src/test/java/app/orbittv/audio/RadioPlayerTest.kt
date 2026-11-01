package app.orbittv.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioPlayerTest {
    @Test fun `only web streams are played`() {
        assertTrue(RadioPlayer.playable("https://stream.example.org/radio.mp3"))
        assertTrue(RadioPlayer.playable("http://stream.example.org:8000/live"))
        assertTrue(RadioPlayer.playable("HTTPS://STREAM.EXAMPLE.ORG/a.aac"))
        for (bad in listOf("", "file:///data/data/app.orbittv/files/profiles.json", "content://x/y", "rtsp://cam/1", "javascript:alert(1)", "https://", "httpx://a.b", " https://a.b"))
            assertFalse(bad, RadioPlayer.playable(bad))
    }
    // TV: Home Mode said TUNING while the station played (the player's buffering-end never came)
    @Test fun `sound coming out after tuning or a stall is reported as playing, once`() {
        assertTrue(RadioPlayer.nowPlaying("connecting", isPlaying = true))
        assertFalse(RadioPlayer.nowPlaying("playing", isPlaying = true))
        assertFalse(RadioPlayer.nowPlaying("connecting", isPlaying = false))
    }
}
