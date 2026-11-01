package app.orbittv.catalog

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable

data class TvApp(
    val packageName: String,
    val component: ComponentName,
    val label: String,
    val banner: Bitmap?,
    val icon: Bitmap?,
)

/** The TV apps installed on this device: one entry per package, own package excluded. */
class AppCatalog(private val pm: PackageManager, private val ownPackage: String) {

    fun load(): List<TvApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val seen = HashSet<String>()
        return pm.queryIntentActivities(intent, 0).mapNotNull { ri ->
            val pkg = ri.activityInfo.packageName
            if (pkg == ownPackage || CatalogFilter.isHidden(pkg) || !seen.add(pkg)) return@mapNotNull null
            TvApp(
                packageName = pkg,
                component = ComponentName(pkg, ri.activityInfo.name),
                label = ri.loadLabel(pm).toString(),
                banner = runCatching { pm.getApplicationBanner(pkg) }.getOrNull()?.let(::toBitmap),
                icon = runCatching { pm.getApplicationIcon(pkg) }.getOrNull()?.let(::toBitmap),
            )
        }
    }

    fun launchIntent(app: TvApp): Intent =
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    /** Renders the drawable at most MAX_EDGE pixels wide or tall; large banners would otherwise cost tens of MB each. */
    private fun toBitmap(d: Drawable): Bitmap? {
        val w0 = d.intrinsicWidth.takeIf { it > 0 } ?: return null
        val h0 = d.intrinsicHeight.takeIf { it > 0 } ?: return null
        val scale = minOf(1f, MAX_EDGE.toFloat() / maxOf(w0, h0))
        val w = maxOf(1, (w0 * scale).toInt()); val h = maxOf(1, (h0 * scale).toInt())
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, w, h); d.draw(Canvas(bmp))
        return bmp
    }

    companion object { const val MAX_EDGE = 768 }
}
