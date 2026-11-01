package app.orbittv.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushMemoTest {
    @Test fun `an unchanged body is pushed once per page`() {
        val m = PushMemo()
        assertTrue(m.fresh("catalog", "{\"a\":1}")); assertFalse(m.fresh("catalog", "{\"a\":1}"))
        assertTrue(m.fresh("feeds", "{\"a\":1}"))   // each kind on its own
        assertTrue(m.fresh("catalog", "{\"a\":2}"))
        m.clear(); assertTrue(m.fresh("catalog", "{\"a\":2}"))   // a new page has nothing yet
    }

    @Test fun `a feeds page that differs only in its top-level fetchedMs is not pushed again`() {
        val m = PushMemo()
        assertTrue(m.freshFeeds("{\"live\":[{\"fetchedMs\":5}],\"fetchedMs\":100}"))
        assertFalse(m.freshFeeds("{\"live\":[{\"fetchedMs\":5}],\"fetchedMs\":200}"))
        assertTrue(m.freshFeeds("{\"live\":[{\"fetchedMs\":6}],\"fetchedMs\":200}"))   // a card's own fetch time is content
        m.clear(); assertTrue(m.freshFeeds("{\"live\":[{\"fetchedMs\":6}],\"fetchedMs\":300}"))
    }
}
