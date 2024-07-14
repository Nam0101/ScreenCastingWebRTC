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

import nv.nam.screencastingwebrtc.utils.DataModel
import nv.nam.screencastingwebrtc.utils.DataModelType
import com.google.gson.Gson

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
                    Log.i("KtorSignalServer", "Connection opened from: $thisConnection")
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
        when (data.type) {
            DataModelType.SignIn -> {
                data.streamId?.let { connections[it] = connection }
            }
            DataModelType.Offer, DataModelType.Answer, DataModelType.IceCandidates -> {
                data.target?.let { target ->
                    connections[target]?.let { targetConnection ->
                        sendMessage(targetConnection, data)
                        Log.i("KtorSignalServer", "Message sent to: $target")
                    }
                }
            }
            // Handle other DataModelType cases as needed
            else -> println("Unknown message type: ${data.type}")
        }
    }

    private suspend fun sendMessage(connection: DefaultWebSocketServerSession, message: DataModel) {
        val jsonMessage = gson.toJson(message)
        connection.send(Frame.Text(jsonMessage))
    }
}