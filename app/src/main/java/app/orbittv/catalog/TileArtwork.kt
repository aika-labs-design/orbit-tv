package app.orbittv.catalog

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import java.io.ByteArrayOutputStream

/**
 * Renders a tile face as a 768x432 PNG. Lighting is baked here because the scene materials are unlit:
 * a brand gradient slab, a soft highlight, the app's mark with a shadow, an inner hairline and a vignette.
 * A banner is used edge to edge only when it is sharp enough (at least 640 px wide).
 */
object TileArtwork {
    const val W = 768; const val H = 432
    private const val SHARP_BANNER_MIN_WIDTH = 640
    private const val GOOD_ICON_MIN_HEIGHT = 144

    fun brandOf(app: TvApp): Int {
        val src = app.icon ?: app.banner ?: return BrandColor.DEFAULT
        val sample = Bitmap.createScaledBitmap(src, 32, 32, true)
        val px = IntArray(32 * 32); sample.getPixels(px, 0, 32, 0, 0, 32, 32)
        return BrandColor.dominant(px)
    }

    fun facePng(app: TvApp, brand: Int): ByteArray {
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val banner = app.banner
        val icon = app.icon
        when {
            banner != null && banner.width >= SHARP_BANNER_MIN_WIDTH -> drawFill(c, banner, paint)
            icon != null && icon.height >= GOOD_ICON_MIN_HEIGHT -> { drawSlab(c, brand); drawMark(c, icon, H * 0.56f, paint) }
            banner != null -> { drawSlab(c, brand); drawCard(c, banner, paint) }
            icon != null -> { drawSlab(c, brand); drawMark(c, icon, H * 0.5f, paint) }
            else -> drawSlab(c, brand)
        }
        drawVignette(c)
        drawHairline(c)
        return ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun drawFill(c: Canvas, bmp: Bitmap, paint: Paint) {
        val scale = maxOf(W.toFloat() / bmp.width, H.toFloat() / bmp.height)
        val dw = bmp.width * scale; val dh = bmp.height * scale
        c.drawBitmap(bmp, Rect(0, 0, bmp.width, bmp.height), RectF((W - dw) / 2, (H - dh) / 2, (W + dw) / 2, (H + dh) / 2), paint)
    }

    private fun drawSlab(c: Canvas, brand: Int) {
        val top = mix(brand, Color.WHITE, 0.10f); val bottom = mix(brand, Color.BLACK, 0.62f)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, 0f, H.toFloat(), top, bottom, Shader.TileMode.CLAMP) })
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
            shader = RadialGradient(W * 0.22f, H * 0.05f, W * 0.85f, intArrayOf(0x30FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        })
    }

    /** The app's icon centred at [height] px with a soft drop shadow. */
    private fun drawMark(c: Canvas, icon: Bitmap, height: Float, paint: Paint) {
        val w = height * icon.width / icon.height
        val dst = RectF((W - w) / 2, (H - height) / 2, (W + w) / 2, (H + height) / 2)
        val shadow = Paint(paint).apply { colorFilter = android.graphics.PorterDuffColorFilter(0x66000000, android.graphics.PorterDuff.Mode.SRC_IN) }
        c.drawBitmap(icon, Rect(0, 0, icon.width, icon.height), RectF(dst).apply { offset(0f, 6f) }, shadow)
        c.drawBitmap(icon, Rect(0, 0, icon.width, icon.height), dst, paint)
    }

    /** A low-resolution banner shown as a smaller card so it is upscaled less. */
    private fun drawCard(c: Canvas, banner: Bitmap, paint: Paint) {
        val cw = W * 0.72f; val ch = cw * banner.height / banner.width
        val dst = RectF((W - cw) / 2, (H - ch) / 2, (W + cw) / 2, (H + ch) / 2)
        c.drawRect(RectF(dst).apply { offset(0f, 8f) }, Paint().apply { color = 0x55000000 })
        c.drawBitmap(banner, Rect(0, 0, banner.width, banner.height), dst, paint)
    }

    private fun drawVignette(c: Canvas) {
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
            shader = RadialGradient(W / 2f, H / 2f, W * 0.72f, intArrayOf(0x00000000, 0x00000000, 0x4D000000), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        })
    }

    private fun drawHairline(c: Canvas) {
        c.drawRect(5f, 5f, W - 5f, H - 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = 0x40FFFFFF })
    }

    private fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
    )
}
