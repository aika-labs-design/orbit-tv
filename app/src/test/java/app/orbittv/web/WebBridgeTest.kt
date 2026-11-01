package app.orbittv.web

import org.junit.Assert.assertEquals
import org.junit.Test

class WebBridgeTest {
    @Test fun `ready needs the page token, so an embedded frame cannot trigger the pushes`() {
        val gate = BridgeGate(); val token = gate.renew()
        var readies = 0
        val bridge = WebBridge(onLaunch = {}, onReady = { readies++ }, gate = gate)
        bridge.ready(null); bridge.ready(""); bridge.ready("guess")
        assertEquals(0, readies)
        bridge.ready(token)
        assertEquals(1, readies)
    }

    @Test fun `the screensaver bridge has no gate and its ready still works`() {
        var readies = 0
        WebBridge(onLaunch = {}, onReady = { readies++ }).ready(null)
        assertEquals(1, readies)
    }

    @Test fun `toast stays ungated but is capped`() {
        var shown = ""
        WebBridge(onLaunch = {}, onReady = {}, gate = BridgeGate().also { it.renew() }, onToast = { shown = it }).toast("x".repeat(500))
        assertEquals(120, shown.length)
    }

    @Test fun `a ready refused before the token arrived runs once the token is injected`() {
        val gate = BridgeGate()
        var readies = 0
        val bridge = WebBridge(onLaunch = {}, onReady = { readies++ }, gate = gate)
        bridge.tokenInjected(); assertEquals(0, readies)   // nothing was refused: nothing to replay
        bridge.ready("")                                    // the page gave up waiting, or asked too early
        assertEquals(0, readies)
        gate.renew(); bridge.tokenInjected()
        assertEquals(1, readies)
        bridge.tokenInjected(); assertEquals(1, readies)    // replayed once
    }

    @Test fun `a refused ready from the previous page is forgotten when a new page starts`() {
        val gate = BridgeGate()
        var readies = 0
        val bridge = WebBridge(onLaunch = {}, onReady = { readies++ }, gate = gate)
        bridge.ready(null); bridge.pageStarted(); gate.renew(); bridge.tokenInjected()
        assertEquals(0, readies)
    }
}
