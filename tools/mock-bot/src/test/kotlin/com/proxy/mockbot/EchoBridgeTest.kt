package com.proxy.mockbot

import com.proxy.protocol.CallMessage
import com.proxy.protocol.CallState
import com.proxy.protocol.DisconnectReason
import com.proxy.protocol.IceCandidatePayload
import com.proxy.protocol.LauncherMessage
import dev.onvoid.webrtc.CreateSessionDescriptionObserver
import dev.onvoid.webrtc.PeerConnectionObserver
import dev.onvoid.webrtc.RTCConfiguration
import dev.onvoid.webrtc.RTCIceCandidate
import dev.onvoid.webrtc.RTCOfferOptions
import dev.onvoid.webrtc.RTCPeerConnection
import dev.onvoid.webrtc.RTCRtpTransceiver
import dev.onvoid.webrtc.RTCSdpType
import dev.onvoid.webrtc.RTCSessionDescription
import dev.onvoid.webrtc.SetSessionDescriptionObserver
import dev.onvoid.webrtc.media.audio.AudioTrack
import dev.onvoid.webrtc.media.audio.AudioTrackSink
import dev.onvoid.webrtc.media.audio.CustomAudioSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class EchoBridgeTest {
    @Test
    fun `dial reports ringing, then active, and a second dial is busy`() {
        val sent: CopyOnWriteArrayList<CallMessage> = CopyOnWriteArrayList()
        val bridge: MockCallBridge = MockCallBridge(send = { message: CallMessage -> sent.add(message) }, log = {}, answerDelayMillis = 50L)
        bridge.handle(LauncherMessage.Dial(gesprek = "c1", nummer = "+31612345678", token = "hash"))
        bridge.handle(LauncherMessage.Dial(gesprek = "c2", nummer = "+31612345678", token = "hash"))
        Thread.sleep(300)
        val statuses: List<CallMessage.Status> = sent.filterIsInstance<CallMessage.Status>()
        assertEquals(CallMessage.Status("c1", CallState.RINGING), statuses[0])
        assertEquals(CallMessage.Status("c2", CallState.DISCONNECTED, DisconnectReason.BUSY), statuses[1])
        assertEquals(CallMessage.Status("c1", CallState.ACTIVE), statuses[2])
        bridge.close()
    }

    @Test
    fun `echo peer sends the caller's audio back`() {
        val factory = MockWebRtc.factory
        val sent: CopyOnWriteArrayList<CallMessage> = CopyOnWriteArrayList()
        val answered: CountDownLatch = CountDownLatch(1)
        lateinit var phone: RTCPeerConnection
        val bridge: MockCallBridge =
            MockCallBridge(
                send = { message: CallMessage ->
                    sent.add(message)
                    when (message) {
                        is CallMessage.WebRtcAnswer ->
                            phone.setRemoteDescription(
                                RTCSessionDescription(RTCSdpType.ANSWER, message.sdp),
                                noopSet { answered.countDown() },
                            )
                        is CallMessage.Ice ->
                            phone.addIceCandidate(
                                RTCIceCandidate(message.kandidaat.sdpMid, message.kandidaat.sdpMLineIndex, message.kandidaat.candidate),
                            )
                        is CallMessage.Status -> {}
                    }
                },
                log = {},
                answerDelayMillis = 10L,
            )

        // De "telefoon": een tweede peer die een toon uitzendt en meet wat terugkomt.
        val receivedEnergy: AtomicLong = AtomicLong(0L)
        val sink: AudioTrackSink =
            AudioTrackSink { data: ByteArray, _: Int, _: Int, _: Int, _: Int ->
                var sum: Long = 0L
                var index: Int = 0
                while (index + 1 < data.size) {
                    val sample: Int = (data[index].toInt() and 0xFF) or (data[index + 1].toInt() shl 8)
                    sum += abs(sample.toShort().toInt())
                    index += 2
                }
                receivedEnergy.addAndGet(sum)
            }
        val tone: CustomAudioSource = CustomAudioSource()
        phone =
            factory.createPeerConnection(
                RTCConfiguration(),
                object : PeerConnectionObserver {
                    override fun onIceCandidate(candidate: RTCIceCandidate) {
                        bridge.handle(
                            LauncherMessage.LocalIce("c1", IceCandidatePayload(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)),
                        )
                    }

                    override fun onTrack(transceiver: RTCRtpTransceiver) {
                        (transceiver.receiver.track as? AudioTrack)?.addSink(sink)
                    }
                },
            )
        phone.addTrack(factory.createAudioTrack("tone", tone), listOf("phone"))

        bridge.handle(LauncherMessage.Dial(gesprek = "c1", nummer = "+31612345678", token = "hash"))
        val offerReady: CountDownLatch = CountDownLatch(1)
        phone.createOffer(
            RTCOfferOptions(),
            object : CreateSessionDescriptionObserver {
                override fun onSuccess(description: RTCSessionDescription) {
                    phone.setLocalDescription(
                        description,
                        noopSet {
                            bridge.handle(LauncherMessage.WebRtcOffer("c1", description.sdp))
                            offerReady.countDown()
                        },
                    )
                }

                override fun onFailure(error: String) {}
            },
        )
        assertTrue(offerReady.await(5, TimeUnit.SECONDS))
        assertTrue(answered.await(5, TimeUnit.SECONDS))

        // 440 Hz, 10 ms-frames, 48 kHz mono 16-bit, maximaal 8 s.
        val frame: ByteArray = ByteArray(960)
        var phase: Double = 0.0
        val deadline: Long = System.currentTimeMillis() + 8_000L
        while (System.currentTimeMillis() < deadline && receivedEnergy.get() < 1_000_000L) {
            for (sampleIndex: Int in 0 until 480) {
                val value: Int = (sin(phase) * 8_000).toInt()
                phase += 2 * PI * 440 / 48_000
                frame[sampleIndex * 2] = (value and 0xFF).toByte()
                frame[sampleIndex * 2 + 1] = ((value shr 8) and 0xFF).toByte()
            }
            tone.pushAudio(frame, 16, 48_000, 1, 480)
            Thread.sleep(10)
        }
        val energy: Long = receivedEnergy.get()
        bridge.handle(LauncherMessage.Hangup("c1"))
        bridge.close()
        phone.close()
        assertTrue("geen echo ontvangen (energie $energy)", energy >= 1_000_000L)
    }

    private fun noopSet(onSuccess: () -> Unit): SetSessionDescriptionObserver =
        object : SetSessionDescriptionObserver {
            override fun onSuccess() {
                onSuccess()
            }

            override fun onFailure(error: String) {}
        }
}
