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
import kotlinx.coroutines.isActive
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
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            try {
                                val data = gson.fromJson(frame.readText(), DataModel::class.java)
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
                val connectionKey = data.streamId.toString()
                connections[connectionKey] = connection
                Log.i(
                    "KtorSignalServer",
                    "Connection added: $connectionKey - Total Connections: ${connections.size}"
                ) // Check total connections

            }

            DataModelType.Offer, DataModelType.Answer,
            DataModelType.IceCandidates -> {
                val targetStreamId = if (data.streamId == "123456") "viewer-1" else "123456"
                connections[targetStreamId]?.let { targetConnection ->
                    sendMessage(targetConnection, data)
                    Log.i("KtorSignalServer", "${data.type} sent to $targetStreamId")
                } ?: Log.w("KtorSignalServer", "Target connection not found: $targetStreamId")
            }
            DataModelType.StartStreaming -> {
                // send to viewer
                val targetStreamId = if (data.streamId == "123456") "viewer-1" else "123456"
                connections[targetStreamId]?.let {
                    sendMessage(it, DataModel(type = DataModelType.StreamStarted, streamId = "123456"))
                }
                Log.i("KtorSignalServer", "StartStreaming message broadcasted")
            }
            DataModelType.WatchStream -> {
                val targetStreamId = if (data.streamId == "123456") "viewer-1" else "123456"
                connections[targetStreamId]?.let {
                    Log.i("KtorSignalServer", "This connection: $connection")
                    sendMessage(it, DataModel(type = DataModelType.ViewerJoined, streamId = "123456", target = "viewer-1"))
                    Log.i("KtorSignalServer", "ViewerJoined message sent to streamer")
                }
            }
            else -> Log.i("KtorSignalServer", "Unhandled message type: ${data.type}")
        }
    }

    private suspend fun sendMessage(connection: DefaultWebSocketServerSession, message: DataModel) {
        try {
            if (connection.isActive) {
                val jsonMessage = gson.toJson(message)
                connection.send(Frame.Text(jsonMessage))
                Log.i("KtorSignalServer", "Message sent: $message to connection: $connection")
            } else {
                Log.w("KtorSignalServer", "Attempted to send to inactive connection: $connection")
            }
        } catch (e: Exception) {
            Log.e(
                "KtorSignalServer", "Error sending message: $message to $connection - ${e.message}"
            )
        }
    }

    private suspend fun broadcastMessage(message: DataModel) {
        connections.values.forEach { session ->
            if (session.isActive) {
                sendMessage(session, message)
            } else {
                Log.i("KtorSignalServer", "Skipping inactive session: $session")
            }
        }
    }
}