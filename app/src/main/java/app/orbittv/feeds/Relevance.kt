package app.orbittv.feeds

import java.text.Normalizer

/**
 * The gate every show, news item and video passes before Orbit shows it for a love. Empty is better than wrong:
 * an item is kept only if it names the love ([keep]) and does not read as clickbait ([clickbait]).
 */
object Relevance {
    private val MARKS = Regex("\\p{M}+")
    private fun fold(s: String) = MARKS.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase().replace('’', '\'')

    private val cache = HashMap<String, Regex>()
    private fun word(term: String): Regex = synchronized(cache) {
        cache.getOrPut(term) { Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(term) + "(?:e?s)?(?![\\p{L}\\p{N}])") }
    }

    /** True if [text] contains one of [terms] as a whole word (a plain plural "s" or "es" is allowed), ignoring case and accents. */
    fun keep(text: String, terms: List<String>): Boolean {
        if (text.isBlank()) return false
        val t = fold(text)
        return terms.any { raw -> val term = fold(raw).trim().replace(Regex("\\s+"), " "); term.isNotEmpty() && word(term).containsMatchIn(t) }
    }

    private val STOCK = Regex("^(adobe stock|shutterstock|getty images|alamy|istock(photo)?|dreamstime|123rf)\\b", RegexOption.IGNORE_CASE)
    private val LISTING = Regex("\\bstock (photo|image|vector|illustration|footage)s?\\b|\\broyalty[- ]free\\b|^amazon\\.com:|\\| ebay\\b|\\bfor sale\\b|\\bfree shipping\\b|\\bshop now\\b|\\bbuy now\\b", RegexOption.IGNORE_CASE)
    private val SHOPS = Regex("^(amazon(\\.[a-z.]+)?|ebay|etsy|walmart(\\.com)?|aliexpress|temu)$", RegexOption.IGNORE_CASE)

    /** True for a stock-photo library or an item that is clearly a product listing: not news, whatever it names. */
    fun listing(title: String, source: String?): Boolean {
        val src = source?.trim() ?: ""
        return STOCK.containsMatchIn(src) || SHOPS.matches(src) || LISTING.containsMatchIn(title)
    }

    /**
     * Sensational words, whole words only ("Scampi", "exposure", "Challengers" and cargo "shorts" are fine). A "challenge" is only
     * the viral kind (#challenge, "24 hour challenge", "challenge accepted"); "shorts" only means a YouTube Shorts marker; "reaction" only the reaction-video sense ("reacts to", "reaction video").
     */
    private val WORDS = Regex(
        "\\bscams?\\b|\\bfakes?\\b|\\bpoison(?:s|ed|ing)?\\b|\\bworst\\b|\\bshocking\\b|\\bfights?\\b|\\bexposed\\b|\\bpranks?\\b|\\bpranked\\b|gone wrong|" +
            "\\breacts to\\b|\\breacting to\\b|\\breaction video\\b|\\bmy reaction\\b|\\bfirst time (?:hearing|watching|trying|listening)\\b.*\\breaction\\b|you won'?t believe|#\\w*challenge|\\bchallenge accepted\\b|\\b\\d+[ -]hours?[ -]challenge\\b|#shorts?\\b|[(\\[]shorts?[)\\]]|^\\W*shorts?\\W*$",
        RegexOption.IGNORE_CASE,
    )

    /**
     * True if [title] reads as clickbait. A flagged word that is itself part of the love's own [loveTerms] ("fight" for boxing,
     * "challengers" for a team called the Challengers) is not held against it; every other flag still counts.
     */
    fun clickbait(title: String, loveTerms: List<String> = emptyList()): Boolean {
        val t = title.replace('\u2019', '\'')
        if (t.isBlank()) return false
        val own = loveTerms.map { fold(it) }
        if (WORDS.findAll(t).any { m -> val w = fold(m.value).removeSuffix("s"); own.none { it.contains(w) } }) return true
        if (t.count { it == '!' || it == '?' } > 2) return true
        val letters = t.filter { it.isLetter() }
        return letters.length >= 8 && letters.none { it.lowercaseChar() == it && it.uppercaseChar() != it }
    }
}
