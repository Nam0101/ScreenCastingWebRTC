package nv.nam.screencastingwebrtc.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import nv.nam.screencastingwebrtc.R
import nv.nam.screencastingwebrtc.repository.MainRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.webrtc.MediaStream
import org.webrtc.SurfaceViewRenderer

/**
 * @author Nam Nguyen Van
 * Project: ScreenCastingWebRTC
 * Created: 11/7/2024
 * Github: https://github.com/Nam0101
 * @description : WebrtcService is the class to handle the webrtc service
 */
class WebrtcService : Service(), MainRepository.Listener,KoinComponent {
    companion object {
        var screenPermissionIntent: Intent? = null
        var surfaceView: SurfaceViewRenderer? = null
        var listener: MainRepository.Listener? = null
    }
    init {
        Log.i("WebrtcService", "init: ")
    }

    private val mainRepository: MainRepository  by inject()

    private lateinit var notificationManager: NotificationManager
    private lateinit var streamId: String

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(
            NotificationManager::class.java
        )
        mainRepository.listener = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when (intent.action) {
                "StartIntent" -> {
                    this.streamId = intent.getStringExtra("streamId").toString()
                    Log.i("WebrtcService", "onStartCommand: $streamId")
                    if(surfaceView == null){
                        Log.i("WebrtcService", "onStartCommand: surfaceView is null")
                    }
                    else {
                        mainRepository.init(streamId, surfaceView!!)
                    }
                    startServiceWithNotification()
                }

                "StopIntent" -> {
                    stopMyService()
                }

                "EndCallIntent" -> {
                    mainRepository.sendCallEndedToOtherPeer()
                    mainRepository.onDestroy()
                    stopMyService()
                }

                "AcceptCallIntent" -> {
                    val target = intent.getStringExtra("target")
                    target?.let {
                        mainRepository.startCall(it)
                    }
                }

                "RequestConnectionIntent" -> {
                    val target = intent.getStringExtra("target")
                    target?.let {
                        Log.i("WebrtcService", "onStartCommand: $it")
                        if(screenPermissionIntent == null){
                            Log.i("WebrtcService", "onStartCommand: screenPermissionIntent is null")
                        }
                        mainRepository.setPermissionIntentToWebrtcClient(screenPermissionIntent!!)
                        mainRepository.startScreenCapturing(surfaceView!!)
                        mainRepository.sendScreenShareConnection(it)
                    }
                }
            }
        }

        return START_STICKY
    }

    private fun stopMyService() {
        mainRepository.onDestroy()
        stopSelf()
        notificationManager.cancelAll()
    }

    private fun startServiceWithNotification() {
        val notificationChannel = NotificationChannel(
            "channel1", "foreground", NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(notificationChannel)
        val notification =
            NotificationCompat.Builder(this, "channel1").setSmallIcon(R.mipmap.ic_launcher)

        startForeground(1, notification.build())

        Log.i("WebrtcService", "startServiceWithNotification: ")

    }

    override fun onConnectionRequestReceived(target: String) {
        listener?.onConnectionRequestReceived(target)
    }

    override fun onConnectionConnected() {
        listener?.onConnectionConnected()
    }

    override fun onCallEndReceived() {
        listener?.onCallEndReceived()
        stopMyService()
    }

    override fun onRemoteStreamAdded(stream: MediaStream) {
        listener?.onRemoteStreamAdded(stream)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}