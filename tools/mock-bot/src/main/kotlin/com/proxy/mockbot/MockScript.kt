package com.proxy.mockbot

import com.proxy.protocol.ActionStatus
import com.proxy.protocol.BotAction
import com.proxy.protocol.Channel
import com.proxy.protocol.ConfirmKind
import com.proxy.protocol.LauncherMessage
import com.proxy.protocol.Recipient
import java.util.Locale

/**
 * Vaste scenario's van de mock-bot. Bellen en sms'en lopen altijd via een
 * bevestigingskaart; pas na "ja" volgt de actie met het bevestigings-id.
 */
class MockScript {
    private val pending: MutableMap<String, BotAction.ConfirmRequest> = mutableMapOf()
    private var counter: Int = 0

    fun respond(message: LauncherMessage): List<BotAction> =
        when (message) {
            is LauncherMessage.Utterance -> onUtterance(message.tekst)
            is LauncherMessage.ConfirmResponse -> onConfirmResponse(message)
            is LauncherMessage.ActionResult -> onActionResult(message)
            // Gespreksberichten handelt MockCallBridge af.
            is LauncherMessage.Dial,
            is LauncherMessage.Hangup,
            is LauncherMessage.WebRtcOffer,
            is LauncherMessage.LocalIce,
            -> emptyList()
            // Alleen de Roxy-bridge voert action_request uit (Gemini Live-route); de mock-bot niet.
            is LauncherMessage.ActionRequest -> listOf(BotAction.Speak(tekst = "De mock-bot voert geen actieverzoeken uit."))
            is com.proxy.protocol.BioBaseResponse -> onBioBaseResponse(message)
        }

    private fun onUtterance(text: String): List<BotAction> {
        val normalized: String = normalize(text)
        return when {
            normalized.startsWith("bel sarah") -> listOf(confirm(ConfirmKind.CALL, "Bel Sarah?", SARAH, null))
            normalized.startsWith("stuur een bericht naar") -> messageScenario(normalized, text)
            normalized.contains("selfie") -> listOf(BotAction.Photo(lensValue = "front"))
            normalized.contains("foto") -> listOf(BotAction.Photo(lensValue = "back"))
            normalized.startsWith("breng me naar het station") -> listOf(station(BotAction.Navigate.DISPLAY_MAP))
            normalized.startsWith("navigeer naar het station") -> listOf(station(BotAction.Navigate.DISPLAY_TURN_BY_TURN))
            else -> listOf(BotAction.Speak(tekst = "Dat scenario ken ik niet: $text"))
        }
    }

    /** "Stuur een bericht naar <Sarah|Mark> [op <app>]: <tekst>" — zonder app is het sms. */
    private fun messageScenario(
        normalized: String,
        text: String,
    ): List<BotAction> {
        val head: String = normalized.substringBefore(':')
        val recipient: Recipient =
            when {
                head.contains("sarah") -> SARAH
                head.contains("mark") -> MARK
                else -> return listOf(BotAction.Speak(tekst = "Die ontvanger ken ik niet."))
            }
        val channel: Channel =
            Channel.values().firstOrNull { candidate: Channel ->
                candidate != Channel.SMS && Regex("\\bop ${candidate.wire}\\b").containsMatchIn(head)
            } ?: Channel.SMS
        val title: String =
            if (channel == Channel.SMS) "Sms aan ${recipient.naam}?" else "Bericht aan ${recipient.naam} via ${channelName(channel)}?"
        return listOf(confirm(ConfirmKind.SMS, title, recipient, messageBody(text), channel))
    }

    /** Vaste coördinaten (Amsterdam Centraal); de echte bot geocodeert zelf (NAV-3). */
    private fun station(display: String): BotAction.Navigate =
        BotAction.Navigate(doel = "station", weergave = display, lat = STATION_LAT, lon = STATION_LON)

