package app.orbittv.feeds

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CatalogTest {
    private fun t(id: Int, pop: Double, title: String = "T$id") = TmdbTitle(id, "movie", title, "2022", "te", "o", listOf(18), pop, 7.0, "/p$id.jpg", "/b$id.jpg")
    private val pkgs = mapOf("Netflix" to "com.netflix.ninja", "Sun Nxt" to "com.suntv.sunnxt")
    private fun objs(a: JSONArray) = (0 until a.length()).map { a.getJSONObject(it) }

    @Test fun `a title is on an app only when the provider lists it and the app is installed`() {
        val d = mapOf("movie:1" to Detail("/l1.png", listOf("k1"), mapOf("IE" to listOf("Netflix", "Sun Nxt"))))
        val withNetflix = objs(Catalog.build(listOf(t(1, 5.0)), d, "IE", pkgs, setOf("com.netflix.ninja"))).single()
        assertEquals(listOf("Netflix"), objs(withNetflix.getJSONArray("apps")).map { it.getString("name") })
        assertEquals("com.netflix.ninja", withNetflix.getJSONArray("apps").getJSONObject(0).getString("pkg"))
        assertEquals(listOf("Netflix", "Sun Nxt"), withNetflix.getJSONArray("providers").let { a -> List(a.length()) { a.getString(it) } })
        val none = objs(Catalog.build(listOf(t(1, 5.0)), d, "IE", pkgs, emptySet())).single()
        assertEquals(0, none.getJSONArray("apps").length())
    }

    @Test fun `a person whose main language is not English sees its logo, else the neutral one, never the English one`() {
        val logos = mapOf("en" to "/en.png", "es" to "/es.png", "" to "/x.png")
        fun logo(display: String, l: Map<String, String>) = objs(Catalog.build(listOf(t(1, 5.0)), mapOf("movie:1" to Detail("/en.png", emptyList(), emptyMap(), logos = l)), "IE", pkgs, emptySet(), display)).single().opt("logo")
        assertEquals("https://image.tmdb.org/t/p/w500/es.png", logo("es", logos))
        assertEquals("https://image.tmdb.org/t/p/w500/x.png", logo("es", logos - "es"))
        assertEquals(JSONObject.NULL, logo("es", mapOf("en" to "/en.png")))   // the localized title shows as text
        assertEquals("https://image.tmdb.org/t/p/w500/en.png", logo("en", logos))
    }
    // TV check: a Kannada-first person lost every logo (details cached before logos by language had none)
    @Test fun `a title in its own script keeps its English logo, with the own-script line for the hero`() {
        val kn = TmdbTitle(5, "movie", "ಕಾಂತಾರ", "2022", "kn", "o", listOf(28), 3.0, 7.1, "/p.jpg", "/b.jpg", "ಕಾಂತಾರ")
        val o = objs(Catalog.build(listOf(kn), mapOf("movie:5" to Detail("/en.png", emptyList(), emptyMap())), "IE", pkgs, emptySet(), "kn")).single()
        assertEquals("https://image.tmdb.org/t/p/w500/en.png", o.getString("logo"))
        assertEquals("ಕಾಂತಾರ", o.getString("localTitle"))
    }
    // review: an English film for a Kannada-first person showed its title as plain text, without its English logo
    @Test fun `an English title shown in English keeps its English logo whatever the main language`() {
        val en = TmdbTitle(7, "movie", "The Bear", "2022", "en", "o", listOf(18), 3.0, 7.1, "/p.jpg", "/b.jpg", "The Bear")
        val italian = TmdbTitle(8, "movie", "The Great Beauty", "2013", "it", "o", listOf(18), 3.0, 7.1, "/p.jpg", "/b.jpg", "La grande bellezza", english = true)
        val d = mapOf("movie:7" to Detail("/en.png", emptyList(), emptyMap()), "movie:8" to Detail("/it-en.png", emptyList(), emptyMap()))
        val a = objs(Catalog.build(listOf(en, italian), d, "IE", pkgs, emptySet(), "kn")).associateBy { it.getInt("id") }
        assertEquals("https://image.tmdb.org/t/p/w500/en.png", a.getValue(7).getString("logo"))
        assertEquals("https://image.tmdb.org/t/p/w500/it-en.png", a.getValue(8).getString("logo"))   // fell back to the English title
    }
    @Test fun `providers are those of the living country`() {
        val d = mapOf("movie:1" to Detail(null, emptyList(), mapOf("IE" to listOf("Sun Nxt"), "DE" to listOf("Netflix"))))
        val de = objs(Catalog.build(listOf(t(1, 5.0)), d, "DE", pkgs, setOf("com.netflix.ninja", "com.suntv.sunnxt"))).single()
        assertEquals(listOf("Netflix"), de.getJSONArray("providers").let { a -> List(a.length()) { a.getString(it) } })
        assertEquals(0, objs(Catalog.build(listOf(t(1, 5.0)), d, "NG", pkgs, setOf("com.netflix.ninja"))).single().getJSONArray("providers").length())
    }

    @Test fun `apps known across countries work anywhere, country extras only at home, and an unknown provider shows its name without an app`() {
        val apps = ProviderApps.parse(File("src/main/assets/providers.json").readText())
        assertEquals("com.netflix.ninja", apps.forCountry("DE")["Netflix"]); assertEquals("com.amazon.firebat", apps.forCountry("IS")["Amazon Prime Video"])
        assertEquals("com.suntv.sunnxt", apps.forCountry("IE")["Sun Nxt"]); assertEquals(null, apps.forCountry("DE")["Sun Nxt"])
        val d = mapOf("movie:1" to Detail(null, emptyList(), mapOf("IS" to listOf("Síminn Bíó", "Netflix"))))
        val o = objs(Catalog.build(listOf(t(1, 5.0)), d, "IS", apps.forCountry("IS"), setOf("com.netflix.ninja"))).single()
        assertEquals(listOf("Síminn Bíó", "Netflix"), o.getJSONArray("providers").let { a -> List(a.length()) { a.getString(it) } })
        assertEquals(listOf("Netflix"), objs(o.getJSONArray("apps")).map { it.getString("name") })
        assertEquals(emptyMap<String, String>(), ProviderApps.NONE.forCountry("DE"))
    }

    @Test fun `a title without details still appears with no logo, trailers or apps`() {
        val o = objs(Catalog.build(listOf(t(2, 1.0)), emptyMap(), "IE", pkgs, setOf("com.netflix.ninja"))).single()
        assertTrue(o.isNull("logo")); assertEquals(0, o.getJSONArray("trailers").length()); assertEquals(0, o.getJSONArray("apps").length())
        assertEquals("https://image.tmdb.org/t/p/w342/p2.jpg", o.getString("poster"))
        assertEquals("https://image.tmdb.org/t/p/w1280/b2.jpg", o.getString("backdrop"))
    }

    @Test fun `titles come out by popularity with duplicates removed`() {
        val a = objs(Catalog.build(listOf(t(1, 1.0), t(2, 9.0), t(3, 4.0), t(2, 9.0)), emptyMap(), "IE", pkgs, emptySet()))
        assertEquals(listOf(2, 3, 1), a.map { it.getInt("id") })
    }

    @Test fun `logo urls are full and nothing leaks the key`() {
        val d = mapOf("movie:1" to Detail("/l1.png", emptyList(), emptyMap()))
        val s = Catalog.build(listOf(t(1, 5.0)), d, "IE", pkgs, emptySet()).toString()
        assertTrue(s.contains("https://image.tmdb.org/t/p/w500/l1.png")); assertFalse(s.contains("api_key"))
    }
    @Test fun `native titles, running time and genre names reach the page`() {
        val kn = TmdbTitle(5, "movie", "Kantara", "2022", "kn", "o", listOf(28, 12, 99999), 3.0, 7.1, "/p.jpg", "/b.jpg", "ಕಾಂತಾರ")
        val latin = TmdbTitle(6, "movie", "Martin", "2024", "kn", "o", listOf(18), 2.0, 6.0, null, null, "Martin")
        val a = Catalog.build(listOf(kn, latin), mapOf("movie:5" to Detail(null, emptyList(), emptyMap(), 147)), "IE", pkgs, emptySet())
        val k = objs(a).first { it.getInt("id") == 5 }; val m = objs(a).first { it.getInt("id") == 6 }
        assertEquals("ಕಾಂತಾರ", k.getString("localTitle")); assertTrue(m.isNull("localTitle"))
        assertEquals(147, k.getInt("runtime")); assertTrue(m.isNull("runtime"))
        assertEquals(listOf("Action", "Adventure"), k.getJSONArray("genreNames").let { g -> List(g.length()) { g.getString(it) } })
    }
}
