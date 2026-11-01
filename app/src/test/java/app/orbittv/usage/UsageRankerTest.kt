package app.orbittv.usage

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class UsageRankerTest {
    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    @Test fun `same hour usage wins`() {
        val now = 1793392200000L // 2026-10-30 20:30 UTC
        val ev = listOf(UsageEvent("a", now - 24 * 3600_000L), UsageEvent("b", now - 12 * 3600_000L), UsageEvent("b", now - 36 * 3600_000L))
        assertEquals(listOf("a", "b", "c"), UsageRanker.order(listOf("c", "b", "a"), ev, now, utc))
    }
    @Test fun `order keeps every app once`() {
        val r = UsageRanker.order(listOf("x", "y", "z"), emptyList(), 0L, utc)
        assertEquals(setOf("x", "y", "z"), r.toSet()); assertEquals(3, r.size)
    }
    @Test fun `lastUsed is the newest event`() {
        assertEquals("q", UsageRanker.lastUsed(listOf(UsageEvent("p", 1), UsageEvent("q", 2))))
    }
}
