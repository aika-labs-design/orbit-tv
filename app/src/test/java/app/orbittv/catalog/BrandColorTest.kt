package app.orbittv.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class BrandColorTest {
    @Test fun `dominant ignores black and white`() {
        val px = IntArray(100) { if (it < 60) 0xFF000000.toInt() else 0xFFE01010.toInt() }
        assertEquals(0xFFE01010.toInt() and 0xFFF0F0F0.toInt(), BrandColor.dominant(px) and 0xFFF0F0F0.toInt())
    }
    @Test fun `saturated minority beats grey majority`() {
        val px = IntArray(100) { if (it < 60) 0xFF808080.toInt() else 0xFFE01010.toInt() }
        assertEquals(0xFFE01010.toInt() and 0xFFF0F0F0.toInt(), BrandColor.dominant(px) and 0xFFF0F0F0.toInt())
    }
    @Test fun `dominant on empty returns default accent`() {
        assertEquals(0xFF3DF2FF.toInt(), BrandColor.dominant(IntArray(0)))
    }
}
