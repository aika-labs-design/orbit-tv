package app.orbittv.feeds

import org.json.JSONArray
import org.json.JSONObject

/** A city's first-level region and its other names (München, Bangalore), from assets/place-names.json (tools/data/build_countries.py). */
data class PlaceAka(val region: String, val names: List<String>)

/**
 * Every name a home goes by in the news: the home places with their other names and regions, the country's English name and its
 * names in its own languages, and the alias groups of assets/web/aliases.json. A place not in the list takes the region of the nearest
 * listed city of its country (within 250 km).
 */
class PlaceNames(private val byCountry: Map<String, Map<String, PlaceAka>>, private val aliases: List<List<String>>) {
    private fun fold(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()

    fun forHome(country: Country?, places: List<HomePlace>): List<String> {
        val out = LinkedHashSet<String>()
        val listed = country?.let { byCountry[it.code] } ?: emptyMap()
        places.forEach { p ->
            out += p.name
            val own = listed.entries.firstOrNull { (name, aka) -> fold(name) == fold(p.name) || aka.names.any { fold(it) == fold(p.name) } }
            if (own != null) { out += own.key; out += own.value.names; if (own.value.region.isNotBlank()) out += own.value.region }
            else country?.cities?.map { it to km(p.lat, p.lon, it.lat, it.lon) }?.filter { it.second <= 250.0 }?.minByOrNull { it.second }
                ?.let { (c, _) -> listed[c.name]?.region?.takeIf { it.isNotBlank() }?.let { out += it } }
            aliases.filter { g -> g.any { fold(it) == fold(p.name) } }.forEach { out += it }
        }
        if (country != null) { out += country.name; out += country.names }
        return out.toList()
    }

    private fun km(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val r = Math.PI / 180; val x = (bLon - aLon) * r * Math.cos((aLat + bLat) / 2 * r); val y = (bLat - aLat) * r
        return Math.sqrt(x * x + y * y) * 6371
    }

    companion object {
        val NONE = PlaceNames(emptyMap(), emptyList())
        fun parse(placeNames: String, aliases: String): PlaceNames {
            val o = JSONObject(placeNames)
            val by = o.keys().asSequence().associateWith { cc -> o.getJSONObject(cc).let { m -> m.keys().asSequence().associateWith { n ->
                m.getJSONObject(n).let { e -> PlaceAka(e.optString("region"), e.optJSONArray("names").let { a -> if (a == null) emptyList() else List(a.length()) { a.getString(it) } }) } } } }
            val al = JSONArray(aliases).let { a -> List(a.length()) { i -> a.getJSONArray(i).let { g -> List(g.length()) { g.getString(it) } } } }
            return PlaceNames(by, al)
        }
    }
}
