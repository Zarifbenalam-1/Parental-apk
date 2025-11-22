package com.project.raven

import android.content.Context
import android.util.Log
import org.webrtc.*

class WebRTCManager(private val context: Context, private val onIceCandidateFound: (IceCandidate) -> Unit) {

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var videoCapturer: VideoCapturer? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private val eglBase: EglBase = EglBase.create()

    private var activePeerConnection: PeerConnection? = null

    init {
        initializePeerConnectionFactory()
    }

    private fun initializePeerConnectionFactory() {
        // 1. Initialize the WebRTC options
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        // 2. Create the Factory
        val options2 = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options2)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
    }

    fun startCapture(): Boolean {
        Log.d("RavenRTC", "Seizing Camera Hardware...")
        
        try {
            // 1. Create Audio Source
            val audioSource = peerConnectionFactory?.createAudioSource(MediaConstraints())
            localAudioTrack = peerConnectionFactory?.createAudioTrack("ARDAMSa0", audioSource)

            // 2. Create Video Capturer (Front Camera by default)
            videoCapturer = createCameraCapturer(Camera2Enumerator(context))

            if (videoCapturer == null) {
                Log.e("RavenRTC", "No camera device found.")
                return false
            }

            // 3. Create Video Source & Track
            val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
            val videoSource = peerConnectionFactory?.createVideoSource(videoCapturer!!.isScreencast)
            
            videoCapturer?.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)
            // Capture 480p at 15fps to save bandwidth/battery
            videoCapturer?.startCapture(640, 480, 15)

            localVideoTrack = peerConnectionFactory?.createVideoTrack("ARDAMSv0", videoSource)
            Log.d("RavenRTC", "Camera & Mic Acquired.")
            return true
        } catch (e: Exception) {
            Log.e("RavenRTC", "CRITICAL: Camera seizure failed: ${e.message}")
            return false
        }
    }

    fun stopCapture() {
        try {
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            localVideoTrack?.dispose()
            localAudioTrack?.dispose()
            peerConnectionFactory?.dispose()
            Log.d("RavenRTC", "Hardware released.")
        } catch (e: Exception) {
            Log.e("RavenRTC", "Error stopping capture: ${e.message}")
        }
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames
        // Try to find front facing camera
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) return capturer
            }
        }
        // Fallback to back facing
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) return capturer
            }
        }
        return null
    }

    // CHUNK 2: The Handshake (SDP Offer)
    fun createOffer(onOfferCreated: (SessionDescription) -> Unit) {
        val constraints = MediaConstraints()
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))

        activePeerConnection = peerConnectionFactory?.createPeerConnection(
            listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()),
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate?) {
                    // CRITICAL: Send this candidate to the server!
                    // The server needs this to find a path back to us.
                    candidate?.let { onIceCandidateGenerated(it) }
                }
                override fun onDataChannel(p0: DataChannel?) {}
                override fun onIceConnectionReceivingChange(p0: Boolean) {}
                override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
                override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
                override fun onAddStream(p0: MediaStream?) {}
                override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
                override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
                override fun onRemoveStream(p0: MediaStream?) {}
                override fun onRenegotiationNeeded() {}
            }
        )

        activePeerConnection = peerConnection

        // Add our local tracks (Camera/Mic) to the connection
        if (localVideoTrack != null && localAudioTrack != null) {
            activePeerConnection?.addTrack(localVideoTrack, listOf("ARDAMS"))
            activePeerConnection?.addTrack(localAudioTrack, listOf("ARDAMS"))
        }

        // Create the Offer
        activePeerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    activePeerConnection?.setLocalDescription(this, it)
                    onOfferCreated(it) // Callback to RavenService to upload JSON
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) { Log.e("RavenRTC", "SDP Create Failed: $p0") }
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    // CHUNK 3: The Answer (Finalizing Connection)
    fun setRemoteDescription(sdpString: String) {
        val sdp = SessionDescription(SessionDescription.Type.ANSWER, sdpString)
        
        // We need to access the peerConnection we created earlier.
        // In a production app, we'd store this in a variable.
        // For this implementation, we assume the peerConnection is still alive in memory.
        // NOTE: This requires 'peerConnection' to be a class-level variable, not local to createOffer.
        
        // FIX: Promoting peerConnection to class level variable
        activePeerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                Log.d("RavenRTC", "Remote Description Set. Connection Established!")
            }
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) { Log.e("RavenRTC", "Remote Set Failed: $p0") }
        }, sdp)
    }

    // CHUNK 4: The Network (ICE Candidates)
    fun addIceCandidate(sdpMid: String?, sdpMLineIndex: Int, sdp: String) {
        val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
        activePeerConnection?.addIceCandidate(candidate)
    }
    
    // Helper to send OUR candidates to the server
    private fun onIceCandidateGenerated(candidate: IceCandidate) {
        onIceCandidateFound(candidate)
    }
}
