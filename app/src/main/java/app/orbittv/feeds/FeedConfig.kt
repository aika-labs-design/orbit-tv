package app.orbittv.feeds

import android.content.res.AssetManager
import org.json.JSONObject

data class CricketLeague(val id: String, val name: String)
data class TennisRule(val allRounds: List<String>, val playersRegex: String)
data class Topic(val id: String, val label: String, val queries: List<String>, val rssUrls: List<String>)
data class Handoff(val pkg: String?, val label: String)
/** A stadium or circuit: where it is, and its country (a cricket match there is "home" for people whose home country it is). */
data class Venue(val lat: Double, val lon: Double, val country: String)
data class MotoRound(val name: String, val circuit: String, val raceUtc: String, val lat: Double, val lon: Double)

/**
 * Sources that are the same for everyone: ESPN cricket leagues scanned for fixtures, tennis rounds always worth showing, venues, the MotoGP
 * calendar. Lives in assets/feeds.json. Who follows what, and the news, come from the profiles' loves.
 */
data class FeedConfig(
    val cricketLeagues: List<CricketLeague>,
    val tennisRounds: List<String>,
    val venues: Map<String, Venue>,
    val motogp: List<MotoRound>,
) {
    companion object {
        fun parse(json: String): FeedConfig {
            val o = JSONObject(json)
            fun strings(key: String, from: JSONObject = o): List<String> { val a = from.optJSONArray(key); return if (a == null) emptyList() else List(a.length()) { a.getString(it) } }
            val leagues = o.getJSONArray("cricketLeagues").let { a -> List(a.length()) { i -> a.getJSONObject(i).let { CricketLeague(it.getString("id"), it.getString("name")) } } }
            val venues = o.getJSONObject("venues").let { v -> v.keys().asSequence().associateWith { k -> v.getJSONObject(k).let { Venue(it.getDouble("lat"), it.getDouble("lon"), it.getString("country")) } } }
            val moto = o.getJSONArray("motogp").let { a -> List(a.length()) { i -> a.getJSONObject(i).let { MotoRound(it.getString("name"), it.getString("circuit"), it.getString("raceUtc"), it.getDouble("lat"), it.getDouble("lon")) } } }
            return FeedConfig(leagues, strings("tennisRounds"), venues, moto)
        }

        fun load(assets: AssetManager): FeedConfig = parse(assets.open("feeds.json").bufferedReader().use { it.readText() })
    }
}

/** Where to watch a sport, by country and then key (league path, sport, or a narrower key such as cricket.ipl). Data in assets/handoffs.json. */
class Handoffs(private val byCountry: Map<String, Map<String, Handoff>>) {
    /** The first key the country has data for; no data means no app button and no label. */
    fun find(country: String, vararg keys: String?): Handoff = byCountry[country]?.let { m -> keys.firstNotNullOfOrNull { k -> k?.let { m[it] } } } ?: NOTHING

    companion object {
        private val NOTHING = Handoff(null, "")
        val NONE = Handoffs(emptyMap())
        fun parse(json: String): Handoffs {
            val o = JSONObject(json)
            return Handoffs(o.keys().asSequence().mapNotNull { cc -> o.optJSONObject(cc)?.let { h -> cc to h.keys().asSequence().associateWith { k -> h.getJSONObject(k).let { Handoff(if (it.isNull("pkg")) null else it.getString("pkg"), it.getString("label")) } } } }.toMap())
        }
    }
}
