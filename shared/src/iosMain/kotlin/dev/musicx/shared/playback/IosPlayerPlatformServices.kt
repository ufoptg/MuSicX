package dev.musicx.shared.playback

import coil3.BitmapImage
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.metrolist.innertubex.extraction.ContentHints
import dev.musicx.shared.model.SongItem
import dev.musicx.shared.ui.settings.IosNativeFiles
import dev.musicx.shared.ui.theme.ThemeColorExtractor
import dev.musicx.shared.ui.theme.getGradientColors
import dev.musicx.shared.utils.artworkColors
import dev.musicx.shared.viewmodels.PlayerArtworkColors
import dev.musicx.shared.viewmodels.PlayerPlatformServices
import dev.musicx.shared.youtube.extraction.models.toContentHints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.UIKit.UIViewController

internal class IosPlayerPlatformServices(
    private val presenter: UIViewController,
) : PlayerPlatformServices {
    override suspend fun extractArtworkColors(
        songId: String,
        thumbnailUrl: String,
    ): PlayerArtworkColors? {
        val context = PlatformContext.INSTANCE
        val request =
            ImageRequest
                .Builder(context)
                .artworkColors(thumbnailUrl)
                .build()
        val bitmap = ((SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image as? BitmapImage)?.bitmap ?: return null
        val seed = withContext(Dispatchers.Default) { ThemeColorExtractor().extractSeedColor(bitmap) }
        return PlayerArtworkColors(dominant = seed, gradient = getGradientColors(seed))
    }

    override fun shareText(text: String) {
        IosNativeFiles.shareText(presenter, text)
    }

    override fun copyText(text: String) {
        platform.UIKit.UIPasteboard.generalPasteboard.string = text
    }

    override suspend fun spotifyCanvas(
        title: String,
        artist: String,
        album: String?,
        durationMs: Long?,
        primaryArtist: String?,
    ): String? = null

    override fun musicVideoCanvasHints(song: SongItem): ContentHints = song.toContentHints()
}
