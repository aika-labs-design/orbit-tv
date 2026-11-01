package app.orbittv.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilesTest {
    private fun profile(id: String, langs: String = "[\"en\"]", home: String = "IE", living: String = "GB", loves: String = "[]") =
        """{"id":"$id","name":"N$id","created":1,"home":{"country":"$home","places":[{"name":"A","lat":1.0,"lon":2.0,"tz":"Europe/Berlin"}]},
           "languages":$langs,"living":{"name":"L","lat":3.0,"lon":4.0,"tz":"Europe/London","country":"$living"},"loves":$loves}"""

    @Test fun `parse caps at six`() {
        val json = "[" + (1..9).joinToString(",") { profile("p$it") } + "]"
        assertEquals(listOf("p1", "p2", "p3", "p4", "p5", "p6"), Profiles.parse(json).map { it.id })
    }

    @Test fun `parse drops a profile with no languages and keeps the others`() {
        val list = Profiles.parse("[" + profile("a") + "," + profile("b", langs = "[]") + "," + profile("c") + "]")
        assertEquals(listOf("a", "c"), list.map { it.id })
    }

    @Test fun `parse drops entries with no places, too many places or garbage and survives bad json`() {
        val noPlaces = profile("x").replace(Regex("\"places\":\\[.*?\\]"), "\"places\":[]")
        assertTrue(Profiles.parse("[" + noPlaces + ",42,{\"id\":\"q\"}]").isEmpty())
        assertTrue(Profiles.parse("not json").isEmpty())
        assertTrue(Profiles.parse("").isEmpty())
    }

    @Test fun `parse keeps loves with refine custom and weight`() {
        val p = Profiles.parse("[" + profile("a", loves = """[{"id":"cricket","refine":["India","IPL"],"weight":2.5},{"id":"x","refine":[],"custom":"chess"}]""") + "]").single()
        assertEquals(listOf("India", "IPL"), p.loves[0].refine)
        assertEquals(2.5, p.loves[0].weight!!, 0.0)
        assertEquals("chess", p.loves[1].custom)
        assertNull(p.loves[1].weight)
    }

    @Test fun `a custom love takes the id native Interests gives its words, whatever id the page sent`() {
        val loves = """[{"id":"custom:5f3a","refine":[],"custom":"Ìlù dùndún"},{"id":"custom:chess-boxing","refine":[],"custom":"Chess boxing"},{"id":"football","refine":["arsenal"]}]"""
        val p = Profiles.parse("[" + profile("a", loves = loves) + "]").single()
        assertEquals(listOf(Interests.custom("Ìlù dùndún").id, "custom:chess-boxing", "football"), p.loves.map { it.id })
        assertEquals("Ìlù dùndún", p.loves[0].custom)
    }

    @Test fun `a profile may lead with a 639-3 language`() {
        assertEquals(listOf("pcm", "en"), Profiles.parse("[" + profile("a", langs = "[\"pcm\",\"en\"]") + "]").single().languages)
    }

    @Test fun `needs unions languages and countries across profiles`() {
        val list = Profiles.parse("[" + profile("a", langs = "[\"kn\",\"en\"]", home = "IN", living = "IE", loves = """[{"id":"f1","refine":[]}]""") + "," +
            profile("b", langs = "[\"te\",\"en\"]", home = "IN", living = "GB", loves = """[{"id":"f1","refine":[]},{"id":"tennis","refine":[]}]""") + "]")
        val n = Profiles.needs(list)
        assertEquals(listOf("kn", "en", "te"), n.languages)
        assertEquals(listOf("IN"), n.homeCountries)
        assertEquals(listOf("IE", "GB"), n.livingCountries)
        assertEquals(listOf("f1", "tennis"), n.loves)
    }

    @Test fun `a bundled seed parses into its profiles`() {
        val list = Profiles.parse(fixture("profiles-seed.json"))
        assertEquals(listOf("p-ann", "p-bo"), list.map { it.id })
        assertTrue(list.all { it.living.name == "Berlin" && it.living.country == "DE" })
        assertEquals(listOf("kn", "en"), list[0].languages)
        assertEquals(2, list[0].home.places.size)
    }

    @Test fun `no seed is no profiles`() {
        assertTrue(Profiles.parse("").isEmpty())
        assertTrue(Profiles.parse("   ").isEmpty())
    }

    @Test fun `a love keeps its since through parse and toJson`() {
        val list = Profiles.parse("[" + profile("a", loves = """[{"id":"cricket","refine":[],"since":1761955200000},{"id":"x","refine":[],"custom":"chess"}]""") + "]")
        assertEquals(1761955200000L, list[0].loves[0].since)
        assertNull(list[0].loves[1].since)
        assertEquals(list, Profiles.parse(Profiles.toJson(list)))
    }

    @Test fun `toJson round trips`() {
        val list = Profiles.parse(fixture("profiles-seed.json"))
        assertEquals(list, Profiles.parse(Profiles.toJson(list)))
    }

    @Test fun `acceptSave allows a full valid list and an intentional empty list`() {
        assertEquals(2, Profiles.acceptSave("[" + profile("a") + "," + profile("b") + "]")!!.size)
        assertEquals(0, Profiles.acceptSave("[]")!!.size)
    }

    @Test fun `acceptSave refuses garbage, all-invalid and partially invalid payloads`() {
        assertNull(Profiles.acceptSave("not json"))
        assertNull(Profiles.acceptSave(""))
        assertNull(Profiles.acceptSave("[{\"id\":\"q\"}]"))
        assertNull(Profiles.acceptSave("[" + profile("a") + "," + profile("b", langs = "[]") + "]"))
    }
}
