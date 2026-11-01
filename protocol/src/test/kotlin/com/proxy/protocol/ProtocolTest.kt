package com.proxy.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTest {
    private val samples: List<BotAction> =
        listOf(
            BotAction.Speak(tekst = "Hallo", urgent = true),
            BotAction.Listen,
            BotAction.ConfirmRequest(id = "c1", titel = "Bel Sarah?", soort = ConfirmKind.CALL, naam = "Sarah", nummer = "+31612345678"),
            BotAction.Call(bevestiging = "c1", naam = "Sarah", nummer = "+31612345678"),
            BotAction.Sms(bevestiging = "c2", ontvanger = Recipient("Mark", "+31687654321"), tekst = "ik ben onderweg"),
            BotAction.Photo(lensValue = "front"),
            BotAction.Navigate(doel = "station"),
            BotAction.ContentShow(tekst = "Document", titel = "Titel"),
        )

    @Test
    fun `every LS-9 action type round-trips through the wire format`() {
        assertEquals(BotAction.TYPES, samples.map { action: BotAction -> action.type })
        for (action: BotAction in samples) {
            assertEquals(ActionParse.Parsed(action), Protocol.decodeAction(Protocol.encodeAction(action)))
        }
    }

    @Test
    fun `wire format is an envelope with type and payload`() {
        assertEquals(
            """{"type":"photo","payload":{"lens":"front"}}""",
            Protocol.encodeAction(BotAction.Photo(lensValue = "front")),
        )
    }

    @Test
    fun `unknown, payment and malformed actions are rejected`() {
        assertEquals(ActionParse.Rejected("payment"), Protocol.decodeAction("""{"type":"payment","payload":{"bedrag":10}}"""))
        assertEquals(ActionParse.Rejected("dance"), Protocol.decodeAction("""{"type":"dance"}"""))
        assertEquals(ActionParse.Rejected("call"), Protocol.decodeAction("""{"type":"call","payload":{}}"""))
        assertEquals(ActionParse.Rejected(""), Protocol.decodeAction("niet json"))
    }

    @Test
    fun `lens defaults to back unless explicitly front`() {
        assertEquals(Lens.FRONT, BotAction.Photo(lensValue = "front").lens)
        assertEquals(Lens.BACK, BotAction.Photo(lensValue = "back").lens)
        assertEquals(Lens.BACK, BotAction.Photo(lensValue = null).lens)
        assertEquals(Lens.BACK, BotAction.Photo(lensValue = "panorama").lens)
    }

    @Test
    fun `navigation defaults to walking`() {
        val parsed: ActionParse = Protocol.decodeAction("""{"type":"navigate","payload":{"doel":"station"}}""")
        assertEquals(ActionParse.Parsed(BotAction.Navigate(doel = "station", modus = "lopen", weergave = "kaart")), parsed)
        val withRoute: BotAction.Navigate =
            BotAction.Navigate(doel = "thuis", lat = 52.0, lon = 4.0, route = listOf(listOf(52.1, 4.1), listOf(52.0, 4.0)))
        assertEquals(ActionParse.Parsed(withRoute), Protocol.decodeAction(Protocol.encodeAction(withRoute)))
    }

    @Test
    fun `launcher messages round-trip`() {
        val messages: List<LauncherMessage> =
            listOf(
                LauncherMessage.Utterance("Bel Sarah"),
                LauncherMessage.ConfirmResponse(id = "c1", akkoord = true),
                LauncherMessage.ActionResult(actie = "call", status = ActionStatus.UNAVAILABLE, detail = "x"),
            )
        for (message: LauncherMessage in messages) {
            assertEquals(message, Protocol.decodeMessage(Protocol.encodeMessage(message)))
        }
        assertNull(Protocol.decodeMessage("""{"type":"onbekend","payload":{}}"""))
    }

    @Test
    fun `call messages are separated from bot actions`() {
        val status: CallMessage = CallMessage.Status(gesprek = "c1", status = CallState.DISCONNECTED, oorzaak = DisconnectReason.BUSY)
        assertEquals(Incoming.Call(status), Protocol.decodeIncoming(Protocol.encodeCallMessage(status)))
        val ice: CallMessage = CallMessage.Ice(gesprek = "c1", kandidaat = IceCandidatePayload("0", 0, "candidate:1 1 udp 1 10.0.0.2 5000 typ host"))
        assertEquals(Incoming.Call(ice), Protocol.decodeIncoming(Protocol.encodeCallMessage(ice)))
        val answer: CallMessage = CallMessage.WebRtcAnswer(gesprek = "c1", sdp = "v=0")
        assertEquals(Incoming.Call(answer), Protocol.decodeIncoming(Protocol.encodeCallMessage(answer)))
        assertEquals(
            Incoming.Action(ActionParse.Parsed(BotAction.Listen)),
            Protocol.decodeIncoming(Protocol.encodeAction(BotAction.Listen)),
        )
        assertEquals(Incoming.Action(ActionParse.Rejected("call_status")), Protocol.decodeIncoming("""{"type":"call_status","payload":{}}"""))
    }

    @Test
    fun `launcher call messages round-trip`() {
        val messages: List<LauncherMessage> =
            listOf(
                LauncherMessage.Dial(gesprek = "c1", nummer = "+31612345678", token = "abc"),
                LauncherMessage.Hangup(gesprek = "c1"),
                LauncherMessage.WebRtcOffer(gesprek = "c1", sdp = "v=0"),
                LauncherMessage.LocalIce(gesprek = "c1", kandidaat = IceCandidatePayload(null, 0, "candidate:x")),
            )
        for (message: LauncherMessage in messages) {
            assertEquals(message, Protocol.decodeMessage(Protocol.encodeMessage(message)))
        }
    }

    @Test
    fun `kanaal defaults to sms and carries the messaging apps`() {
        val plain: ActionParse =
            Protocol.decodeAction("""{"type":"sms","payload":{"bevestiging":"c1","ontvanger":{"naam":"Mark","nummer":"+31687654321"},"tekst":"hoi"}}""")
        assertEquals(Channel.SMS, ((plain as ActionParse.Parsed).action as BotAction.Sms).kanaal)
        for (channel: Channel in Channel.values()) {
            val action: BotAction =
                BotAction.Sms(bevestiging = "c1", ontvanger = Recipient("Sarah", "+31612345678"), tekst = "hoi", kanaal = channel)
            assertEquals(ActionParse.Parsed(action), Protocol.decodeAction(Protocol.encodeAction(action)))
            assertTrue(Protocol.encodeAction(action).contains("\"kanaal\":\"${channel.wire}\""))
        }
        assertEquals(8, BotAction.TYPES.size)
    }

    @Test
    fun `biobase relay requests are their own category and responses are ephemeral`() {
        val request: BioBaseRequest =
            BioBaseRequest(id = "r1", tool = BioBaseTools.CONTACTS_RESOLVE, argumenten = kotlinx.serialization.json.buildJsonObject {
                put("naam", kotlinx.serialization.json.JsonPrimitive("Sarah"))
            })
        assertEquals(Incoming.BioBase(request), Protocol.decodeIncoming(Protocol.encodeBioBaseRequest(request)))
        val response: BioBaseResponse = BioBaseResponse(id = "r1", fout = BioBaseErrors.OFFLINE)
        val wire: String = Protocol.encodeBioBaseResponse(response)
        assertTrue(wire.contains("\"efemeer\":true"))
        assertEquals(response, Protocol.decodeBioBaseResponse(wire))
        assertEquals(setOf("contacts_resolve", "profile_get", "memory_query"), BioBaseTools.BOT_READABLE)
    }

    @Test
    fun `action_request round-trips with only the function-call arguments`() {
        val request: LauncherMessage.ActionRequest =
            LauncherMessage.ActionRequest(id = "g1", actie = "sms", naam = "Mark", tekst = "ik ben onderweg", kanaal = Channel.WHATSAPP)
        val wire: String = Protocol.encodeMessage(request)
        assertTrue(wire.contains("\"type\":\"action_request\""))
        assertTrue(wire.contains("\"kanaal\":\"whatsapp\""))
        assertEquals(request, Protocol.decodeMessage(wire))
        val call: LauncherMessage.ActionRequest = LauncherMessage.ActionRequest(id = "g2", actie = "call", naam = "Sarah")
        assertEquals(call, Protocol.decodeMessage(Protocol.encodeMessage(call)))
        assertTrue(!Protocol.encodeMessage(call).contains("nummer"))
    }
}
