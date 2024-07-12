package nv.nam.screencastingwebrtc.server

import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

/**
 * @author Nam Nguyen Van
 * Project: ScreenCastingWebRTC
 * Created: 12/7/2024
 * Github: https://github.com/Nam0101
 * @description : KtorLocalServer is the class to handle the local server
 */
class KtorLocalServer {
    init {
        println("KtorLocalServer init")
    }

    private val HTML = """
        <!DOCTYPE html>
<html lang ="en">

<head>
    <title>WebRTC Viewer</title>
</head>

<body>
    <h1>WebRTC Viewer</h1>
    <video id="remoteVideo" autoplay playsinline controls></video>

   <script>
    const videoElement = document.getElementById('remoteVideo');
    const streamId = "123456"
    const wsUrl = 'ws://127.0.0.1:3000';
    const connection = new WebSocket(wsUrl);
    const clientId = "viewer-1";
    let peerConnection;
    let remoteStream = new MediaStream();

    connection.onopen = () => {
        connection.send(JSON.stringify({ type: 'SignIn', streamId: clientId }));

        if (streamId) {
            connection.send(JSON.stringify({ type: 'WatchStream', streamId: streamId, target: clientId }));
        } else {
            console.error('Missing streamId parameter');
        }
    };

    connection.onmessage = async (message) => {
        const data = JSON.parse(message.data);
        switch (data.type) {
            case 'Offer':
                await handleOffer(data.data);
                break;
            case 'Answer':
                // This case might not be necessary for a viewer client
                break;
            case 'IceCandidates':
                await handleOffer(data.data);
                break;
            case 'StreamStarted':
                console.log('Stream started:', data.streamId);
                break;
            default:
                console.log('Unknown message type:', data.type);
        }
    };

    async function handleOffer(offerSdp) {
            console.log("Received offer SDP:", offerSdp); // Debug log

            peerConnection = new RTCPeerConnection({
                iceServers: [{ urls: 'stun:stun.l.google.com:19302' }] // Example STUN server
            });
            peerConnection.onicecandidate = handleIceCandidateEvent;
            peerConnection.ontrack = handleTrackEvent;

            videoElement.srcObject = remoteStream;

            try {
                await peerConnection.setRemoteDescription(new RTCSessionDescription({
                    type: 'offer',
                    sdp: offerSdp
                }));
            } catch (error) {
                console.error("Error setting remote description:", error);
                return; // Exit the function if there's an error
            }

            const answer = await peerConnection.createAnswer();
            await peerConnection.setLocalDescription(answer);
            connection.send(JSON.stringify({
                type: 'Answer',
                streamId: clientId,
                target: streamId,
                data: answer.sdp
            }));
        }

    async function handleIceCandidate(candidate) {
        try {
            await peerConnection.addIceCandidate(new RTCIceCandidate(candidate));
        } catch (error) {
            console.error('Error adding ICE candidate:', error);
        }
    }

    function handleIceCandidateEvent(event) {
        if (event.candidate) {
            connection.send(JSON.stringify({
                type: 'IceCandidates',
                streamId: clientId,
                target: streamId,
                data: event.candidate
            }));
        }
    }

    function handleTrackEvent(event) {
        remoteStream.addTrack(event.track);
    }
</script>
</body>

</html>
    """.trimIndent()

    fun startServer() {
        embeddedServer(CIO, port = 8080) { // Use CIO engine
            routing {
                get("/") {
                    call.respondText(HTML , contentType = io.ktor.http.ContentType.Text.Html)
                }
            }
        }.start(wait = false)
    }
}