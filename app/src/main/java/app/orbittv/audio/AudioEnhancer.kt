package app.orbittv.audio

import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.util.Log

/**
 * System-wide audio enhancement for the TV's small speakers: attaches effects to the global output mix
 * (audio session 0) so every app benefits. Whether the platform honours session 0 is verified on device.
 */
object AudioEnhancer {
    private const val TAG = "OrbitAudio"
    private val effects = ArrayList<AudioEffect>()

    /** Turns every effect off and releases it. */
    fun disable() { release(); Log.i(TAG, "disabled") }

    /** Dialogue clarity and body: loudness +6 dB, presence boost 2 to 6 kHz, gentle low shelf, mild bass and width. */
    fun enable(): String {
        release()
        val report = ArrayList<String>()
        attempt(report, "loudness") { LoudnessEnhancer(0).apply { setTargetGain(600); enabled = true } }
        attempt(report, "equalizer") {
            Equalizer(0, 0).apply {
                for (b in 0 until numberOfBands) {
                    val hz = getCenterFreq(b.toShort()) / 1000
                    val gain = when { hz in 2000..6000 -> 350; hz < 150 -> 250; hz in 150..400 -> -100; else -> 0 }
                    setBandLevel(b.toShort(), gain.coerceIn(bandLevelRange[0].toInt(), bandLevelRange[1].toInt()).toShort())
                }
                enabled = true
            }
        }
        attempt(report, "bass") { BassBoost(0, 0).apply { if (strengthSupported) setStrength(450); enabled = true } }
        attempt(report, "virtualizer") { Virtualizer(0, 0).apply { if (strengthSupported) setStrength(500); enabled = true } }
        val summary = report.joinToString("; ")
        Log.i(TAG, summary)
        return summary
    }

    fun release() { effects.forEach { runCatching { it.enabled = false; it.release() } }; effects.clear() }

    private fun attempt(report: MutableList<String>, name: String, create: () -> AudioEffect) {
        try { val e = create(); effects.add(e); report.add("$name on (enabled=${e.enabled}, id=${e.id})") }
        catch (t: Throwable) { report.add("$name failed: ${t.javaClass.simpleName} ${t.message}") }
    }
}
