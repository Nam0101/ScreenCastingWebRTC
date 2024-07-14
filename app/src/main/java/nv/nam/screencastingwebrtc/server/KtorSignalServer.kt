package nv.nam.screencastingwebrtc.server

import android.util.Log
import com.google.gson.Gson
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import nv.nam.screencastingwebrtc.utils.DataModel
import nv.nam.screencastingwebrtc.utils.DataModelType

class KtorSignalServer(private val port: Int = 3000) {
    private var server: ApplicationEngine? = null
    private val connections = mutableMapOf<String, DefaultWebSocketServerSession>()
    private val gson = Gson() // Use Gson for serialization/deserialization

    fun startServer() {
        server = embeddedServer(CIO, port = port) {
            install(WebSockets)
            Log.i("KtorSignalServer", "Server started on port $port")
            routing {
                webSocket("/") {
                    val thisConnection = this
                    Log.i("KtorSignalServer", "Connection opened from: ${thisConnection}")
                    val remoteAddress = call.request.local.remoteHost
                    Log.i("KtorSignalServer", "Remote address: $remoteAddress")
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            try {
                                val data = gson.fromJson(frame.readText(), DataModel::class.java)
                                Log.i("KtorSignalServer", "Message received: $data")
                                Log.i("KtorSignalServer", "send to connection: $thisConnection with remoteAddress: $remoteAddress")
                                handleMessage(thisConnection, data)
                            } catch (e: Exception) {
                                println("Error parsing message: ${e.message}")
                            }
                        }
                    }
                }
            }
        }.start(wait = false)
    }

    private suspend fun handleMessage(connection: DefaultWebSocketServerSession, data: DataModel) {
        Log.i("KtorSignalServer", "Handling message: $data")
        when (data.type) {
            DataModelType.SignIn -> {
                if (data.streamId == "123456") {
                    connections["123456"] = connection
                    Log.i("KtorSignalServer", "Streamer signed in with StreamID: 123456")
                } else {
                    connections[data.streamId!!] = connection
                    Log.i("KtorSignalServer", "Viewer ${data.streamId} signed in")
                }
            }
            DataModelType.Offer -> {
                connections["viewer-1"]?.let {
                    sendMessage(it, data)
                    Log.i("KtorSignalServer", "Offer sent to viewer")
                }
            }
            DataModelType.Answer -> {
                connections["123456"]?.let {
                    sendMessage(it, data)
                    Log.i("KtorSignalServer", "Answer sent to streamer")
                }
            }
            DataModelType.IceCandidates -> {
                if (data.streamId == "123456") {
                    connections["viewer-1"]?.let {
                        sendMessage(it, data)
                        Log.i("KtorSignalServer", "ICE Candidate sent to viewer")
                    }
                } else {
                    connections["123456"]?.let {
                        sendMessage(it, data)
                        Log.i("KtorSignalServer", "ICE Candidate sent to streamer")
                    }
                }
            }
            DataModelType.StartStreaming -> {
                connections["viewer-1"]?.let {
                    sendMessage(it, data)
                    Log.i("KtorSignalServer", "StartStreaming message sent to viewer")
                }
            }
            DataModelType.WatchStream -> {
                connections["123456"]?.let {
                    Log.i("KtorSignalServer", "This connection: $connection")
                    sendMessage(it, DataModel(type = DataModelType.ViewerJoined, streamId = "123456", target = "viewer-1"))
                    Log.i("KtorSignalServer", "ViewerJoined message sent to streamer")
                }
            }
            else -> Log.i("KtorSignalServer", "Unhandled message type: ${data.type}")
        }
    }

    private suspend fun sendMessage(connection: DefaultWebSocketServerSession, message: DataModel) {
        Log.i("KtorSignalServer", "Sending message: $message")
        val jsonMessage = gson.toJson(message)
        connection.send(Frame.Text(jsonMessage))
        Log.i("KtorSignalServer", "Message sent: $message")
    }
}