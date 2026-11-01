package com.proxy.mockbot

import com.proxy.protocol.IceCandidatePayload
import dev.onvoid.webrtc.CreateSessionDescriptionObserver
import dev.onvoid.webrtc.PeerConnectionFactory
import dev.onvoid.webrtc.PeerConnectionObserver
import dev.onvoid.webrtc.RTCAnswerOptions
import dev.onvoid.webrtc.RTCConfiguration
import dev.onvoid.webrtc.RTCIceCandidate
import dev.onvoid.webrtc.RTCPeerConnection
import dev.onvoid.webrtc.RTCRtpSender
import dev.onvoid.webrtc.RTCRtpTransceiver
import dev.onvoid.webrtc.RTCSdpType
import dev.onvoid.webrtc.RTCSessionDescription
import dev.onvoid.webrtc.SetSessionDescriptionObserver
import dev.onvoid.webrtc.media.MediaStreamTrack
import dev.onvoid.webrtc.media.audio.AudioTrack
import dev.onvoid.webrtc.media.audio.AudioTrackSink
import dev.onvoid.webrtc.media.audio.CustomAudioSource
import dev.onvoid.webrtc.media.audio.HeadlessAudioDeviceModule

/** Eén gedeelde WebRTC-factory zonder geluidskaart (de mock draait headless). */
object MockWebRtc {
    val factory: PeerConnectionFactory by lazy { PeerConnectionFactory(HeadlessAudioDeviceModule()) }
}

/**
 * Bootst het audio-been van de bridge na: beantwoordt het offer van de telefoon
 * en stuurt de ontvangen stem direct terug (echo).
 */
class EchoPeer(
    private val listener: Listener,
    factory: PeerConnectionFactory = MockWebRtc.factory,
) {
    interface Listener {
        fun onAnswer(sdp: String)

        fun onLocalIce(candidate: IceCandidatePayload)

        fun onFailure(reason: String)

        /** Verbindingsstatus van het audio-been, voor de log. */
        fun onConnectionState(state: String) {}

        /** Eerste audioframe van de telefoon ontvangen: de echo loopt. */
        fun onFirstAudio() {}
    }

    private val source: CustomAudioSource = CustomAudioSource()
    private val track: AudioTrack = factory.createAudioTrack("echo", source)
    @Volatile
    private var heardAudio: Boolean = false

    private val echo: AudioTrackSink =
        AudioTrackSink { data: ByteArray, bitsPerSample: Int, sampleRate: Int, channels: Int, frames: Int ->
            if (!heardAudio) {
                heardAudio = true
                listener.onFirstAudio()
            }
            source.pushAudio(data, bitsPerSample, sampleRate, channels, frames)
        }
    private var remoteTrack: AudioTrack? = null

    private val connection: RTCPeerConnection =
        factory.createPeerConnection(
            RTCConfiguration(),
            object : PeerConnectionObserver {
                override fun onIceCandidate(candidate: RTCIceCandidate) {
                    listener.onLocalIce(IceCandidatePayload(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp))
                }

                override fun onConnectionChange(state: dev.onvoid.webrtc.RTCPeerConnectionState) {
                    listener.onConnectionState(state.name)
                }

                override fun onTrack(transceiver: RTCRtpTransceiver) {
                    val incoming: MediaStreamTrack = transceiver.receiver.track
                    if (incoming is AudioTrack && remoteTrack == null) {
                        remoteTrack = incoming
                        incoming.addSink(echo)
                    }
                }
            },
        )

    private val sender: RTCRtpSender = connection.addTrack(track, listOf("echo"))

    /** Verwerkt een (her)offer, ook na een ICE-restart (TB-8). */
    fun accept(offerSdp: String) {
        connection.setRemoteDescription(
            RTCSessionDescription(RTCSdpType.OFFER, offerSdp),
            setObserver {
                connection.createAnswer(
                    RTCAnswerOptions(),
                    object : CreateSessionDescriptionObserver {
                        override fun onSuccess(description: RTCSessionDescription) {
                            connection.setLocalDescription(description, setObserver { listener.onAnswer(description.sdp) })
                        }

                        override fun onFailure(error: String) {
                            listener.onFailure("answer: $error")
                        }
                    },
                )
            },
        )
    }

    fun addRemoteIce(candidate: IceCandidatePayload) {
        connection.addIceCandidate(RTCIceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.candidate))
    }

    fun close() {
        remoteTrack?.removeSink(echo)
        connection.removeTrack(sender)
        connection.close()
    }

    private fun setObserver(onSuccess: () -> Unit): SetSessionDescriptionObserver =
        object : SetSessionDescriptionObserver {
            override fun onSuccess() {
                onSuccess()
            }

            override fun onFailure(error: String) {
                listener.onFailure(error)
            }
        }
}
