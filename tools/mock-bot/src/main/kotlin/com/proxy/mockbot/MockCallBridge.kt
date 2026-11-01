package com.proxy.mockbot

import com.proxy.protocol.CallMessage
import com.proxy.protocol.CallState
import com.proxy.protocol.DisconnectReason
import com.proxy.protocol.IceCandidatePayload
import com.proxy.protocol.LauncherMessage
import java.util.Timer
import java.util.TimerTask

/**
 * Nagebootste bridge: ontvangt dial/hangup/offer/ice over het botkanaal, meldt
 * ringing → active en echoot de audio terug. Eén gesprek tegelijk (TB-6).
 */
class MockCallBridge(
    private val send: (CallMessage) -> Unit,
    private val log: (String) -> Unit,
    private val answerDelayMillis: Long = 2_000L,
    private val peerFactory: (EchoPeer.Listener) -> EchoPeer = { listener: EchoPeer.Listener -> EchoPeer(listener) },
) {
    private val timer: Timer = Timer("mock-bridge", true)
    private var callId: String? = null
    private var peer: EchoPeer? = null

    @Synchronized
    fun handle(message: LauncherMessage): Boolean =
        when (message) {
            is LauncherMessage.Dial -> {
                onDial(message)
                true
            }
            is LauncherMessage.Hangup -> {
                onHangup(message)
                true
            }
            is LauncherMessage.WebRtcOffer -> {
                onOffer(message)
                true
            }
            is LauncherMessage.LocalIce -> {
                if (message.gesprek == callId) peer?.addRemoteIce(message.kandidaat)
                true
            }
            is LauncherMessage.Utterance,
            is LauncherMessage.ActionRequest,
            is LauncherMessage.ConfirmResponse,
            is LauncherMessage.ActionResult,
            is com.proxy.protocol.BioBaseResponse,
            -> false
        }

    @Synchronized
    fun close() {
        peer?.close()
        peer = null
        callId = null
        timer.cancel()
    }

    private fun onDial(dial: LauncherMessage.Dial) {
        val active: String? = callId
        if (active != null) {
            send(CallMessage.Status(gesprek = dial.gesprek, status = CallState.DISCONNECTED, oorzaak = DisconnectReason.BUSY))
            return
        }
        callId = dial.gesprek
        log("   bridge: bel ${dial.nummer} (token ${dial.token.take(12)}…)")
        send(CallMessage.Status(gesprek = dial.gesprek, status = CallState.RINGING))
        timer.schedule(
            object : TimerTask() {
                override fun run() {
                    synchronized(this@MockCallBridge) {
                        if (callId == dial.gesprek) {
                            log("   bridge: opgenomen")
                            send(CallMessage.Status(gesprek = dial.gesprek, status = CallState.ACTIVE))
                        }
                    }
                }
            },
            answerDelayMillis,
        )
    }

    private fun onOffer(offer: LauncherMessage.WebRtcOffer) {
        if (offer.gesprek != callId) return
        val current: EchoPeer =
            peer ?: peerFactory(
                object : EchoPeer.Listener {
                    override fun onAnswer(sdp: String) {
                        send(CallMessage.WebRtcAnswer(gesprek = offer.gesprek, sdp = sdp))
                    }

                    override fun onLocalIce(candidate: IceCandidatePayload) {
                        send(CallMessage.Ice(gesprek = offer.gesprek, kandidaat = candidate))
                    }

                    override fun onFailure(reason: String) {
                        log("   bridge: webrtc-fout $reason")
                    }

                    override fun onConnectionState(state: String) {
                        log("   bridge: audioverbinding $state")
                    }

                    override fun onFirstAudio() {
                        log("   bridge: audio van de telefoon ontvangen — echo loopt")
                    }
                },
            ).also { created: EchoPeer -> peer = created }
        current.accept(offer.sdp)
    }

    private fun onHangup(hangup: LauncherMessage.Hangup) {
        if (hangup.gesprek != callId) return
        log("   bridge: opgehangen door de telefoon")
        peer?.close()
        peer = null
        callId = null
    }
}
