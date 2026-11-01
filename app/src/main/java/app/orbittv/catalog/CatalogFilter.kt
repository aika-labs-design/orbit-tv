package app.orbittv.catalog

/** Launcher entries that are platform plumbing rather than apps a person opens. */
object CatalogFilter {
    val hidden: Set<String> = setOf(
        "com.vewd.core.opapp",
        "com.vewd.core.fvpplayer",
        "com.amazon.servicemenu",
        "com.amazon.tv.settings.v2",
        "com.amazon.firehomestarter",
    )

    fun isHidden(packageName: String): Boolean = packageName in hidden
}
