package app.orbittv.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RadioWatchdogTest {
    @Test fun `no answer in ten seconds fails exactly once`() {
        val w = RadioWatchdog(); w.onPlayRequested(0L)
        assertNull(w.check(9_999L)); assertEquals("failed", w.check(10_001L)); assertNull(w.check(20_000L))
    }
    @Test fun `prepared in time never fails`() {
        val w = RadioWatchdog(); w.onPlayRequested(0L); w.onPrepared(); assertNull(w.check(20_000L))
    }
    @Test fun `an error fails at the next check`() {
        val w = RadioWatchdog(); w.onPlayRequested(0L); w.onPrepared(); w.onError(); assertEquals("failed", w.check(1_000L)); assertNull(w.check(2_000L))
    }
    @Test fun `nothing requested never fails`() { assertNull(RadioWatchdog().check(60_000L)) }
    @Test fun `a stall after the stream started fails after ten seconds`() {
        val w = RadioWatchdog(); w.onPlayRequested(0L); w.onPrepared(); w.onStall(5_000L)
        assertNull(w.check(14_000L)); assertEquals("failed", w.check(15_001L)); assertNull(w.check(30_000L))
    }
    @Test fun `buffering that recovers does not fail`() {
        val w = RadioWatchdog(); w.onPlayRequested(0L); w.onPrepared(); w.onStall(5_000L); w.onRecovered(); assertNull(w.check(60_000L))
    }
}
