package com.proxy.mockbot

import com.proxy.protocol.BotAction
import com.proxy.protocol.CallMessage
import com.proxy.protocol.LauncherMessage
import com.proxy.protocol.Protocol
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

/** SDP's zijn lang; de log toont alleen het begin. */
private const val LOG_LIMIT: Int = 160

/** Lokale WebSocket-server die zich gedraagt als de Communication Partner Bot. */
class MockBotServer(
    port: Int,
    private val log: (String) -> Unit = ::println,
) : WebSocketServer(InetSocketAddress(port)) {
    private val scripts: MutableMap<WebSocket, MockScript> = ConcurrentHashMap()
    private val bridges: MutableMap<WebSocket, MockCallBridge> = ConcurrentHashMap()

    init {
        isReuseAddr = true
    }

    override fun onStart() {
        log("mock-bot luistert op ws://localhost:${address.port}")
    }

    override fun onOpen(
        conn: WebSocket,
        handshake: ClientHandshake,
    ) {
        scripts[conn] = MockScript()
        bridges[conn] = newBridge(conn)
        log("verbonden: ${conn.remoteSocketAddress}")
    }

    override fun onClose(
        conn: WebSocket,
        code: Int,
        reason: String,
        remote: Boolean,
    ) {
        scripts.remove(conn)
        bridges.remove(conn)?.close()
        log("verbinding gesloten ($code)")
    }

    override fun onMessage(
        conn: WebSocket,
        message: String,
    ) {
        log("← ${message.take(LOG_LIMIT)}")
        val decoded: LauncherMessage? = Protocol.decodeMessage(message)
        if (decoded == null) {
            log("   (onbekend bericht genegeerd)")
            return
        }
        val bridge: MockCallBridge = bridges.getOrPut(conn) { newBridge(conn) }
        if (bridge.handle(decoded)) return
        val script: MockScript = scripts.getOrPut(conn) { MockScript() }
        val memoryRequest: com.proxy.protocol.BioBaseRequest? = script.bioBaseRequestFor(decoded)
        if (memoryRequest != null) {
            val encoded: String = Protocol.encodeBioBaseRequest(memoryRequest)
            log("→ $encoded")
            conn.send(encoded)
            return
        }
        for (action: BotAction in script.respond(decoded)) {
            val encoded: String = Protocol.encodeAction(action)
            log("→ $encoded")
            conn.send(encoded)
        }
    }

    private fun newBridge(conn: WebSocket): MockCallBridge =
        MockCallBridge(
            send = { message: CallMessage ->
                val encoded: String = Protocol.encodeCallMessage(message)
                log("→ ${encoded.take(LOG_LIMIT)}")
                if (conn.isOpen) conn.send(encoded)
            },
            log = log,
        )

    override fun onError(
        conn: WebSocket?,
        ex: Exception,
    ) {
        log("fout: ${ex.message}")
    }
}
