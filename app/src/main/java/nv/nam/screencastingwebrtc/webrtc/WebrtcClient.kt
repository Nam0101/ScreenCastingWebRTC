package nv.nam.screencastingwebrtc.webrtc

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nv.nam.screencastingwebrtc.utils.DataModel
import nv.nam.screencastingwebrtc.utils.DataModelType
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.Observer
import org.webrtc.PeerConnectionFactory
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

/**
 * @author Nam Nguyen Van
 * Project: ScreenCastingWebRTC
 * Created: 11/7/2024
 * Github: https://github.com/Nam0101
 * @description : WebrtcClient is the class to handle the webrtc client
 */
class WebrtcClient(
    private val context: Context, private val gson: Gson
) {
    private lateinit var streamId: String
    private lateinit var observer: Observer
    private lateinit var localSurfaceView: SurfaceViewRenderer
    var listener: Listener? = null
    private var permissionIntent: Intent? = null

    private var peerConnection: PeerConnection? = null
    private val eglBaseContext = EglBase.create().eglBaseContext
    private val peerConnectionFactory by lazy { createPeerConnectionFactory() }
    private val mediaConstraint = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
        optional.add(MediaConstraints.KeyValuePair("videoCodec", "AV1"))
        optional.add(MediaConstraints.KeyValuePair("videoCodec", "VP9"))
        optional.add(MediaConstraints.KeyValuePair("videoCodec", "H264"))
    }
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var isRecording = false

    private val iceServer = listOf(
        PeerConnection.IceServer(
            "turn:openrelay.metered.ca:443?transport=udp", "openrelayproject", "openrelayproject"
        )
    )

    private var screenCapturer: VideoCapturer? = null
    private lateinit var localVideoSource: VideoSource
    private val localTrackId = "local_track"
    private val localStreamId = "local_stream"
    private val audioTrackId = "audio_track"
    private var localVideoTrack: VideoTrack? = null
    private var localStream: MediaStream? = null

    init {
        initPeerConnectionFactory(context)
    }

    fun initializeWebrtcClient(
        streamId: String, view: SurfaceViewRenderer, observer: Observer
    ) {
        this.streamId = streamId
        this.observer = observer
        peerConnection = createPeerConnection(observer)
        initSurfaceView(view)
    }

    fun setPermissionIntent(intent: Intent) {
        this.permissionIntent = intent
    }

    private fun initSurfaceView(view: SurfaceViewRenderer) {
        this.localSurfaceView = view
        view.run {
            setMirror(false)
            setEnableHardwareScaler(true)
            init(eglBaseContext, null)
        }
    }

    fun startScreenCapturing(view: SurfaceViewRenderer) {
        val displayMetrics = DisplayMetrics()
        val windowsManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowsManager.defaultDisplay.getMetrics(displayMetrics)

        val screenWidthPixels = displayMetrics.widthPixels
        val screenHeightPixels = displayMetrics.heightPixels
        val scalingFactor = 0.8
        val reducedWidth = (screenWidthPixels * scalingFactor).toInt()
        val reducedHeight = (screenHeightPixels * scalingFactor).toInt()

        val surfaceTextureHelper = SurfaceTextureHelper.create(
            Thread.currentThread().name, eglBaseContext
        )
        Log.i("TAG", "startScreenCapturing:  height: $screenHeightPixels width: $screenWidthPixels")

        screenCapturer = createScreenCapturer()

        localVideoSource = peerConnectionFactory.createVideoSource(screenCapturer!!.isScreencast)
        screenCapturer!!.initialize(
            surfaceTextureHelper, context, localVideoSource.capturerObserver
        )
//        screenCapturer!!.startCapture(screenWidthPixels, screenHeightPixels, 24)
        screenCapturer!!.startCapture(reducedWidth, reducedHeight, 24)

        localVideoTrack =
            peerConnectionFactory.createVideoTrack(localTrackId + "_video", localVideoSource)
        Log.d("TAG", "startScreenCapturing: $localVideoTrack")
        localVideoTrack?.addSink(view)
        localStream = peerConnectionFactory.createLocalMediaStream(localStreamId)
        localStream?.addTrack(localVideoTrack)

        startAudioRecording()

        peerConnection?.addStream(localStream)


        peerConnection?.setBitrate(300000, 1000000, 2500000)
        peerConnection?.setAudioRecording(true)
//        peerConnection?.setAudioPlayout(true)
//        peerConnection?.startRtcEventLog(100, 100)
    }


    @SuppressLint("MissingPermission")
    private fun startAudioRecording() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        val minBufferSize = AudioRecord.getMinBufferSize(
            22050, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_8BIT
        )
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.DEFAULT,
            22050,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_8BIT,
            minBufferSize
        )

        audioSource = peerConnectionFactory.createAudioSource(mediaConstraint)
        localAudioTrack = peerConnectionFactory.createAudioTrack(audioTrackId, audioSource)

        isRecording = true
        CoroutineScope(Dispatchers.Default).launch {
            audioRecord.startRecording()
//            audioRecord.stop()
//            audioRecord.release()
        }
        localStream?.addTrack(localAudioTrack)
    }


    private fun createScreenCapturer(): VideoCapturer {
        return ScreenCapturerAndroid(permissionIntent, object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                Log.d("TAG", "onStop: stopped screen casting permission")
            }
        })
    }

    private fun initPeerConnectionFactory(application: Context) {
        val options = PeerConnectionFactory.InitializationOptions.builder(application)
            .setEnableInternalTracer(true).setFieldTrials("WebRTC-SupportAV1/Enabled/")
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
    }

    private fun createPeerConnectionFactory(): PeerConnectionFactory {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-SupportAV1/Enabled/")
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        return PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBaseContext, true, true))
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = false
                disableNetworkMonitor = false
            }).createPeerConnectionFactory()
    }


    private fun createPeerConnection(observer: Observer): PeerConnection? {
        return peerConnectionFactory.createPeerConnection(
            iceServer, observer
        )
    }

    fun call(target: String) {
        peerConnection?.createOffer(object : SdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                super.onCreateSuccess(desc)
                peerConnection?.setLocalDescription(object : SdpObserver() {
                    override fun onSetSuccess() {
                        super.onSetSuccess()
                        listener?.onTransferEventToSocket(
                            DataModel(
                                type = DataModelType.Offer,
                                streamId = streamId,
                                target = target,
                                data = desc?.description
                            )
                        )
                    }
                }, desc)
            }
        }, mediaConstraint)
    }

    fun answer(target: String) {
        peerConnection?.createAnswer(object : SdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                super.onCreateSuccess(desc)
                peerConnection?.setLocalDescription(object : SdpObserver() {
                    override fun onSetSuccess() {
                        super.onSetSuccess()
                        listener?.onTransferEventToSocket(
                            DataModel(
                                type = DataModelType.Answer,
                                streamId = streamId,
                                target = target,
                                data = desc?.description
                            )
                        )
                    }
                }, desc)
            }
        }, mediaConstraint)
    }

    fun onRemoteSessionReceived(sessionDescription: SessionDescription) {
        peerConnection?.setRemoteDescription(SdpObserver(), sessionDescription)
    }

    fun addIceCandidate(iceCandidate: IceCandidate) {
        peerConnection?.addIceCandidate(iceCandidate)
    }

    fun sendIceCandidate(candidate: IceCandidate, target: String) {
        addIceCandidate(candidate)
        listener?.onTransferEventToSocket(
            DataModel(
                type = DataModelType.IceCandidates,
                streamId = streamId,
                target = target,
                data = gson.toJson(candidate)
            )
        )
    }

    fun closeConnection() {
        try {
            isRecording = false
            screenCapturer?.stopCapture()
            screenCapturer?.dispose()
            localVideoSource.dispose()
            localStream?.dispose()
            peerConnection?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun restart() {
        closeConnection()
        localSurfaceView.let {
            it.clearImage()
            it.release()
            initializeWebrtcClient(streamId, it, observer)
        }
    }

    interface Listener {
        fun onTransferEventToSocket(data: DataModel)
    }
}