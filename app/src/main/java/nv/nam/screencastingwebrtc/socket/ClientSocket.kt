package nv.nam.screencastingwebrtc.socket

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nv.nam.screencastingwebrtc.BuildConfig
import nv.nam.screencastingwebrtc.utils.DataModel
import nv.nam.screencastingwebrtc.utils.DataModelType
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI

class ClientSocket(private val gson: Gson) {
    private var streamId: String? = null
    private var webSocket: WebSocketClient? = null

    init {
        Log.i("ClientSocket", "init: ")
    }

    var listener: Listener? = null

    fun init(streamID: String) {
        this.streamId = streamID
        connectWebSocket()
    }

    private fun connectWebSocket() {
        webSocket = object : WebSocketClient(URI(BuildConfig.SERVER_IP)) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                Log.i("ClientSocket", "WebSocket Connected")
                sendMessageToSocket(DataModel(streamId = streamId, type = DataModelType.SignIn, data = null))
            }

            override fun onMessage(message: String?) {
                message?.let {
                    gson.fromJson(it, DataModel::class.java)?.also { model ->
                        listener?.onNewMessageReceived(model)
                    }
                }
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                Log.i("ClientSocket", "WebSocket Closed, reason: $reason")
                attemptReconnect()
            }

            override fun onError(ex: Exception?) {
                Log.e("ClientSocket", "WebSocket Error: ${ex?.message}")
                attemptReconnect()
            }
        }.apply { connect() }
    }

    private fun attemptReconnect() {
        CoroutineScope(Dispatchers.IO).launch {
            delay(5000) // Wait for 5 seconds before attempting to reconnect
            connectWebSocket() // Attempt to reconnect
        }
    }

    fun sendMessageToSocket(message: Any?) {
        if (webSocket?.isOpen == true) {
            try {
                webSocket?.send(gson.toJson(message))
            } catch (e: Exception) {
                Log.e("ClientSocket", "Error sending message: ${e.message}")
            }
        } else {
            Log.w("ClientSocket", "WebSocket is not connected. Attempting to reconnect...")
            attemptReconnect()
        }
    }

    fun onDestroy() {
        webSocket?.close()
    }

    interface Listener {
        fun onNewMessageReceived(model: DataModel)
    }
}