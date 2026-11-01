package app.orbittv.web

import java.security.SecureRandom

/**
 * A per-page token for the bridge calls that act or push data (ready, launch, open, system, page, radio, journey, saveProfiles).
 * The native shell injects it into the main frame only. An embedded frame (the YouTube player iframe) still sees the injected
 * bridge object, because WebView exposes it to every frame, but without the token it can only log and show a short toast.
 */
class BridgeGate {
    @Volatile private var token: String = ""
    fun renew(): String { val b = ByteArray(18); SecureRandom().nextBytes(b); token = b.joinToString("") { "%02x".format(it) }; return token }
    fun allows(t: String?): Boolean = token.isNotEmpty() && t == token
}
