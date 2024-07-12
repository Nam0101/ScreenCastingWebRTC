package nv.nam.screencastingwebrtc.server

import android.util.Log
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SignalMessage(
    val type: String,
    val streamId: String? = null,
    val target: String? = null,
    val data: String? = null
)

@Serializable
data class ViewerJoinedMessage(val type: String, val streamId: String, val target: String)

@Serializable
data class StreamStartedMessage(val type: String, val streamId: String)

@Serializable
data class ErrorMessage(val type: String, val message: String)

/**
 * @author Nam Nguyen Van
 * Project: ScreenCastingWebRTC
 * Created: 12/7/2024
 * Github: https://github.com/Nam0101
 * @description :
 */
class KtorSignalServer(
    private val port: Int = 3000
) {
    private var server: ApplicationEngine? = null
    val connections = mutableMapOf<String, DefaultWebSocketServerSession>()
    fun startServer() {
        server = embeddedServer(CIO, port = port) {
            install(WebSockets)
            Log.i("KtorSignalServer", "Server started on port $port")
            routing {
                webSocket("/") {
                    val thisConnection = this
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            try {
                                val data = Json.decodeFromString<SignalMessage>(frame.readText())
                                handleMessage(thisConnection, data)
                            } catch (e: Exception) {
                                println("Error parsing message: ${e.message}")
                            }
                        }
                    }

                    val connectionId = connections.entries.find { it.value == thisConnection }?.key
                    if (connectionId != null) {
                        connections.remove(connectionId)
                        Log.i("KtorSignalServer", "Connection closed: $connectionId")
                    }
                }
            }
        }.start(wait = false)
    }

    private suspend fun handleMessage(
        connection: DefaultWebSocketServerSession, data: SignalMessage
    ) {
        when (data.type) {
            "SignIn" -> {
                val streamId = data.streamId
                if (streamId != null) {
                    connections[streamId] = connection
                }
            }

            "Offer", "Answer", "IceCandidates" -> {
                val targetConnection = data.target?.let { connections[it] }
                if (targetConnection != null) {
                    sendMessage(targetConnection, data)
                    Log.i("KtorSignalServer", "Message sent to: ${data.target}")
                } else {
                    Log.i("KtorSignalServer", "Connection not found: ${data.target}")
                }
            }

            "WatchStream" -> {
                val streamId = data.streamId
                val clientId = data.target
                val targetConnection = streamId?.let { connections[it] }
                if (targetConnection != null) {
                    sendMessage(
                        targetConnection,
                        ViewerJoinedMessage("ViewerJoined", streamId, clientId ?: "unknown")
                    )
                } else {
                    sendMessage(connection, ErrorMessage("Error", "Stream not found"))
                }
            }

            "StartStreaming" -> {
                val streamId = data.streamId
                if (streamId != null) {
                    for (clientId in connections.keys) {
                        if (clientId != streamId) {
                            val clientConnection = connections[clientId]
                            if (clientConnection != null) {
                                sendMessage(
                                    clientConnection,
                                    StreamStartedMessage("StreamStarted", streamId)
                                )
                            }
                        }
                    }
                }
            }

            else -> println("Unknown message type: ${data.type}")
        }
    }

    private suspend fun sendMessage(connection: DefaultWebSocketServerSession, message: Any) {
        connection.send(Frame.Text(Json.encodeToString(message)))
    }
}
