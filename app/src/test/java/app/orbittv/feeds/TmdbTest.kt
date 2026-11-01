package app.orbittv.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbTest {
    @Test fun `discover parses films with their kind and image paths`() {
        val t = Tmdb.parseDiscover(fixture("tmdb_discover_kn.json"), "movie")
        assertEquals(20, t.size)
        assertTrue(t.all { it.kind == "movie" && it.lang == "kn" && it.title.isNotBlank() })
        assertTrue(t.first().poster!!.startsWith("/"))
    }

    @Test fun `discover parses series names and first air year`() {
        val t = Tmdb.parseDiscover(fixture("tmdb_discover_tv_te.json"), "tv")
        assertTrue(t.isNotEmpty())
        assertTrue(t.all { it.kind == "tv" && it.lang == "te" && it.title.isNotBlank() && (it.year.isEmpty() || it.year.length == 4) })
    }

    @Test fun `logo prefers english, then the title language, then none`() {
        assertTrue(Tmdb.parseLogo(fixture("tmdb_images_579974.json"), "te")!!.endsWith(".png") || Tmdb.parseLogo(fixture("tmdb_images_579974.json"), "te")!!.endsWith(".svg"))
        val json = """{"logos":[{"file_path":"/hi.png","iso_639_1":"hi"},{"file_path":"/te.png","iso_639_1":"te"},{"file_path":"/en.png","iso_639_1":"en"}]}"""
        assertEquals("/en.png", Tmdb.parseLogo(json, "te"))
        assertEquals("/te.png", Tmdb.parseLogo("""{"logos":[{"file_path":"/hi.png","iso_639_1":"hi"},{"file_path":"/te.png","iso_639_1":"te"}]}""", "te"))
        assertEquals("/x.png", Tmdb.parseLogo("""{"logos":[{"file_path":"/x.png","iso_639_1":null}]}""", "te"))
        assertNull(Tmdb.parseLogo(fixture("tmdb_images_nologo.json"), "te"))
        assertNull(Tmdb.parseLogo("""{"logos":[{"file_path":"/hi.png","iso_639_1":"hi"}]}""", "te"))
    }

    @Test fun `trailers keep every youtube trailer first, then teasers, in order`() {
        val keys = Tmdb.parseTrailers(fixture("tmdb_videos_579974.json"))
        assertTrue(keys.size >= 3)
        val json = """{"results":[{"key":"f","site":"YouTube","type":"Featurette"},{"key":"t1","site":"YouTube","type":"Teaser"},{"key":"a","site":"YouTube","type":"Trailer"},{"key":"v","site":"Vimeo","type":"Trailer"},{"key":"b","site":"YouTube","type":"Trailer"}]}"""
        assertEquals(listOf("a", "b", "t1"), Tmdb.parseTrailers(json))
        assertEquals(emptyList<String>(), Tmdb.parseTrailers("""{"results":[]}"""))
    }

    @Test fun `providers for the asked country merge subscription, free and ad tiers`() {
        assertEquals(listOf("Netflix"), Tmdb.parseProviders(fixture("tmdb_watch_providers_579974.json"), "IE"))
        val json = """{"results":{"IE":{"link":"x","flatrate":[{"provider_name":"Netflix"}],"ads":[{"provider_name":"Netflix"},{"provider_name":"ZEE5"}],"rent":[{"provider_name":"Apple TV"}]},"DE":{"flatrate":[{"provider_name":"Joyn"}]}}}"""
        assertEquals(listOf("Netflix", "ZEE5"), Tmdb.parseProviders(json, "IE"))
        assertEquals(listOf("Joyn"), Tmdb.parseProviders(json, "DE"))
        assertEquals(emptyList<String>(), Tmdb.parseProviders(json, "NG"))
        assertEquals(emptyList<String>(), Tmdb.parseProviders("""{"results":{}}""", "DE"))
    }

    @Test fun `urls and images`() {
        assertEquals("https://image.tmdb.org/t/p/w780/x.jpg", Tmdb.image("/x.jpg", "w780"))
        val d = Tmdb.discoverUrl("", "tv", "te", 2)
        assertTrue(d, d.startsWith("https://api.themoviedb.org/3/discover/tv?") && d.contains("with_original_language=te") && d.contains("page=2") && d.contains("vote_count.gte=3"))
        assertTrue(Tmdb.discoverUrl("k", "movie", "kn", 1).contains("vote_count.gte=20"))
    }

    // QA: a Spanish-first person got English titles and overviews
    @Test fun `discover asks for titles in the person's main language, English needs no parameter`() {
        assertTrue(Tmdb.discoverUrl("k", "movie", "es", 1, display = "es").endsWith("&language=es"))
        assertFalse(Tmdb.discoverUrl("k", "movie", "es", 1, display = "en").contains("&language="))
        assertFalse(Tmdb.discoverUrl("k", "movie", "es", 1).contains("&language="))
        // TV check: Spanish titles had no trailer, TMDB lists videos in English only unless asked
        assertTrue(Tmdb.detailAllUrl("k", "movie", 9, "es", listOf("es", "kn")).endsWith("include_image_language=en,null,es,kn&include_video_language=en,null,es,kn"))
    }
    @Test fun `a detail keeps every logo by language, the neutral one under an empty key`() {
        val d = Tmdb.parseDetail("""{"images":{"logos":[{"file_path":"/en.png","iso_639_1":"en"},{"file_path":"/es.png","iso_639_1":"es"},{"file_path":"/es2.png","iso_639_1":"es"},{"file_path":"/x.png","iso_639_1":null}]}}""", "es", emptyList())!!
        assertEquals("/en.png", d.logo); assertEquals(mapOf("en" to "/en.png", "es" to "/es.png", "" to "/x.png"), d.logos)
    }
    @Test fun `junk gives nothing`() {
        assertEquals(emptyList<TmdbTitle>(), Tmdb.parseDiscover("<html>", "movie"))
        assertNull(Tmdb.parseLogo("{}", "kn")); assertEquals(emptyList<String>(), Tmdb.parseTrailers("oops")); assertFalse(Tmdb.parseProviders("oops", "DE").isNotEmpty())
    }
    @Test fun `one detail call gives logo, trailers, providers and running time`() {
        val d = Tmdb.parseDetail(fixture("tmdb_detail_579974.json"), "te", listOf("DE", "NG", "XK"))!!
        assertTrue(d.logo!!.startsWith("/")); assertTrue(d.trailers.isNotEmpty()); assertTrue(d.backdrop!!.startsWith("/")); assertEquals(187, d.runtime)
        // providers only for the living countries asked; a country with none is recorded as checked and empty
        assertEquals(mapOf("DE" to listOf("Netflix", "Netflix Standard with Ads"), "NG" to listOf("Netflix", "Zee5"), "XK" to emptyList<String>()), d.providers)
        assertEquals("https://api.themoviedb.org/3/tv/9?api_key=k&append_to_response=images,videos,watch/providers&include_image_language=en,null,te&include_video_language=en,null,te", Tmdb.detailAllUrl("k", "tv", 9, "te"))
        val empty = Tmdb.parseDetail("{}", "te", listOf("DE"))!!; assertNull(empty.logo); assertNull(empty.runtime); assertEquals(emptyList<String>(), empty.trailers); assertNull(Tmdb.parseDetail("<html>", "te", listOf("DE")))
    }
    @Test fun `discover keeps the original title`() {
        assertEquals("ಕಾಂತಾರ", Tmdb.parseDiscover(fixture("tmdb_discover_kn.json"), "movie").first { it.id == 858485 }.originalTitle)
    }
    @Test fun `the clean backdrop is the best rated one without text`() {
        val json = """{"images":{"backdrops":[{"file_path":"/en.jpg","iso_639_1":"en","vote_average":9},{"file_path":"/a.jpg","iso_639_1":null,"vote_average":5.1},{"file_path":"/b.jpg","iso_639_1":null,"vote_average":5.6}],"logos":[]}}"""
        assertEquals("/b.jpg", Tmdb.parseDetail(json, "kn", emptyList())!!.backdrop)
        assertNull(Tmdb.parseDetail("""{"images":{"backdrops":[{"file_path":"/en.jpg","iso_639_1":"en","vote_average":9}]}}""", "kn", emptyList())!!.backdrop)
    }
}
