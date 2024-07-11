package nv.nam.screencastingwebrtc

import android.app.Application
import android.util.Log
import com.google.gson.Gson
import nv.nam.screencastingwebrtc.repository.MainRepository
import nv.nam.screencastingwebrtc.service.WebrtcService
import nv.nam.screencastingwebrtc.service.WebrtcServiceRepository
import nv.nam.screencastingwebrtc.socket.ClientSocket
import nv.nam.screencastingwebrtc.webrtc.WebrtcClient
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.logger.Level
import org.koin.dsl.module

/**
 * @author Nam Nguyen Van
 * Project: ScreenCastingWebRTC
 * Created: 11/7/2024
 * Github: https://github.com/Nam0101
 * @description : ScreenCastingApplication is the main application class
 */
class ScreenCastingApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.INFO)
            androidContext(this@ScreenCastingApplication)
            modules(module)
        }
    }
}

val module = module {
    factory {
        ClientSocket(get())
        Log.i("Koin", "Debug init ClientSocket:")
    }
    factory {
        Gson()
        Log.i("Koin", "Debug init Gson:")
    }
    factory {
        WebrtcClient(get(), get())
        Log.i("Koin", "Debug init WebrtcClient:")
    }
    factory<WebrtcServiceRepository> {
        WebrtcServiceRepository(androidContext())
    }
    factory {
        WebrtcService()
        Log.i("Koin", "Debug init WebrtcService:")
    }
    factory {
        MainRepository(get(), get(), get())
        Log.i("Koin", "Debug init MainRepository:")
    }
}