    private fun channelName(channel: Channel): String =
        when (channel) {
            Channel.SMS -> "sms"
            Channel.WHATSAPP -> "WhatsApp"
            Channel.TELEGRAM -> "Telegram"
            Channel.SIGNAL -> "Signal"
            Channel.SLACK -> "Slack"
            Channel.X -> "X"
        }

    private fun onConfirmResponse(response: LauncherMessage.ConfirmResponse): List<BotAction> {
        val request: BotAction.ConfirmRequest =
            pending.remove(response.id) ?: return listOf(BotAction.Speak(tekst = "Die bevestiging ken ik niet."))
        if (!response.akkoord) return listOf(BotAction.Speak(tekst = "Oké, niet gedaan."))
        val recipient: Recipient = Recipient(naam = request.naam, nummer = request.nummer)
        return when (request.soort) {
            ConfirmKind.CALL -> listOf(BotAction.Call(bevestiging = request.id, naam = recipient.naam, nummer = recipient.nummer))
            ConfirmKind.SMS ->
                listOf(
                    BotAction.Sms(
                        bevestiging = request.id,
                        ontvanger = recipient,
                        tekst = request.tekst.orEmpty(),
                        kanaal = request.kanaal,
                    ),
                )
        }
    }

    /** "Wat weet je over <onderwerp>" → memory_query via de telefoon (MC-4); anders null. */
    fun bioBaseRequestFor(message: LauncherMessage): com.proxy.protocol.BioBaseRequest? {
        if (message !is LauncherMessage.Utterance) return null
        val normalized: String = normalize(message.tekst)
        if (!normalized.startsWith("wat weet je over ")) return null
        counter += 1
        return com.proxy.protocol.BioBaseRequest(
            id = "geheugen-$counter",
            tool = com.proxy.protocol.BioBaseTools.MEMORY_QUERY,
            argumenten =
                kotlinx.serialization.json.buildJsonObject {
                    put("vraag", kotlinx.serialization.json.JsonPrimitive(normalized.removePrefix("wat weet je over ").trim()))
                },
        )
    }

    /** Antwoord op een eigen biobase_request: uitspreken wat EVE terugstuurde (efemeer, niets bewaren). */
    private fun onBioBaseResponse(response: com.proxy.protocol.BioBaseResponse): List<BotAction> {
        val error: String? = response.fout
        if (error != null) {
            return listOf(BotAction.Speak(tekst = if (error == "geheugen_offline") "Mijn geheugen is offline." else "Dat lukte niet: $error"))
        }
        return listOf(BotAction.Speak(tekst = "Uit je geheugen: ${response.resultaat}"))
    }

    private fun onActionResult(result: LauncherMessage.ActionResult): List<BotAction> =
        when (result.status) {
            ActionStatus.OK -> emptyList()
            ActionStatus.UNAVAILABLE -> listOf(BotAction.Speak(tekst = "${result.actie} is nog niet beschikbaar op de telefoon."))
            ActionStatus.REFUSED,
            ActionStatus.FAILED,
            -> emptyList()
        }

    private fun confirm(
        kind: ConfirmKind,
        title: String,
        recipient: Recipient,
        body: String?,
        channel: Channel = Channel.SMS,
    ): BotAction.ConfirmRequest {
        counter += 1
        val request: BotAction.ConfirmRequest =
            BotAction.ConfirmRequest(
                id = "mock-$counter",
                titel = title,
                soort = kind,
                naam = recipient.naam,
                nummer = recipient.nummer,
                tekst = body,
                kanaal = channel,
            )
        pending[request.id] = request
        return request
    }

    private fun messageBody(text: String): String {
        val colon: Int = text.indexOf(':')
        return if (colon >= 0) text.substring(colon + 1).trim() else ""
    }

    private fun normalize(text: String): String =
        text
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}: ]"), "")
            .trim()

    companion object {
        val SARAH: Recipient = Recipient(naam = "Sarah", nummer = "+31612345678")
        val MARK: Recipient = Recipient(naam = "Mark", nummer = "+31687654321")
        const val STATION_LAT: Double = 52.3791
        const val STATION_LON: Double = 4.9003
    }
}
