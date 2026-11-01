package app.orbittv.catalog

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogFilterTest {
    @Test fun `hides platform plumbing that is not an app for people`() {
        assertTrue(CatalogFilter.isHidden("com.vewd.core.opapp"))
        assertTrue(CatalogFilter.isHidden("com.vewd.core.fvpplayer"))
        assertTrue(CatalogFilter.isHidden("com.amazon.servicemenu"))
    }
    @Test fun `keeps real apps and live tv`() {
        assertFalse(CatalogFilter.isHidden("com.netflix.ninja"))
        assertFalse(CatalogFilter.isHidden("com.vewd.core.tvapp"))
    }
}
