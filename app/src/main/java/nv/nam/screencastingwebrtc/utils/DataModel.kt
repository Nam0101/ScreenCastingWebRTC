package nv.nam.screencastingwebrtc.utils

/**
 * @author Nam Nguyen Van
 * Project: ScreenCastingWebRTC
 * Created: 11/7/2024
 * Github: https://github.com/Nam0101
 * @description :
 */

enum class DataModelType {
    StartStreaming, EndCall, Offer, Answer, IceCandidates, SignIn, ViewerJoined, WatchStream
}

data class DataModel(
    val streamId: String? = null,
    val type: DataModelType? = null,
    val target: String? = null,
    val data: Any? = null
)
