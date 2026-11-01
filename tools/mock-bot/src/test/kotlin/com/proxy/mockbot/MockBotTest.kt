package com.proxy.mockbot

import com.proxy.protocol.ActionParse
import com.proxy.protocol.BotAction
import com.proxy.protocol.ConfirmKind
import com.proxy.protocol.LauncherMessage
import com.proxy.protocol.Lens
import com.proxy.protocol.Protocol
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.net.URI
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class MockBotTest {
    @Test
    fun `Bel Sarah asks for confirmation and calls after yes`() {
        val script: MockScript = MockScript()
        val confirm: BotAction.ConfirmRequest =
            script.respond(LauncherMessage.Utterance("Bel Sarah")).single() as BotAction.ConfirmRequest
        assertEquals("Bel Sarah?", confirm.titel)
        assertEquals(ConfirmKind.CALL, confirm.soort)
        val call: BotAction.Call =
            script.respond(LauncherMessage.ConfirmResponse(confirm.id, akkoord = true)).single() as BotAction.Call
        assertEquals(confirm.id, call.bevestiging)
        assertEquals(MockScript.SARAH.nummer, call.nummer)
    }

    @Test
    fun `no means no call`() {
        val script: MockScript = MockScript()
        val confirm: BotAction.ConfirmRequest =
            script.respond(LauncherMessage.Utterance("Bel Sarah")).single() as BotAction.ConfirmRequest
        val reply: BotAction = script.respond(LauncherMessage.ConfirmResponse(confirm.id, akkoord = false)).single()
        assertTrue(reply is BotAction.Speak)
    }

    @Test
    fun `sms to Mark carries the literal text through confirmation`() {
        val script: MockScript = MockScript()
        val confirm: BotAction.ConfirmRequest =
            script.respond(LauncherMessage.Utterance("Stuur een bericht naar Mark: ik ben onderweg")).single()
                as BotAction.ConfirmRequest
        assertEquals(ConfirmKind.SMS, confirm.soort)
        assertEquals("ik ben onderweg", confirm.tekst)
        val sms: BotAction.Sms =
            script.respond(LauncherMessage.ConfirmResponse(confirm.id, akkoord = true)).single() as BotAction.Sms
        assertEquals("ik ben onderweg", sms.tekst)
        assertEquals(confirm.id, sms.bevestiging)
    }

    @Test
    fun `selfie uses the front camera and navigation walks to the station`() {
        val script: MockScript = MockScript()
        val photo: BotAction.Photo = script.respond(LauncherMessage.Utterance("Maak een selfie")).single() as BotAction.Photo
        assertEquals(Lens.FRONT, photo.lens)
        val navigate: BotAction.Navigate =
            script.respond(LauncherMessage.Utterance("Breng me naar het station")).single() as BotAction.Navigate
        assertEquals("station", navigate.doel)
        assertEquals(BotAction.Navigate.MODE_WALKING, navigate.modus)
        assertEquals(BotAction.Navigate.DISPLAY_MAP, navigate.weergave)
        assertEquals(MockScript.STATION_LAT, navigate.lat)
        val handover: BotAction.Navigate =
            script.respond(LauncherMessage.Utterance("Navigeer naar het station")).single() as BotAction.Navigate
        assertEquals(BotAction.Navigate.DISPLAY_TURN_BY_TURN, handover.weergave)
    }

    @Test
    fun `server accepts a websocket connection and answers Bel Sarah with a confirm_request`() {
        val port: Int = ServerSocket(0).use { socket: ServerSocket -> socket.localPort }
        val server: MockBotServer = MockBotServer(port, log = { _: String -> })
        server.start()
        val received: LinkedBlockingQueue<String> = LinkedBlockingQueue()
        val client: WebSocketClient =
            object : WebSocketClient(URI("ws://localhost:$port")) {
                override fun onOpen(handshakedata: ServerHandshake) {}

                override fun onMessage(message: String) {
                    received.put(message)
                }

                override fun onClose(
                    code: Int,
                    reason: String,
                    remote: Boolean,
                ) {}

                override fun onError(ex: Exception) {}
            }
        try {
            var connected: Boolean = false
            for (attempt: Int in 0 until 20) {
                connected = client.connectBlocking(2, TimeUnit.SECONDS)
                if (connected) break
                Thread.sleep(100)
            }
            assertTrue(connected)
            client.send(Protocol.encodeMessage(LauncherMessage.Utterance("Bel Sarah")))
            val reply: String = received.poll(5, TimeUnit.SECONDS) ?: ""
            val parsed: ActionParse = Protocol.decodeAction(reply)
            assertTrue(parsed is ActionParse.Parsed)
            val action: BotAction = (parsed as ActionParse.Parsed).action
            assertEquals("Bel Sarah?", (action as BotAction.ConfirmRequest).titel)
        } finally {
            client.closeBlocking()
            server.stop(1000)
        }
    }

    @Test
    fun `a WhatsApp message to Sarah asks for confirmation with the channel and sends it after yes`() {
        val script: MockScript = MockScript()
        val confirm: BotAction.ConfirmRequest =
            script.respond(LauncherMessage.Utterance("Stuur een bericht naar Sarah op WhatsApp: ik ben onderweg")).single()
                as BotAction.ConfirmRequest
        assertEquals(com.proxy.protocol.Channel.WHATSAPP, confirm.kanaal)
        assertEquals("Bericht aan Sarah via WhatsApp?", confirm.titel)
        assertEquals("ik ben onderweg", confirm.tekst)
        val sms: BotAction.Sms =
            script.respond(LauncherMessage.ConfirmResponse(confirm.id, akkoord = true)).single() as BotAction.Sms
        assertEquals(com.proxy.protocol.Channel.WHATSAPP, sms.kanaal)
        assertEquals(MockScript.SARAH.nummer, sms.ontvanger.nummer)
    }

    @Test
    fun `every app channel is recognised and plain messages stay sms`() {
        for (app: String in listOf("telegram", "signal", "slack", "x")) {
            val confirm: BotAction.ConfirmRequest =
                MockScript().respond(LauncherMessage.Utterance("Stuur een bericht naar Mark op $app: hoi")).single()
                    as BotAction.ConfirmRequest
            assertEquals(app, confirm.kanaal.wire)
        }
        val plain: BotAction.ConfirmRequest =
            MockScript().respond(LauncherMessage.Utterance("Stuur een bericht naar Mark: ik ben onderweg")).single()
                as BotAction.ConfirmRequest
        assertEquals(com.proxy.protocol.Channel.SMS, plain.kanaal)
        assertEquals("Sms aan Mark?", plain.titel)
    }

    @Test
    fun `memory questions go to BioBASE through the phone and the answer is spoken`() {
        val script: MockScript = MockScript()
        val request: com.proxy.protocol.BioBaseRequest =
            requireNotNull(script.bioBaseRequestFor(LauncherMessage.Utterance("Wat weet je over het budget?")))
        assertEquals("memory_query", request.tool)
        assertEquals("het budget", (request.argumenten["vraag"] as kotlinx.serialization.json.JsonPrimitive).content)
        val offline: BotAction =
            script.respond(com.proxy.protocol.BioBaseResponse(id = request.id, fout = "geheugen_offline")).single()
        assertEquals("Mijn geheugen is offline.", (offline as BotAction.Speak).tekst)
    }
}
