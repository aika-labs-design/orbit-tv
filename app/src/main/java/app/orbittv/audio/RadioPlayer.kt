package app.orbittv.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log

/** Plays one radio stream at a time (MP3, AAC or HLS). States: connecting, playing, failed, stopped. Main thread only. */
class RadioPlayer(private val report: (String) -> Unit) {
    private var last = "stopped"
    private fun onState(s: String) { last = s; report(s) }
    private var player: MediaPlayer? = null
    private val watchdog = RadioWatchdog()
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (watchdog.check(System.currentTimeMillis()) == "failed") { release(); onState("failed"); return }
            if (nowPlaying(last, player?.let { runCatching { it.isPlaying }.getOrDefault(false) } ?: false)) { watchdog.onRecovered(); onState("playing") }   // some players never send buffering-end
            if (player != null) handler.postDelayed(this, 1_000)
        }
    }

    fun play(url: String) {
        release()
        watchdog.onPlayRequested(System.currentTimeMillis()); onState("connecting")
        player = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            setOnPreparedListener { it.start(); watchdog.onPrepared(); onState("playing") }
            setOnErrorListener { _, what, extra -> Log.w("OrbitRadio", "error $what/$extra on $url"); watchdog.onError(); true }
            // A live stream that ends or stops delivering is a failure too; never leave the page saying ON AIR in silence.
            setOnCompletionListener { Log.w("OrbitRadio", "stream ended: $url"); watchdog.onError() }
            setOnInfoListener { _, what, _ ->
                when (what) {
                    MediaPlayer.MEDIA_INFO_BUFFERING_START -> { watchdog.onStall(System.currentTimeMillis()); onState("connecting") }
                    MediaPlayer.MEDIA_INFO_BUFFERING_END -> { watchdog.onRecovered(); onState("playing") }
                }
                false
            }
            try { setDataSource(url); prepareAsync() } catch (e: Exception) { Log.w("OrbitRadio", "bad source $url", e); watchdog.onError() }
        }
        handler.postDelayed(tick, 1_000)
    }

    fun stop() { val was = player != null; release(); if (was) onState("stopped") }

    companion object {
        /** Sound is coming out while the page was told tuning (or a stall): time to say playing. */
        fun nowPlaying(last: String, isPlaying: Boolean): Boolean = isPlaying && last != "playing"

        /** Only web streams: the page names the URL, so file:, content: and other schemes never reach the player. */
        fun playable(url: String): Boolean = Regex("^https?://[^/\\s]+", RegexOption.IGNORE_CASE).containsMatchIn(url)
    }

    private fun release() {
        handler.removeCallbacks(tick)
        player?.let { runCatching { it.reset() }; it.release() }
        player = null
    }
}
