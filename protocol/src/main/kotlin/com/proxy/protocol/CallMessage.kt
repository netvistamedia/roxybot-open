package com.proxy.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** ICE-kandidaat in de standaard WebRTC-velden. */
@Serializable
data class IceCandidatePayload(
    val sdpMid: String? = null,
    val sdpMLineIndex: Int,
    val candidate: String,
)

/**
 * Gespreksberichten van bot/bridge naar launcher. Dit zijn géén botacties (LS-9/LS-10):
 * de launcher accepteert ze alleen voor het lopende gesprek met een geldig token.
 */
sealed interface CallMessage {
    val type: String
    val gesprek: String

    @Serializable
    data class WebRtcAnswer(
        override val gesprek: String,
        val sdp: String,
    ) : CallMessage {
        override val type: String get() = TYPE

        companion object {
            const val TYPE: String = "webrtc_answer"
        }
    }

    @Serializable
    data class Ice(
        override val gesprek: String,
        val kandidaat: IceCandidatePayload,
    ) : CallMessage {
        override val type: String get() = TYPE

        companion object {
            const val TYPE: String = "ice"
        }
    }

    /** Gespreksstatus van het PSTN-been (telephony-bridge, stap 4 van de flow). */
    @Serializable
    data class Status(
        override val gesprek: String,
        val status: CallState,
        val oorzaak: DisconnectReason? = null,
    ) : CallMessage {
        override val type: String get() = TYPE

        companion object {
            const val TYPE: String = "call_status"
        }
    }

    companion object {
        val TYPES: List<String> = listOf(WebRtcAnswer.TYPE, Ice.TYPE, Status.TYPE)
    }
}

@Serializable
enum class CallState {
    @SerialName("ringing")
    RINGING,

    @SerialName("active")
    ACTIVE,

    @SerialName("disconnected")
    DISCONNECTED,
}

/** Benoemde oorzaken (telephony-bridge): bezet, geweigerd, netwerkfout, opgehangen. */
@Serializable
enum class DisconnectReason {
    @SerialName("busy")
    BUSY,

    @SerialName("rejected")
    REJECTED,

    @SerialName("network")
    NETWORK,

    @SerialName("hung_up")
    HUNG_UP,
}
