package dev.ufoptg.musicx.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import dev.ufoptg.musicx.wear.model.NowPlaying
import dev.ufoptg.musicx.wear.model.parseItems
import dev.ufoptg.musicx.wear.model.parseNowPlaying
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Talks to [com.metrolist.music.wear.WearMediaBridgeService] on the paired phone over the
 * Wear Data Layer. Uses CapabilityClient to find the phone node, MessageClient for request/
 * response correlated by `requestId`.
 */
class PhoneBridge(context: Context) : MessageClient.OnMessageReceivedListener {

    private val messageClient = Wearable.getMessageClient(context)
    private val capabilityClient = Wearable.getCapabilityClient(context)
    private val inflight = ConcurrentHashMap<String, CompletableDeferred<ByteArray>>()
    @Volatile private var phoneNodeId: String? = null

    init {
        messageClient.addListener(this)
    }

    /** Remove the message listener when the composition is disposed. */
    fun close() {
        runCatching { messageClient.removeListener(this) }
    }

    suspend fun isPhoneReachable(): Boolean = runCatching { ensurePhoneNode() }.isSuccess

    private suspend fun ensurePhoneNode(): String {
        phoneNodeId?.let { return it }
        repeat(30) {
            val info = capabilityClient
                .getCapability(CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                .await()
            val node = info.nodes.firstOrNull { it.isNearby } ?: info.nodes.firstOrNull()
            if (node != null) {
                phoneNodeId = node.id
                return node.id
            }
            delay(500)
        }
        error("MuSicX phone (GMS build) is not reachable. Install the GMS APK and pair the watch.")
    }

    override fun onMessageReceived(event: MessageEvent) {
        val requestId = event.path.substringAfterLast('/', "")
        if (requestId.isBlank()) return
        inflight.remove(requestId)?.complete(event.data ?: ByteArray(0))
    }

    private suspend fun request(path: String, payload: JSONObject, timeoutMs: Long = 15_000): ByteArray {
        val requestId = UUID.randomUUID().toString()
        payload.put("requestId", requestId)
        val deferred = CompletableDeferred<ByteArray>()
        inflight[requestId] = deferred
        try {
            val node = ensurePhoneNode()
            messageClient.sendMessage(node, path, payload.toString().toByteArray(Charsets.UTF_8)).await()
            return withTimeout(timeoutMs) { deferred.await() }
        } finally {
            // Avoid leaking deferreds on timeout or send failure.
            inflight.remove(requestId)
        }
    }

    suspend fun browse(parentId: String, page: Int = 0, pageSize: Int = 50) =
        parseItems(request("/browse", JSONObject().put("parentId", parentId).put("page", page).put("pageSize", pageSize)))

    suspend fun search(query: String, page: Int = 0, pageSize: Int = 50) =
        parseItems(request("/search", JSONObject().put("query", query).put("page", page).put("pageSize", pageSize)))

    suspend fun play(mediaId: String) {
        request("/play", JSONObject().put("mediaId", mediaId), timeoutMs = 20_000)
    }

    suspend fun pause() = request("/pause", JSONObject())
    suspend fun resume() = request("/resume", JSONObject())
    suspend fun next() = request("/next", JSONObject())
    suspend fun previous() = request("/previous", JSONObject())
    suspend fun nowPlaying(): NowPlaying =
        parseNowPlaying(request("/nowplaying", JSONObject(), timeoutMs = 5_000))

    companion object {
        const val CAPABILITY = "musicx_phone_bridge"
        private const val TAG = "PhoneBridge"
    }
}
