package app.orbittv.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeGateTest {
    @Test fun `only the current page token opens side effects`() {
        val g = BridgeGate()
        val t = g.renew()
        assertTrue(g.allows(t)); assertFalse(g.allows(null)); assertFalse(g.allows("")); assertFalse(g.allows("guess"))
        val t2 = g.renew(); assertFalse(g.allows(t)); assertTrue(g.allows(t2))
        assertTrue(t2.length >= 24)
    }
}
