package nv.nam.screencastingwebrtc.webrtc

import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

/**
 * @author Nam Nguyen Van
 * Project: ScreenCastingWebRTC
 * Created: 11/7/2024
 * Github: https://github.com/Nam0101
 * @description : SdpObserver is the class to handle the sdp observer
 */
open class SdpObserver : SdpObserver {
    override fun onCreateSuccess(desc: SessionDescription?) {
    }

    override fun onSetSuccess() {
    }

    override fun onCreateFailure(p0: String?) {
    }

    override fun onSetFailure(p0: String?) {
    }
}