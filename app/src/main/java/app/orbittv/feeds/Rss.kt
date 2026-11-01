package app.orbittv.feeds

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import javax.xml.parsers.SAXParserFactory

data class RssItem(val title: String, val link: String, val publishedMs: Long, val source: String?, val description: String?)

/** Minimal RSS 2.0 reader (SAX, available on the JVM and on Android alike). Returns what it could parse; junk yields nothing. */
object Rss {
    private val DATE_FORMATS = listOf("EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss zzz", "EEE, dd MMM yyyy HH:mm Z")

    fun parse(xml: String): List<RssItem> {
        val out = ArrayList<RssItem>()
        if (xml.isBlank()) return out
        val handler = object : DefaultHandler() {
            var inItem = false; var field: String? = null; val text = StringBuilder()
            var title = ""; var link = ""; var date = ""; var source: String? = null; var description: String? = null
            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes?) {
                if (qName == "item") { inItem = true; title = ""; link = ""; date = ""; source = null; description = null }
                else if (inItem) { field = qName; text.setLength(0) }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) { if (field != null) text.append(ch, start, length) }
            override fun endElement(uri: String?, localName: String?, qName: String) {
                if (qName == "item") { inItem = false; if (title.isNotBlank()) out.add(RssItem(title, link, parseDate(date), source, description)) }
                else if (inItem && qName == field) {
                    val v = decodeEntities(text.toString().trim())
                    when (qName) { "title" -> title = v.replace(Regex("\\s+"), " "); "link" -> link = v; "pubDate" -> date = v; "source" -> source = v.ifBlank { null }; "description" -> description = v.ifBlank { null } }
                    field = null
                }
            }
        }
        try {
            val parser = SAXParserFactory.newInstance().apply { isNamespaceAware = false }.newSAXParser()
            parser.parse(InputSource(StringReader(xml)), handler)
        } catch (e: Exception) { /* keep what was parsed */ }
        return out
    }

    fun parseDate(s: String): Long {
        if (s.isBlank()) return 0L
        for (f in DATE_FORMATS) try { return SimpleDateFormat(f, Locale.US).parse(s)!!.time } catch (e: Exception) { }
        return 0L
    }

    /** Second-level HTML entities left inside text after XML decoding (Google News and some publishers double-encode). */
    fun decodeEntities(s: String): String {
        if (!s.contains('&')) return s
        var t = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'").replace("&nbsp;", " ")
        t = Regex("&#(\\d+);").replace(t) { m -> m.groupValues[1].toInt().toChar().toString() }
        t = Regex("&#x([0-9a-fA-F]+);").replace(t) { m -> m.groupValues[1].toInt(16).toChar().toString() }
        return t
    }
}
