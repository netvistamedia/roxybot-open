package com.proxy.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Berichten van launcher naar bot. Op de draad: {"type": "<type>", "payload": {...}}. */
sealed interface LauncherMessage {
    val type: String

    /** Definitief transcript; de bot bepaalt de intent (LS-7). */
    @Serializable
    data class Utterance(
        val tekst: String,
    ) : LauncherMessage {
        override val type: String get() = TYPE

        companion object {
            const val TYPE: String = "utterance"
        }
    }

    /** Antwoord op een bevestigingskaart: Ja, Nee of timeout (= Nee, LS-15). */
    @Serializable
    data class ConfirmResponse(
        val id: String,
        val akkoord: Boolean,
    ) : LauncherMessage {
        override val type: String get() = TYPE

        companion object {
            const val TYPE: String = "confirm_response"
        }
    }

    /** Uitkomst van een botactie. */
    @Serializable
    data class ActionResult(
        val actie: String,
        val status: ActionStatus,
        val detail: String? = null,
    ) : LauncherMessage {
        override val type: String get() = TYPE

        companion object {
            const val TYPE: String = "action_result"
        }
    }

    /**
     * Gemini Live-route (GL-2): een bel- of berichtverzoek uit een function call, naar de
     * Roxy-bridge. Alleen de argumenten; Roxy zoekt het nummer op en antwoordt via
     * `confirm_request`, nooit direct met `call`/`sms`.
     */
    @Serializable
    data class ActionRequest(
        val id: String,
        /** "call" of "sms". */
        val actie: String,
        val naam: String,
        val tekst: String? = null,
        val kanaal: Channel? = null,
    ) : LauncherMessage {
        override val type: String get() = TYPE

        companion object {
            const val TYPE: String = "action_request"
            const val CALL: String = "call"
            const val SMS: String = "sms"
        }
    }

    /** Gespreksberichten van launcher naar bridge (via het botkanaal). */
    @Serializable
    data class Dial(
        val gesprek: String,
        val nummer: String,
        /** Payload-hash van het ConfirmationToken; dekt het doelnummer (TB-1). */
        val token: String,
    ) : LauncherMessage {
        override val type: String get() = TYPE

        companion object {
            const val TYPE: String = "dial"
        }
    }

    @Serializable
    data class Hangup(
        val gesprek: String,
    ) : LauncherMessage {
        override val type: String get() = TYPE

        companion object {
            const val TYPE: String = "hangup"
        }
    }

    @Serializable
    data class WebRtcOffer(
        val gesprek: String,
        val sdp: String,
    ) : LauncherMessage {
        override val type: String get() = TYPE

        companion object {
            const val TYPE: String = "webrtc_offer"
        }
    }

    @Serializable
    data class LocalIce(
        val gesprek: String,
        val kandidaat: IceCandidatePayload,
    ) : LauncherMessage {
        override val type: String get() = TYPE

        companion object {
            const val TYPE: String = "ice"
        }
    }
}

@Serializable
enum class ActionStatus {
    @SerialName("ok")
    OK,

    @SerialName("geweigerd")
    REFUSED,

    @SerialName("niet_beschikbaar")
    UNAVAILABLE,

    @SerialName("fout")
    FAILED,
}
