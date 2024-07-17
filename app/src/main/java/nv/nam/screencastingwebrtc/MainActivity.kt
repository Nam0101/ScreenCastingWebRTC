package nv.nam.screencastingwebrtc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioRecord
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nv.nam.screencastingwebrtc.databinding.ActivityMainBinding
import nv.nam.screencastingwebrtc.repository.MainRepository
import nv.nam.screencastingwebrtc.server.KtorLocalServer
import nv.nam.screencastingwebrtc.server.KtorSignalServer
import nv.nam.screencastingwebrtc.service.WebrtcService
import nv.nam.screencastingwebrtc.service.WebrtcServiceRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.webrtc.MediaStream


/**
 * @author Nam Nguyen Van
 * Project: ScreenCastingWebRTC
 * Created: 11/7/2024
 * Github: https://github.com/Nam0101
 * @description : MainActivity is the main activity it handle the screen capture and audio recording
 */
class MainActivity : AppCompatActivity(), KoinComponent, MainRepository.Listener {
    private var binding: ActivityMainBinding? = null
//    private val streamId = (100000..999999).random().toString()
    private val streamId = "123456"
    private val webrtcServiceRepository: WebrtcServiceRepository by inject()
    private var isRecording = false
    private lateinit var audioRecord: AudioRecord
    private lateinit var audioManager: MediaProjectionManager
    private val recordingCoroutine = Dispatchers.IO + Job()

    companion object {
        private const val TAG = "MainActivity"
        private val REQUEST_SCREEN_CAPTURE = 101
        private val REQUEST_RECORD_AUDIO = 102
        private val REQUEST_RECORD_AUDIO_FOREGROUND = 103
    }

    private fun startKtorServer() {
        CoroutineScope(Dispatchers.Default).launch {
            delay(1000)
            val ktorSignalServer = KtorSignalServer()
            ktorSignalServer.start()
            val ktorLocalServer = KtorLocalServer()
            ktorLocalServer.startServer()
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
        binding!!.textView.text = getIPAddress()
        WebrtcService.listener = this
        WebrtcService.surfaceView = binding!!.surfaceView
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO
            )
        }
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION),
                REQUEST_RECORD_AUDIO_FOREGROUND
            )
        }
        startKtorServer()
        binding!!.start.setOnClickListener {
            startScreenCapture()
            isRecording = true
            Log.i(TAG, "onCreate: start")
            webrtcServiceRepository.startIntent(streamId)

        }
        binding!!.stop.setOnClickListener {
            webrtcServiceRepository.stopIntent()
            isRecording = false
            Log.i(TAG, "onCreate: stop")
        }
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.i(TAG, "screenCaptureLauncher: ${result.resultCode}")
        if (result.resultCode == RESULT_OK) {
            val intentData = result.data
            Log.i(TAG, "Screen capture permission granted")

            WebrtcService.screenPermissionIntent = intentData
            webrtcServiceRepository.requestConnection(streamId)
        } else {
            Log.i(TAG, "Screen capture permission denied")
        }
    }

    private fun startScreenCapture() {
        val mediaProjectionManager = application.getSystemService(
            Context.MEDIA_PROJECTION_SERVICE
        ) as MediaProjectionManager
        Log.i(TAG, "startScreenCapture: ")

        screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    override fun onConnectionRequestReceived(target: String) {
        Log.i(TAG, "onConnectionRequestReceived: $target")
    }

    override fun onConnectionConnected() {
        Log.i(TAG, "onConnectionConnected: ")
    }

    override fun onCallEndReceived() {
        Log.i(TAG, "onCallEndReceived: ")
    }

    override fun onRemoteStreamAdded(stream: MediaStream) {
        Log.i(TAG, "onRemoteStreamAdded: $stream")
    }

    private fun getIPAddress(): String {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        val ipAddress = wifiManager.connectionInfo.ipAddress
        return android.text.format.Formatter.formatIpAddress(ipAddress)
    }



}