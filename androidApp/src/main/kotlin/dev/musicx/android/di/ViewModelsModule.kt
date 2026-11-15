package dev.musicx.android.di

import android.app.Application
import dev.musicx.android.R
import dev.musicx.android.data.PlayHistoryRepository
import dev.musicx.android.playback.AndroidPlayerPlatformServices
import dev.musicx.android.playback.PlayerConnection
import dev.musicx.android.viewmodels.AboutArtistViewModel
import dev.musicx.android.viewmodels.AccountViewModel
import dev.musicx.android.viewmodels.AlbumViewModel
import dev.musicx.android.viewmodels.ArtistViewModel
import dev.musicx.android.viewmodels.BrowseViewModel
import dev.musicx.android.viewmodels.HistoryViewModel
import dev.musicx.android.viewmodels.HomeViewModel
import dev.musicx.android.viewmodels.LibraryViewModel
import dev.musicx.android.viewmodels.PlaybackViewModel
import dev.musicx.android.viewmodels.PlaylistViewModel
import dev.musicx.android.viewmodels.SearchViewModel
import dev.musicx.android.viewmodels.StatsViewModel
import dev.musicx.shared.api.artwork.AnimatedArtworkService
import dev.musicx.shared.api.artwork.DirectCanvasProvider
import dev.musicx.shared.viewmodels.PlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelsModule =
    module {
        singleOf(::PlayHistoryRepository)

        viewModel { StatsViewModel(get<PlayHistoryRepository>()) }

        viewModel { params ->
            AlbumViewModel(
                params.getOrNull(),
                get(),
                get(),
                get<CoroutineScope>(),
            )
        }

        singleOf(::AnimatedArtworkService)

        viewModel { params ->
            BrowseViewModel(params[0], params[1], params[2])
        }

        viewModel { params ->
            ArtistViewModel(params.getOrNull(), get(), get<CoroutineScope>())
        }

        viewModel { (id: String, name: String) -> AboutArtistViewModel(id, name) }

        viewModel {
            PlaybackViewModel(
                androidContext().applicationContext as Application,
                get(),
                get(),
            )
        }

        viewModelOf(::AccountViewModel)
        viewModel { HistoryViewModel(get<PlayHistoryRepository>(), get<CoroutineScope>()) }
        viewModelOf(::LibraryViewModel)
        viewModel {
            HomeViewModel(
                database = get(),
                likedSongsName = androidContext().getString(R.string.liked_songs),
            )
        }
        viewModelOf(::SearchViewModel)
        single { DirectCanvasProvider(get()) }

        viewModel { params ->
            PlaylistViewModel(
                params.getOrNull(),
                get(),
                get<CoroutineScope>(),
                thumbnailHint = params[1],
            )
        }

        single {
            PlayerViewModel(
                playerConnection = get<PlayerConnection>(),
                imageLoader = get(),
                platformContext = androidContext().applicationContext,
                platform = AndroidPlayerPlatformServices(androidContext().applicationContext, get(), get()),
                lyricsRepository = get(),
                animatedArtworkService = get(),
                musicVideoResolver = get(),
                viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            )
        }
    }
