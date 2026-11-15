package dev.musicx.android.playback

import android.content.ContextWrapper
import coil3.ImageLoader
import dev.musicx.android.ui.player.CanvasCache
import dev.musicx.shared.api.artwork.CanvasContentSource
import dev.musicx.shared.api.artwork.DirectCanvasProvider
import dev.musicx.shared.api.artwork.YouTubeCanvasClip
import dev.musicx.shared.settings.Settings
import dev.musicx.shared.settings.YouTubeCanvasMode
import dev.musicx.shared.viewmodels.PlayerViewModel.MusicVideoCanvas
import dev.musicx.shared.youtube.playback.PlaybackResource
import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class CanvasStartupTest {
    @Test
    fun uncachedForegroundClipsReturnTheStreamWithoutPreparingFiles() =
        runBlocking {
            val settings = Settings(MapSettings())
            Settings.replaceSharedForTest(settings)
            val context =
                object : ContextWrapper(RuntimeEnvironment.getApplication()) {
                    override fun getCacheDir(): File = error("Foreground Canvas must not prepare clip files")

                    override fun getFilesDir(): File = error("Foreground preparation must not wait for the disk cache")
                }
            val imageLoader = ImageLoader.Builder(context).build()
            val http = HttpClient(MockEngine { error("Foreground preparation must not fetch media") })
            try {
                val platform = AndroidPlayerPlatformServices(context, imageLoader, DirectCanvasProvider(http))
                val canvas =
                    MusicVideoCanvas(
                        PlaybackResource(
                            "video",
                            "audio",
                            emptyMap(),
                            null,
                            null,
                            videoUrl = "https://canvas.invalid/video",
                            videoMimeType = "video/mp4",
                            durationSeconds = 180,
                        ),
                        listOf(YouTubeCanvasClip(20_000, 23_000), YouTubeCanvasClip(40_000, 43_000)),
                    )
                val mode = settings.youtubeCanvasMode
                val quality = settings.youtubeCanvasQuality
                assertEquals(YouTubeCanvasMode.SHORT_CLIPS, mode)
                assertNotEquals(0, settings.animatedArtworkCacheSize)
                val result = platform.prepareMusicVideoCanvas("song", canvas, mode, quality, preload = false)
                val source = CanvasContentSource.YOUTUBE_MUSIC_VIDEO.name
                val preparedKey = CanvasCache.key("song", source, "${quality.name}:${mode.name}")
                val streamingKey = CanvasCache.key("song", source, "${quality.name}:${mode.name}:stream")
                assertEquals(canvas.copy(cacheKey = streamingKey), result)
                assertNotEquals(preparedKey, result.cacheKey)
            } finally {
                http.close()
                imageLoader.shutdown()
                Settings.replaceSharedForTest(null)
            }
        }
}
