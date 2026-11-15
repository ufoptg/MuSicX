package dev.musicx.shared

import dev.musicx.shared.api.lastfm.LastFM
import dev.musicx.shared.auth.AuthManager
import dev.musicx.shared.backup.IosBackupRestoreManager
import dev.musicx.shared.db.MusicDatabase
import dev.musicx.shared.db.daos.DatabaseDao
import dev.musicx.shared.db.getDatabaseBuilder
import dev.musicx.shared.discord.DiscordRpcManager
import dev.musicx.shared.playback.MusicPlayer
import dev.musicx.shared.repository.LyricsRepository
import dev.musicx.shared.scrobble.ScrobbleSessions
import dev.musicx.shared.settings.BlockedContent
import dev.musicx.shared.settings.Settings
import dev.musicx.shared.state.GlobalYouTube
import dev.musicx.shared.state.SongLikeState
import dev.musicx.shared.state.coreModule
import dev.musicx.shared.youtube.di.sharedModule
import dev.musicx.shared.youtube.playback.StreamResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform

object MuSicXPlatform {
    private var isInitialized = false

    fun initialize() {
        if (isInitialized) return
        IosBackupRestoreManager().recoverInterruptedRestore()
        isInitialized = true
        startKoin {
            modules(
                coreModule,
                sharedModule,
                module {
                    single<MusicDatabase> { getDatabaseBuilder(null).build() }
                    single<DatabaseDao> { get<MusicDatabase>().dao() }
                    single { MusicPlayer() }
                },
            )
        }

        LastFM.initialize(
            apiKey = BuildConfig.LASTFM_API_KEY,
            secret = BuildConfig.LASTFM_SECRET,
        )
        LastFM.sessionKey = Settings.shared.lastFmSession.ifBlank { null }
        ScrobbleSessions.restorePersistedAccounts()

        AuthManager().restorePersistedSession()

        val startupScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        startupScope.launch {
            BlockedContent.refreshAiSlopBlocklist()
        }
        startupScope.launch {
            runCatching {
                KoinPlatform.getKoin().get<StreamResolver>().prewarm()
            }
        }
    }

    suspend fun reopenDatabaseAfterRestore(): MusicDatabase {
        val koin = KoinPlatform.getKoin()
        val database = getDatabaseBuilder(null).build()
        koin.declare(database)
        koin.declare(database.dao())
        koin.declare(MusicPlayer())
        koin.declare(
            LyricsRepository(
                lyricsClient = koin.get(),
                lyricsTranslationClient = koin.get(),
                databaseDao = database.dao(),
                youtubeLyricsApi = koin.get(),
                aiModelCatalog = koin.get(),
            ),
        )
        refreshRestoredSettings()
        return database
    }

    suspend fun refreshRestoredSettings() {
        DiscordRpcManager.reloadAfterRestore(GlobalYouTube.client)
        AuthManager().restorePersistedSession()
        ScrobbleSessions.restorePersistedAccounts()
        SongLikeState.refreshFromSettings()
    }
}
