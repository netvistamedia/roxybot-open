package com.proxy.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** Alles wat de launcher van de bot ontvangt: een botactie of een gespreksbericht. */
sealed interface Incoming {
    data class Action(val parse: ActionParse) : Incoming

    data class Call(val message: CallMessage) : Incoming

    /** BioBASE-relayverzoek van de bot (MC-4). */
    data class BioBase(val request: BioBaseRequest) : Incoming
}

sealed interface ActionParse {
    data class Parsed(val action: BotAction) : ActionParse

    /** Onbekend type, `payment`, of een onleesbaar bericht: de launcher weigert (LS-10). */
    data class Rejected(val type: String) : ActionParse
}

object Protocol {
    const val TYPE_FIELD: String = "type"
    const val PAYLOAD_FIELD: String = "payload"

    val json: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }

    fun encodeAction(action: BotAction): String =
        when (action) {
            is BotAction.Speak -> envelope(action.type, BotAction.Speak.serializer(), action)
            is BotAction.Listen -> envelope(action.type, JsonObject(emptyMap()))
            is BotAction.ConfirmRequest -> envelope(action.type, BotAction.ConfirmRequest.serializer(), action)
            is BotAction.Call -> envelope(action.type, BotAction.Call.serializer(), action)
            is BotAction.Sms -> envelope(action.type, BotAction.Sms.serializer(), action)
            is BotAction.Photo -> envelope(action.type, BotAction.Photo.serializer(), action)
            is BotAction.Navigate -> envelope(action.type, BotAction.Navigate.serializer(), action)
            is BotAction.ContentShow -> envelope(action.type, BotAction.ContentShow.serializer(), action)
        }

    fun decodeAction(text: String): ActionParse {
        val (type: String, payload: JsonObject) = split(text) ?: return ActionParse.Rejected("")
        return try {
            val action: BotAction? =
                when (type) {
                    BotAction.Speak.TYPE -> json.decodeFromJsonElement(BotAction.Speak.serializer(), payload)
                    BotAction.Listen.TYPE -> BotAction.Listen
                    BotAction.ConfirmRequest.TYPE ->
                        json.decodeFromJsonElement(BotAction.ConfirmRequest.serializer(), payload)
                    BotAction.Call.TYPE -> json.decodeFromJsonElement(BotAction.Call.serializer(), payload)
                    BotAction.Sms.TYPE -> json.decodeFromJsonElement(BotAction.Sms.serializer(), payload)
                    BotAction.Photo.TYPE -> json.decodeFromJsonElement(BotAction.Photo.serializer(), payload)
                    BotAction.Navigate.TYPE -> json.decodeFromJsonElement(BotAction.Navigate.serializer(), payload)
                    BotAction.ContentShow.TYPE ->
                        json.decodeFromJsonElement(BotAction.ContentShow.serializer(), payload)
                    else -> null
                }
            if (action == null) ActionParse.Rejected(type) else ActionParse.Parsed(action)
        } catch (error: SerializationException) {
            ActionParse.Rejected(type)
        } catch (error: IllegalArgumentException) {
            ActionParse.Rejected(type)
        }
    }

    /** Gespreksberichten gaan apart; al het andere valt onder de actieregels (LS-10). */
    fun decodeIncoming(text: String): Incoming {
        val type: String? = split(text)?.first
        if (type == BioBaseRequest.TYPE) {
            val payload: JsonObject = split(text)?.second ?: return Incoming.Action(ActionParse.Rejected(type))
            return try {
                Incoming.BioBase(json.decodeFromJsonElement(BioBaseRequest.serializer(), payload))
            } catch (error: SerializationException) {
                Incoming.Action(ActionParse.Rejected(type))
            } catch (error: IllegalArgumentException) {
                Incoming.Action(ActionParse.Rejected(type))
            }
        }
        if (type != null && type in CallMessage.TYPES) {
            val call: CallMessage? = decodeCallMessage(text)
            return if (call != null) Incoming.Call(call) else Incoming.Action(ActionParse.Rejected(type))
        }
        return Incoming.Action(decodeAction(text))
    }

    fun encodeBioBaseRequest(request: BioBaseRequest): String =
        envelope(BioBaseRequest.TYPE, BioBaseRequest.serializer(), request)

    fun encodeBioBaseResponse(response: BioBaseResponse): String =
        envelope(BioBaseResponse.TYPE, BioBaseResponse.serializer(), response)

    fun decodeBioBaseResponse(text: String): BioBaseResponse? {
        val (type: String, payload: JsonObject) = split(text) ?: return null
        if (type != BioBaseResponse.TYPE) return null
        return try {
            json.decodeFromJsonElement(BioBaseResponse.serializer(), payload)
        } catch (error: SerializationException) {
            null
        } catch (error: IllegalArgumentException) {
            null
        }
    }

    fun encodeCallMessage(message: CallMessage): String =
        when (message) {
            is CallMessage.WebRtcAnswer -> envelope(message.type, CallMessage.WebRtcAnswer.serializer(), message)
            is CallMessage.Ice -> envelope(message.type, CallMessage.Ice.serializer(), message)
            is CallMessage.Status -> envelope(message.type, CallMessage.Status.serializer(), message)
        }

    fun decodeCallMessage(text: String): CallMessage? {
        val (type: String, payload: JsonObject) = split(text) ?: return null
        return try {
            when (type) {
                CallMessage.WebRtcAnswer.TYPE -> json.decodeFromJsonElement(CallMessage.WebRtcAnswer.serializer(), payload)
                CallMessage.Ice.TYPE -> json.decodeFromJsonElement(CallMessage.Ice.serializer(), payload)
                CallMessage.Status.TYPE -> json.decodeFromJsonElement(CallMessage.Status.serializer(), payload)
                else -> null
            }
        } catch (error: SerializationException) {
            null
        } catch (error: IllegalArgumentException) {
            null
        }
    }

    fun encodeMessage(message: LauncherMessage): String =
        when (message) {
            is BioBaseResponse -> envelope(message.type, BioBaseResponse.serializer(), message)
            is LauncherMessage.Dial -> envelope(message.type, LauncherMessage.Dial.serializer(), message)
            is LauncherMessage.Hangup -> envelope(message.type, LauncherMessage.Hangup.serializer(), message)
            is LauncherMessage.WebRtcOffer -> envelope(message.type, LauncherMessage.WebRtcOffer.serializer(), message)
            is LauncherMessage.LocalIce -> envelope(message.type, LauncherMessage.LocalIce.serializer(), message)
            is LauncherMessage.Utterance -> envelope(message.type, LauncherMessage.Utterance.serializer(), message)
            is LauncherMessage.ConfirmResponse ->
                envelope(message.type, LauncherMessage.ConfirmResponse.serializer(), message)
            is LauncherMessage.ActionResult ->
                envelope(message.type, LauncherMessage.ActionResult.serializer(), message)
            is LauncherMessage.ActionRequest ->
                envelope(message.type, LauncherMessage.ActionRequest.serializer(), message)
        }

    fun decodeMessage(text: String): LauncherMessage? {
        val (type: String, payload: JsonObject) = split(text) ?: return null
        return try {
            when (type) {
                LauncherMessage.Utterance.TYPE ->
                    json.decodeFromJsonElement(LauncherMessage.Utterance.serializer(), payload)
                LauncherMessage.ConfirmResponse.TYPE ->
                    json.decodeFromJsonElement(LauncherMessage.ConfirmResponse.serializer(), payload)
                LauncherMessage.ActionResult.TYPE ->
                    json.decodeFromJsonElement(LauncherMessage.ActionResult.serializer(), payload)
                LauncherMessage.ActionRequest.TYPE ->
                    json.decodeFromJsonElement(LauncherMessage.ActionRequest.serializer(), payload)
                BioBaseResponse.TYPE -> json.decodeFromJsonElement(BioBaseResponse.serializer(), payload)
                LauncherMessage.Dial.TYPE -> json.decodeFromJsonElement(LauncherMessage.Dial.serializer(), payload)
                LauncherMessage.Hangup.TYPE -> json.decodeFromJsonElement(LauncherMessage.Hangup.serializer(), payload)
                LauncherMessage.WebRtcOffer.TYPE ->
                    json.decodeFromJsonElement(LauncherMessage.WebRtcOffer.serializer(), payload)
                LauncherMessage.LocalIce.TYPE -> json.decodeFromJsonElement(LauncherMessage.LocalIce.serializer(), payload)
                else -> null
            }
        } catch (error: SerializationException) {
            null
        } catch (error: IllegalArgumentException) {
            null
        }
    }

    private fun <T> envelope(
        type: String,
        serializer: KSerializer<T>,
        value: T,
    ): String = envelope(type, json.encodeToJsonElement(serializer, value))

    private fun envelope(
        type: String,
        payload: JsonElement,
    ): String =
        json.encodeToString(
            JsonObject.serializer(),
            JsonObject(mapOf(TYPE_FIELD to JsonPrimitive(type), PAYLOAD_FIELD to payload)),
        )

    private fun split(text: String): Pair<String, JsonObject>? =
        try {
            val root: JsonObject = json.parseToJsonElement(text).jsonObject
            val type: String = (root[TYPE_FIELD] as? JsonPrimitive)?.contentOrNull ?: return null
            val payload: JsonObject = (root[PAYLOAD_FIELD] as? JsonObject) ?: JsonObject(emptyMap())
            type to payload
        } catch (error: SerializationException) {
            null
        } catch (error: IllegalArgumentException) {
            null
        }
}
