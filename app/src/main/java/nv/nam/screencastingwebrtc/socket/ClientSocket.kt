package nv.nam.screencastingwebrtc.socket

import android.content.Context
import android.net.wifi.WifiManager
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

/**
 * @author Nam Nguyen Van
 * Project: ScreenCastingWebRTC
 * Created: 11/7/2024
 * Github: https://github.com/Nam0101
 * @description : ClientSocket is the class to handle the socket connection
 */
class ClientSocket(
    private val gson: Gson, private val wifiManager: WifiManager
) {
    private var streamId: String? = null
    companion object {
        private var webSocket: WebSocketClient? = null
    }
    private fun getIPAddress(): String {
        val ipAddress = wifiManager.connectionInfo.ipAddress
        return android.text.format.Formatter.formatIpAddress(ipAddress)
    }

    init {
        Log.i("ClientSocket", "init: ")
    }

    var listener: Listener? = null
    fun init(streamID: String) {
        this.streamId = streamID
        val serverIP = getIPAddress()
        val server = "ws://$serverIP:3000"
        Log.i("ClientSocket", "init: $server")
        webSocket = object : WebSocketClient(URI(server)) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                sendMessageToSocket(
                    DataModel(
                        streamId = streamID, DataModelType.SignIn, null
                    )
                )
            }

            override fun onMessage(message: String?) {
                val model = try {
                    Log.i("ClientSocket", "onMessage: $message")
                    gson.fromJson(message.toString(), DataModel::class.java)
                } catch (e: Exception) {
                    Log.i("TAG", "onMessage: ${e.message}")
                    null
                }
                model?.let {
                    listener?.onNewMessageReceived(it)
                }
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                CoroutineScope(Dispatchers.IO).launch {
                    delay(5000)
                    init(streamID)
                }
            }

            override fun onError(ex: Exception?) {
            }
        }
        webSocket?.connect()
    }


    fun sendMessageToSocket(message: Any?) {
        try {
            webSocket?.send(gson.toJson(message))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun onDestroy() {
        webSocket?.close()
    }

    interface Listener {
        fun onNewMessageReceived(model: DataModel)
    }
}