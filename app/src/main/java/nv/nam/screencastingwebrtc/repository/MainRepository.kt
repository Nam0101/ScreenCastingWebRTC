package nv.nam.screencastingwebrtc.repository

import android.content.Intent
import android.util.Log
import com.google.gson.Gson
import nv.nam.screencastingwebrtc.socket.ClientSocket
import nv.nam.screencastingwebrtc.utils.DataModel
import nv.nam.screencastingwebrtc.utils.DataModelType
import nv.nam.screencastingwebrtc.webrtc.PeerObserver
import nv.nam.screencastingwebrtc.webrtc.WebrtcClient
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer

/**
 * @author Nam Nguyen Van
 * Project: ScreenCastingWebRTC
 * Created: 11/7/2024
 * Github: https://github.com/Nam0101
 * @description : MainRepository is the class to handle the main repository it about socket and webrtc client
 */
class MainRepository(
    private val socketClient: ClientSocket,
    private val webrtcClient: WebrtcClient,
    private val gson: Gson
) : ClientSocket.Listener, WebrtcClient.Listener {
    init {
        Log.i("MainRepository", "init: ")
    }
    private lateinit var streamId: String
    private val target: String = "viewer"
    private lateinit var surfaceView: SurfaceViewRenderer
    var listener: Listener? = null

    fun init(streamId: String, surfaceView: SurfaceViewRenderer) {
        this.streamId = streamId
        this.surfaceView = surfaceView
        initSocket()
        initWebrtcClient()

    }

    private fun initSocket() {
        Log.i("SOCKET", "initSocket: ")
        socketClient.listener = this
        socketClient.init(streamId)
    }

    fun setPermissionIntentToWebrtcClient(intent: Intent) {
        webrtcClient.setPermissionIntent(intent)
    }

    fun sendScreenShareConnection(target: String) {
        socketClient.sendMessageToSocket(
            DataModel(
                type = DataModelType.StartStreaming,
                streamId = streamId, data = null, target = target
            )
        )
    }

    fun startScreenCapturing(surfaceView: SurfaceViewRenderer) {
        webrtcClient.startScreenCapturing(surfaceView)
    }

    fun startCall(target: String) {
        webrtcClient.call(target)
    }

    fun sendCallEndedToOtherPeer() {
        socketClient.sendMessageToSocket(
            DataModel(
                type = DataModelType.EndCall, streamId = streamId, data = null, target = null
            )
        )
    }

    fun restartRepository() {
        webrtcClient.restart()
    }

    fun onDestroy() {
        socketClient.onDestroy()
        webrtcClient.closeConnection()
    }

    private fun initWebrtcClient() {
        webrtcClient.listener = this
        webrtcClient.initializeWebrtcClient(streamId, surfaceView, object : PeerObserver() {
            override fun onIceCandidate(p0: IceCandidate?) {
                super.onIceCandidate(p0)
                p0?.let { webrtcClient.sendIceCandidate(it, target) }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                super.onConnectionChange(newState)
                Log.d("TAG", "onConnectionChange: $newState")
                if (newState == PeerConnection.PeerConnectionState.CONNECTED) {
                    listener?.onConnectionConnected()
                }
            }

            override fun onAddStream(p0: MediaStream?) {
                super.onAddStream(p0)
                Log.d("TAG", "onAddStream: $p0")
                p0?.let { listener?.onRemoteStreamAdded(it) }
            }
        })
    }

    override fun onNewMessageReceived(model: DataModel) {
        Log.i("MainRepository", "onNewMessageReceived: $model")
        when (model.type) {
            DataModelType.StartStreaming -> {
                model.streamId?.let { listener?.onConnectionRequestReceived(it) }
            }

            DataModelType.EndCall -> {
                listener?.onCallEndReceived()
            }

            DataModelType.Offer -> {
                webrtcClient.onRemoteSessionReceived(
                    SessionDescription(
                        SessionDescription.Type.OFFER, model.data.toString()
                    )
                )
//                this.target = model.streamId.toString()
                webrtcClient.answer(target)
            }

            DataModelType.Answer -> {
                webrtcClient.onRemoteSessionReceived(
                    SessionDescription(SessionDescription.Type.ANSWER, model.data.toString())
                )

            }

            DataModelType.IceCandidates -> {
                val candidate = try {
                    gson.fromJson(model.data.toString(), IceCandidate::class.java)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
                candidate?.let {
                    webrtcClient.addIceCandidate(it)
                }
            }
            DataModelType.ViewerJoined -> {
                Log.i("MainRepository", "onNewMessageReceived: ViewerJoined")
                val viewerId = model.target.toString()
                webrtcClient.call(viewerId)
            }

            else -> Unit
        }
    }

    override fun onTransferEventToSocket(data: DataModel) {
        socketClient.sendMessageToSocket(data)
    }

    interface Listener {
        fun onConnectionRequestReceived(target: String)
        fun onConnectionConnected()
        fun onCallEndReceived()
        fun onRemoteStreamAdded(stream: MediaStream)
    }
}