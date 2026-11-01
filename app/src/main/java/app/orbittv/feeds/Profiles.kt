package app.orbittv.feeds

import org.json.JSONArray
import org.json.JSONObject

data class Place(val name: String, val lat: Double, val lon: Double, val tz: String)
data class Living(val name: String, val lat: Double, val lon: Double, val tz: String, val country: String)
data class Home(val country: String, val places: List<Place>)
/** One thing a person loves. [weight] is optional (default: pick order); [since] is when it was chosen (ms), the fade baseline; [custom] is free text. */
data class Love(val id: String, val refine: List<String>, val custom: String? = null, val weight: Double? = null, val since: Long? = null)
data class Profile(
    val id: String, val name: String, val home: Home, val languages: List<String>, val living: Living,
    val loves: List<Love>, val created: Long,
)
data class Needs(val languages: List<String>, val homeCountries: List<String>, val livingCountries: List<String>, val loves: List<String>)

/** Profiles stored on the TV (files/profiles.json). The JSON shape is shared with web/profiles.js. */
object Profiles {
    const val MAX = 6
    const val FILE = "profiles.json"

    fun parse(json: String): List<Profile> {
        val arr = try { JSONArray(json) } catch (e: Exception) { return emptyList() }
        val out = ArrayList<Profile>()
        for (i in 0 until arr.length()) {
            val p = try { arr.optJSONObject(i)?.let(::one) } catch (e: Exception) { null }
            if (p != null && out.none { it.id == p.id }) out.add(p)
            if (out.size == MAX) break
        }
        return out
    }

    /** The profiles to store for a save request, or null to refuse it (bad JSON, or entries that would be dropped). `[]` is allowed. */
    fun acceptSave(raw: String): List<Profile>? {
        val count = try { JSONArray(raw).length() } catch (e: Exception) { return null }
        val list = parse(raw)
        return if (list.size < minOf(count, MAX)) null else list
    }

    private fun strings(a: JSONArray?) = if (a == null) emptyList() else List(a.length()) { a.getString(it) }

    private fun one(o: JSONObject): Profile? {
        val id = o.optString("id").trim(); val name = o.optString("name").trim()
        if (id.isEmpty() || name.isEmpty()) return null
        val h = o.optJSONObject("home") ?: return null
        val country = h.optString("country"); val pa = h.optJSONArray("places") ?: return null
        if (country.isEmpty() || pa.length() !in 1..3) return null
        val places = List(pa.length()) { val p = pa.getJSONObject(it); Place(p.getString("name"), p.getDouble("lat"), p.getDouble("lon"), p.getString("tz")) }
        val languages = strings(o.optJSONArray("languages")).filter { it.isNotBlank() }.distinct()
        if (languages.isEmpty()) return null
        val l = o.optJSONObject("living") ?: return null
        val living = Living(l.getString("name"), l.getDouble("lat"), l.getDouble("lon"), l.getString("tz"), l.getString("country"))
        val la = o.optJSONArray("loves")
        val loves = if (la == null) emptyList() else List(la.length()) {
            val x = la.getJSONObject(it)
            val custom = x.optString("custom").takeIf { c -> c.isNotBlank() }
            // a custom love's id is always the one Interests.custom gives its words, so the page never has to hash them the same way
            Love(custom?.let { c -> Interests.custom(c).id } ?: x.getString("id"), strings(x.optJSONArray("refine")), custom, if (x.has("weight") && !x.isNull("weight")) x.getDouble("weight") else null, if (x.has("since") && !x.isNull("since")) x.getLong("since") else null)
        }
        return Profile(id, name, Home(country, places), languages, living, loves, o.optLong("created", 0L))
    }

    fun toJson(list: List<Profile>): String = JSONArray().also { arr ->
        list.forEach { p ->
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("created", p.created)
                .put("home", JSONObject().put("country", p.home.country).put("places", JSONArray().also { a -> p.home.places.forEach { a.put(JSONObject().put("name", it.name).put("lat", it.lat).put("lon", it.lon).put("tz", it.tz)) } }))
                .put("languages", JSONArray(p.languages))
                .put("living", JSONObject().put("name", p.living.name).put("lat", p.living.lat).put("lon", p.living.lon).put("tz", p.living.tz).put("country", p.living.country))
                .put("loves", JSONArray().also { a -> p.loves.forEach { v -> a.put(JSONObject().put("id", v.id).put("refine", JSONArray(v.refine)).also { j -> v.custom?.let { j.put("custom", it) }; v.weight?.let { j.put("weight", it) }; v.since?.let { j.put("since", it) } }) } }))
        }
    }.toString()

    /** What the feeds must cover for everyone: each list in first-seen order, no repeats. */
    fun needs(list: List<Profile>) = Needs(
        languages = list.flatMap { it.languages }.distinct(),
        homeCountries = list.map { it.home.country }.distinct(),
        livingCountries = list.map { it.living.country }.distinct(),
        loves = list.flatMap { p -> p.loves.map { it.id } }.distinct(),
    )
}
