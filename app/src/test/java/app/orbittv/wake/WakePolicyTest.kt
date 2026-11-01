package app.orbittv.wake

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakePolicyTest {
    @Test fun `takes over after netflix`() { assertTrue(WakePolicy.shouldTakeOver("com.netflix.ninja")) }
    @Test fun `stays out of tv inputs`() { assertFalse(WakePolicy.shouldTakeOver("com.vewd.core.tvapp")) }
    @Test fun `unknown foreground still takes over`() { assertTrue(WakePolicy.shouldTakeOver(null)) }
    @Test fun `comes back even when it was already in front`() { assertTrue(WakePolicy.shouldTakeOver("app.orbittv")) }
    @Test fun `does not replay the intro when already in front`() { assertFalse(WakePolicy.shouldPlayIntro(true)); assertTrue(WakePolicy.shouldPlayIntro(false)) }
    @Test fun `waking never lands in home mode`() {
        assertTrue(WakePolicy.pageAfterWake("home") == "cinema")
        assertTrue(WakePolicy.pageAfterWake("wall") == "wall")
        assertTrue(WakePolicy.pageAfterWake("hero") == "hero")
    }
    @Test fun `with no profiles Orbit starts in setup, otherwise where it was`() {
        assertTrue(WakePolicy.startPage("cinema", hasProfiles = false) == "setup")
        assertTrue(WakePolicy.startPage(null, hasProfiles = false) == "setup")
        assertTrue(WakePolicy.startPage("atlas", hasProfiles = true) == "atlas")
        assertTrue(WakePolicy.startPage(null, hasProfiles = true) == "cinema")
        assertTrue(WakePolicy.startPage("home", hasProfiles = true) == "cinema")
        assertTrue(WakePolicy.startPage("setup", hasProfiles = true) == "setup")
    }
}
