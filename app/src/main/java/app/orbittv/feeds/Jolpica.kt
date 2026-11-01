package app.orbittv.feeds

import org.json.JSONObject

data class Race(val name: String, val round: Int, val circuit: String, val locality: String, val country: String, val lat: Double, val lon: Double, val startMs: Long)
data class Standing(val position: Int, val driver: String, val team: String, val points: Double)

/** Jolpica, the Ergast successor: next race with circuit coordinates, and driver standings. */
object Jolpica {
    fun nextRace(json: String): Race? = try {
        val r = JSONObject(json).getJSONObject("MRData").getJSONObject("RaceTable").getJSONArray("Races").optJSONObject(0) ?: return null
        val c = r.getJSONObject("Circuit"); val loc = c.getJSONObject("Location")
        Race(r.getString("raceName"), r.getString("round").toInt(), c.getString("circuitName"), loc.getString("locality"), loc.getString("country"),
            loc.getString("lat").toDouble(), loc.getString("long").toDouble(), EspnScoreboard.isoMs(r.getString("date") + "T" + r.optString("time", "00:00:00Z")))
    } catch (e: Exception) { null }

    fun driverStandings(json: String): List<Standing> = try {
        val lists = JSONObject(json).getJSONObject("MRData").getJSONObject("StandingsTable").getJSONArray("StandingsLists")
        val arr = lists.optJSONObject(0)?.getJSONArray("DriverStandings")
        if (arr == null) emptyList() else (0 until arr.length()).map { i ->
            val s = arr.getJSONObject(i)
            Standing(s.getString("position").toInt(), s.getJSONObject("Driver").getString("familyName"), s.getJSONArray("Constructors").optJSONObject(0)?.optString("name") ?: "", s.getString("points").toDouble())
        }
    } catch (e: Exception) { emptyList() }
}
