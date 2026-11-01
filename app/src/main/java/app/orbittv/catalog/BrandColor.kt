package app.orbittv.catalog

/** Picks a representative brand colour from ARGB pixels, ignoring near-black, near-white and transparent ones. */
object BrandColor {
    const val DEFAULT = 0xFF3DF2FF.toInt()

    fun dominant(pixels: IntArray): Int {
        val counts = HashMap<Int, FloatArray>() // bucket -> [count, sumR, sumG, sumB, score]
        for (p in pixels) {
            if ((p ushr 24) < 0x80) continue
            val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
            val lum = (r * 299 + g * 587 + b * 114) / 1000
            if (lum < 28 || lum > 232) continue
            val mx = maxOf(r, g, b); val mn = minOf(r, g, b)
            val saturation = if (mx == 0) 0f else (mx - mn) / mx.toFloat()
            val bucket = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            val acc = counts.getOrPut(bucket) { FloatArray(5) }
            acc[0]++; acc[1] += r; acc[2] += g; acc[3] += b
            acc[4] += 1f + 3f * saturation // colourful pixels count for more than greys
        }
        val best = counts.values.maxByOrNull { it[4] } ?: return DEFAULT
        val n = best[0]
        return (0xFF shl 24) or ((best[1] / n).toInt() shl 16) or ((best[2] / n).toInt() shl 8) or (best[3] / n).toInt()
    }
}
