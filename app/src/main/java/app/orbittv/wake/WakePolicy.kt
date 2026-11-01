package app.orbittv.wake

/** Decides what Orbit does when the TV wakes. */
object WakePolicy {
    /** Apps that are TV inputs (tuner, HDMI); waking into one of these keeps Orbit out of the way. */
    val tvInputPackages: Set<String> = setOf(
        "com.vewd.core.tvapp",
        "com.vewd.core.fvpplayer",
        "com.amazon.tv.livetv",
        "com.amazon.hedwig",
        "com.amazon.tv.inputs",
        "com.amazon.franktvinput",
        "com.droidlogic.tvinput",
    )

    /** Orbit comes to the front on every wake unless a TV input was in use. */
    fun shouldTakeOver(foregroundAtSleep: String?): Boolean =
        foregroundAtSleep == null || foregroundAtSleep !in tvInputPackages

    /** The wake intro plays only when Orbit was not already on screen when the TV slept. */
    fun shouldPlayIntro(orbitInFront: Boolean): Boolean = !orbitInFront

    /** Home Mode can play radio, so a wake always lands on the Cinema home instead; other pages stay as they were. */
    fun pageAfterWake(current: String): String = if (current == "home") "cinema" else current

    /** A TV with no profiles starts in setup; otherwise the page it was on (never Home Mode, see [pageAfterWake]). */
    fun startPage(saved: String?, hasProfiles: Boolean): String = if (!hasProfiles) "setup" else pageAfterWake(saved ?: "cinema")
}
