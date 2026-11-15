package dev.musicx.desktop.spotifyimport

import dev.musicx.shared.network.sharedHttpEngine
import dev.musicx.shared.settings.Settings
import dev.musicx.shared.spotifyimport.SpotifyImportManager
import dev.musicx.shared.utils.PlatformLogger
import meow.nyx.spotlib.LogLevel
import meow.nyx.spotlib.SpotifyClient
import meow.nyx.spotlib.SpotifyConfig
import meow.nyx.spotlib.SpotifyLogger
import java.util.logging.Logger

internal val desktopSpotifyImportManager: SpotifyImportManager by lazy(::createDesktopSpotifyImportManager)

private fun createDesktopSpotifyImportManager(): SpotifyImportManager =
    SpotifyImportManager(
        clientFactory = {
            SpotifyClient.create(
                SpotifyConfig(
                    engine = sharedHttpEngine,
                    logLevel = LogLevel.SUMMARY,
                    logger = SpotifyLogger { _, message ->
                        Logger.getLogger("SpotifyApi").fine(message)
                    },
                ),
            )
        },
        loadStoredSession = { Settings.shared.spotifySession },
        saveStoredSession = { Settings.shared.spotifySession = it },
    )
