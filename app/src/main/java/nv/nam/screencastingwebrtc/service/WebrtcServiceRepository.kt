package nv.nam.screencastingwebrtc.service

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * @author Nam Nguyen Van
 * Project: ScreenCastingWebRTC
 * Created: 11/7/2024
 * Github: https://github.com/Nam0101
 * @description : WebrtcServiceRepository is the class to handle the webrtc service
 */
class WebrtcServiceRepository(
    private val context: Context
) {
    init {
        Log.i("WebrtcServiceRepository", "init: ")
    }


    fun startIntent(streamId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val startIntent = Intent(context, WebrtcServiceRepository::class.java)
            startIntent.action = "StartIntent"
            startIntent.putExtra("streamId", streamId)
            Log.i("WebrtcServiceRepository", "startIntent: $streamId")
            context.startForegroundService(startIntent)
        }

    }

    fun requestConnection(target: String) {
        val thread = Thread {
            val startIntent = Intent(context, WebrtcServiceRepository::class.java)
            startIntent.action = "RequestConnectionIntent"
            startIntent.putExtra("target", target)
            context.startForegroundService(startIntent)
        }
        thread.start()
    }

    fun acceptCAll(target: String) {
        val thread = Thread {
            val startIntent = Intent(context, WebrtcServiceRepository::class.java)
            startIntent.action = "AcceptCallIntent"
            startIntent.putExtra("target", target)
            context.startForegroundService(startIntent)
        }
        thread.start()
    }

    fun endCallIntent() {
        val thread = Thread {
            val startIntent = Intent(context, WebrtcServiceRepository::class.java)
            startIntent.action = "EndCallIntent"
            context.startForegroundService(startIntent)
        }
        thread.start()
    }

    fun stopIntent() {
        val thread = Thread {
            val startIntent = Intent(context, WebrtcServiceRepository::class.java)
            startIntent.action = "StopIntent"
            context.startForegroundService(startIntent)
        }
        thread.start()
    }

}