package app.orbittv.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadyQueueTest {
    // TV: a journey's quota answer came back before the page was ready and was dropped, so the page kept "Finding..."
    @Test fun `answers before the page is ready wait for it`() {
        val q = ReadyQueue(); val g = q.generation
        assertNull(q.offer("a", g)); assertNull(q.offer("b", g))
        assertEquals(listOf("a", "b"), q.ready())
        assertEquals("c", q.offer("c", g))   // ready: straight through
    }
    // review: an answer asked for by the previous page that arrives after the switch must never reach the new page
    @Test fun `an answer to a request of an earlier page is dropped, waiting or not`() {
        val q = ReadyQueue(); val old = q.generation
        q.ready(); q.newPage(); val now = q.generation
        assertNull(q.offer("late", old))          // after newPage, before ready
        assertNull(q.offer("mine", now))
        assertEquals(listOf("mine"), q.ready())
        assertNull(q.offer("later", old))         // after ready too
        assertEquals("fresh", q.offer("fresh", now))
    }
    @Test fun `at most fifty answers wait`() {
        val q = ReadyQueue(); repeat(60) { q.offer("x$it", q.generation) }
        assertEquals(50, q.ready().size)
    }
}
