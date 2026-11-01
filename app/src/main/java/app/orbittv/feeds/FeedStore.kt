package app.orbittv.feeds

import android.content.Context
import java.io.File

/** Raw feed bodies and the compiled page document, on disk, so cards survive restarts and bad fetches. */
class FeedStore(private val dir: File) {
    init { dir.mkdirs() }
    fun save(name: String, body: String) { val tmp = File(dir, "$name.tmp"); tmp.writeText(body); tmp.renameTo(File(dir, name)) }
    fun load(name: String): String? = File(dir, name).takeIf { it.isFile }?.readText()
    fun savedAt(name: String): Long? = File(dir, name).takeIf { it.isFile }?.lastModified()?.takeIf { it > 0 }
    fun pageJson(): String? = load(PAGE)
    fun names(): List<String> = dir.list()?.toList() ?: emptyList()
    fun delete(name: String): Boolean = File(dir, name).delete()

    companion object {
        const val ACTION_UPDATED = "app.orbittv.FEEDS_UPDATED"
        const val PAGE = "feeds.json"
        const val CATALOG = "catalog.json"
        fun of(context: Context) = FeedStore(File(context.filesDir, "feeds"))
        fun pageJson(context: Context): String? = of(context).pageJson()
    }
}
