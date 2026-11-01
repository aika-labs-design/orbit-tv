package app.orbittv.feeds

data class CricketScore(val teamA: String, val scoreA: String, val teamB: String, val scoreB: String, val battingA: Boolean, val battingB: Boolean, val link: String)

/** Cricinfo live-scores RSS titles: "<team> [<score>] [*] v <team> [<score>] [*]". The asterisk marks the side batting. */
object CricinfoLive {
    // a score is runs/wickets, or a bare runs figure for an all-out or completed innings, optionally declared ("d"), several innings joined by "&"
    private val SIDE = Regex("""^(.*?)\s*((?:\d+(?:/\d+)?d?)(?:\s*&\s*\d+(?:/\d+)?d?)*)?\s*(\*)?\s*$""")

    fun parseTitle(title: String, link: String): CricketScore? {
        val parts = title.split(" v ", limit = 2)
        if (parts.size != 2) return null
        val a = SIDE.find(parts[0].trim()) ?: return null
        val b = SIDE.find(parts[1].trim()) ?: return null
        val teamA = a.groupValues[1].trim(); val teamB = b.groupValues[1].trim()
        if (teamA.isBlank() || teamB.isBlank()) return null
        return CricketScore(teamA, a.groupValues[2].trim(), teamB, b.groupValues[2].trim(), a.groupValues[3].isNotEmpty(), b.groupValues[3].isNotEmpty(), link)
    }

    /** Development sides ("India A", "India Under-19s", "India U19 Women") are not the followed team. */
    private val DEVELOPMENT = Regex("\\b(A|U19|U-19|Under-19s?|Emerging)\\b", RegexOption.IGNORE_CASE)

    private val MATCH_ID = Regex("/match/(\\d+)\\.html")
    /** The match number in a Cricinfo link; ESPN uses the same number. */
    fun matchId(link: String): String? = MATCH_ID.find(link)?.groupValues?.get(1)

    fun followed(scores: List<CricketScore>, teams: List<String>): List<CricketScore> {
        val res = teams.map { Regex("\\b" + Regex.escape(it) + "\\b", RegexOption.IGNORE_CASE) }
        fun side(name: String) = !DEVELOPMENT.containsMatchIn(name) && res.any { it.containsMatchIn(name) }
        return scores.filter { s -> side(s.teamA) || side(s.teamB) }
    }
}
