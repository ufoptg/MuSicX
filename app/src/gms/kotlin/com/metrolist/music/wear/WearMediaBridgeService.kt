package com.metrolist.music.wear

import android.content.ComponentName
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.metrolist.music.playback.MusicService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/**
 * Phone-side bridge (GMS flavor only).
 *
 * A Wear OS watch cannot directly [MediaBrowser.connect] to a [MediaLibraryService] that lives
 * on the paired phone — Media3's [SessionToken] only resolves same-device services. This service
 * closes that gap: it runs on the phone, connects to the app's own [MusicService] with a
 * same-device [MediaBrowser] (which works), and exposes the existing browse/search/playback
 * surface to the watch over the Wear Data Layer.
 *
 * Everything the watch can do is delegated to [MusicService]'s already-implemented
 * [androidx.media3.session.MediaLibraryService.MediaLibrarySession.Callback] — the same code
 * path that powers Android Auto. No data/playback logic is duplicated.
 *
 * Protocol (MessageClient, request carries a JSON payload with `requestId`):
 *  watch -> phone            phone -> watch
 *  /browse    {parentId,page,pageSize,requestId}  ->  /browse_result/<requestId>  (JSON array)
 *  /search    {query,page,pageSize,requestId}      ->  /search_result/<requestId>   (JSON array)
 *  /play      {mediaId,requestId}                  ->  /play_result/<requestId>     {ok}
 *  /pause     {requestId}                         ->  /pause_result/<requestId>
 *  /resume    {requestId}                         ->  /resume_result/<requestId>
 *  /next      {requestId}                         ->  /next_result/<requestId>
 *  /previous  {requestId}                         ->  /previous_result/<requestId>
 *  /nowplaying {requestId}                        ->  /nowplaying_result/<requestId> (JSON object)
 *
 * Requires the GMS build (play-services-wearable). FOSS/Izzy (F-Droid) builds ship without it,
 * so on those builds the watch simply finds no reachable bridge capability — by design.
 */
class WearMediaBridgeService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var browser: MediaBrowser? = null

    override fun onDestroy() {
        scope.cancel()
        runCatching { browser?.release() }
        browser = null
        super.onDestroy()
    }

    override fun onMessageReceived(event: MessageEvent) {
        val node = event.sourceNodeId ?: return
        val path = event.path
        val payload = event.data ?: ByteArray(0)
        scope.launch {
            try {
                ensureConnected()
                handle(node, path, payload)
            } catch (e: Throwable) {
                Log.w(TAG, "request $path failed", e)
                // If the session died, force a reconnect on the next request.
                runCatching { browser?.release() }
                browser = null
            }
        }
    }

    private suspend fun ensureConnected() {
        browser?.let { return }
        val token = SessionToken(this, ComponentName(this, MusicService::class.java))
        browser = MediaBrowser.Builder(this, token).buildAsync().await()
    }

    private suspend fun handle(node: String, path: String, payload: ByteArray) {
        val browser = this.browser ?: error("browser not connected")
        val req = runCatching { JSONObject(String(payload, Charsets.UTF_8)) }.getOrNull()
            ?: JSONObject()
        val requestId = req.optString("requestId")

        when (path) {
            "/browse" -> {
                val parentId = req.optString("parentId", MusicService.ROOT)
                val page = req.optInt("page", 0)
                val pageSize = req.optInt("pageSize", 50)
                val result = browser.getChildren(parentId, page, pageSize, null).await()
                val items = result.value ?: emptyList()
                MediaItemCache.putAll(items)
                reply(node, "/browse_result/$requestId", items.toJsonArray())
            }
            "/search" -> {
                val query = req.optString("query", "")
                val page = req.optInt("page", 0)
                val pageSize = req.optInt("pageSize", 50)
                browser.search(query, null).await() // triggers onSearch on the service
                val result = browser.getSearchResult(query, page, pageSize, null).await()
                val items = result.value ?: emptyList()
                MediaItemCache.putAll(items)
                reply(node, "/search_result/$requestId", items.toJsonArray())
            }
            "/play" -> {
                val mediaId = req.optString("mediaId", "")
                val item = MediaItemCache.get(mediaId)
                    ?: MediaItem.Builder().setMediaId(mediaId).build()
                browser.setMediaItem(item)
                browser.prepare()
                browser.play()
                replyOk(node, "/play_result/$requestId")
            }
            "/pause" -> { browser.pause(); replyOk(node, "/pause_result/$requestId") }
            "/resume" -> { browser.play(); replyOk(node, "/resume_result/$requestId") }
            "/next" -> { browser.seekToNext(); replyOk(node, "/next_result/$requestId") }
            "/previous" -> { browser.seekToPrevious(); replyOk(node, "/previous_result/$requestId") }
            
            "/nowplaying" -> {
                reply(node, "/nowplaying_result/$requestId", nowPlayingJson(browser))
            }
            else -> Log.w(TAG, "unknown path: $path")
        }
    }

    private fun nowPlayingJson(browser: MediaBrowser): ByteArray {
        val item = browser.currentMediaItem
        val md = item?.mediaMetadata
        val o = JSONObject()
        o.put("title", md?.title?.toString())
        o.put("subtitle", md?.subtitle?.toString())
        o.put("artist", md?.artist?.toString())
        o.put("artworkUri", md?.artworkUri?.toString())
        o.put("durationMs", browser.duration.coerceAtLeast(0L))
        o.put("positionMs", browser.currentPosition.coerceAtLeast(0L))
        o.put("isPlaying", browser.isPlaying)
        return o.toString().toByteArray(Charsets.UTF_8)
    }

    private suspend fun replyOk(node: String, path: String) {
        val bytes = JSONObject().put("ok", true).toString().toByteArray(Charsets.UTF_8)
        reply(node, path, bytes)
    }

    private suspend fun reply(node: String, path: String, data: ByteArray) {
        runCatching {
            Wearable.getMessageClient(this).sendMessage(node, path, data).await()
        }.onFailure { Log.w(TAG, "reply to $path failed", it) }
    }

    companion object {
        private const val TAG = "WearMediaBridge"
    }
}
