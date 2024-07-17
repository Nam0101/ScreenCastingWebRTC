package nv.nam.screencastingwebrtc.server

import android.util.Log
import com.google.gson.Gson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.origin
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import nv.nam.screencastingwebrtc.webrtc.WebrtcClient
import java.time.Duration

class KtorSignalServer(
    private val webrtcClient: WebrtcClient
) {
    private val port = 3000
    private var server: ApplicationEngine? = null

    data class SignalingMessage(
        val type: String,
        val streamId: String? = null,
        val target: String? = null,
        val data: String? = null,
        val clientId: String? = null
    )

    fun start() {
        server = embeddedServer(CIO, port) {
            ktorSignalServer()
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(1000, 5000)
        server = null
    }

    private fun Application.ktorSignalServer() {
        val connections = mutableMapOf<String, DefaultWebSocketServerSession>()
        val gson = Gson()

        install(WebSockets) {
            pingPeriod = Duration.ofSeconds(15)
            timeout = Duration.ofSeconds(15)
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }
        Log.i("KtorSignalServer", "ktorSignalServer: started on port $port")

        routing {
            webSocket("/") {
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            try {
                                val data = gson.fromJson(text, SignalingMessage::class.java)
                                handleMessage(this, data, connections)
                                Log.i("KtorSignalServer", "received: $data")
                            } catch (e: Exception) {
                                Log.e("KtorSignalServer", "Error parsing message: $text")
                            }
                        }

                        else -> {}
                    }
                }
                val streamId = connections.entries.find { it.value == this }?.key
                if (streamId != null) {
                    connections.remove(streamId)
                }
            }
        }
    }

    private suspend fun handleMessage(
        connection: DefaultWebSocketServerSession,
        data: SignalingMessage,
        connections: MutableMap<String, DefaultWebSocketServerSession>
    ) {
        val host = connection.call.request.origin.remoteHost
        Log.i("KtorSignalServer", "host: $host")

        when (data.type) {
            "SignIn" -> {
                val streamId = data.streamId ?: return
                Log.i("KtorSignalServer", "SignIn: $data")
                connections[streamId] = connection
            }
            "Offer", "Answer", "IceCandidates" -> {
                Log.i("KtorSignalServer", "Offer: $data")
                val streamId = data.streamId ?: return
                val target = data.target ?: return
                val targetConnection = connections[target]
                if (targetConnection != null) {
                    sendMessage(targetConnection, data)
                } else {
                    Log.e("KtorSignalServer", "Target $target not found.")
                }
            }
            "WatchStream" -> {
//                Log.i("WatchStream", "WatchStream: $data")
//                val streamId = data.streamId ?: return
//                val clientId = data.clientId ?: return
//                val targetConnection = connections[streamId]
//                Log.i("KtorSignalServer", "WatchStream: $streamId")
//                if (targetConnection != null) {
//                    sendMessage(
//                        targetConnection,
//                        SignalingMessage("ViewerJoined", streamId, target = clientId)
//                    )
//                } else {
//                    Log.e("WatchStream", "Target $streamId not found.")
//                }
                Log.i("StartStreaming", "StartStreaming: $data")
                webrtcClient.call(data.target!!)
            }
            "StartStreaming" -> {
                val streamId = data.streamId ?: return
                Log.i("StartStreaming", "StartStreaming: $data")
                for ((clientId, clientConnection) in connections) {
                    if (clientId != streamId) {
                        sendMessage(
                            clientConnection, SignalingMessage("StreamStarted", streamId = streamId)
                        )
                    }
                }
            }

            else -> Log.e("KtorSignalServer", "Unknown message type: ${data.type}")
        }
    }

    private suspend fun sendMessage(
        connection: DefaultWebSocketServerSession, message: SignalingMessage
    ) {
        val gson = Gson()
        try {
            connection.send(Frame.Text(gson.toJson(message)))
            Log.i("KtorSignalServer", "sent: $message")
        } catch (e: Exception) {
            Log.e("KtorSignalServer", "Error sending message: $message")
        }
    }
}