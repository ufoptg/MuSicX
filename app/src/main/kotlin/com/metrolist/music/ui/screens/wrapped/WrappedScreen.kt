package com.metrolist.music.ui.screens.wrapped

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.metrolist.music.R

@Composable
fun WrappedScreen(
    navController: NavController,
    viewModel: WrappedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose { viewModel.releaseAudio() }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.pauseAudio()
                Lifecycle.Event.ON_START -> viewModel.resumeAudio()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (!state.isDataReady && !state.isLoading) {
            viewModel.prepare(
                fromTimeStamp = WrappedViewModel.HALF_YEAR_START,
                toTimeStamp = WrappedViewModel.HALF_YEAR_END,
            )
        }
    }

    val window = (LocalContext.current as? Activity)?.window

    DisposableEffect(window) {
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    when {
        state.isLoading || !state.isDataReady -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.wrapped_loading))
            }
        }

        state.error != null -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.wrapped_error))
                Spacer(Modifier.height(8.dp))
                Text(state.error!!)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { navController.popBackStack() }) {
                    Text(stringResource(R.string.wrapped_back))
                }
            }
        }

        state.isAudioLoading && !state.isAudioReady -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.wrapped_audio_loading),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(24.dp))
                LinearProgressIndicator(
                    progress = {
                        if (state.audioTotalTracks > 0) {
                            state.audioLoadingProgress.toFloat() / state.audioTotalTracks.toFloat()
                        } else {
                            0f
                        }
                    },
                    modifier = Modifier.fillMaxWidth(0.6f),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.wrapped_audio_progress,
                        state.audioLoadingProgress,
                        state.audioTotalTracks,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(24.dp))
                TextButton(onClick = { viewModel.skipAudio() }) {
                    Text(stringResource(R.string.wrapped_audio_skip))
                }
            }
        }

        state.audioErrorMessage != null && !state.isAudioReady && !state.isAudioLoading -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.wrapped_audio_error))
                Spacer(Modifier.height(8.dp))
                Text(state.audioErrorMessage!!)
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { viewModel.retryAudio() }) {
                        Text(stringResource(R.string.wrapped_audio_retry))
                    }
                    TextButton(onClick = { viewModel.skipAudio() }) {
                        Text(stringResource(R.string.wrapped_audio_skip))
                    }
                }
            }
        }

        state.isDataReady && state.isAudioReady -> {
            val pagerState = rememberPagerState(
                initialPage = 0,
                pageCount = { 19 },
            )

            LaunchedEffect(pagerState.currentPage) {
                viewModel.onPageChanged(pagerState.currentPage)
            }

            Box(modifier = Modifier.fillMaxSize()) {
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                ) { page ->
                    when (page) {
                        0 -> WrappedPage1Intro(state)
                        1 -> WrappedPage2GuessMinutes(state)
                        2 -> WrappedPage3MinutesReveal(state)
                        3 -> WrappedPage4ShareMinutes(state)
                        4 -> WrappedPage5TotalSongs(state)
                        5 -> WrappedPage6GuessTopSong(state)
                        6 -> WrappedPage7TopSongs(state)
                        7 -> WrappedPage8ShareTopTracks(state)
                        8 -> WrappedPage9GuessTopArtist(state)
                        9 -> WrappedPage10TopArtistReveal(state)
                        10 -> WrappedPage11TopArtists(state)
                        11 -> WrappedPage12ShareTopArtists(state)
                        12 -> WrappedPage13GuessTopAlbum(state)
                        13 -> WrappedPage14TopAlbumReveal(state)
                        14 -> WrappedPage15TopAlbums(state)
                        15 -> WrappedPage16FunStat(state)
                        16 -> WrappedPage17Playlist(state)
                        17 -> WrappedPage18Conclusion(state)
                        18 -> WrappedPage19Credits(state)
                    }
                }

                IconButton(
                    onClick = { viewModel.toggleMute() },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    Icon(
                        painter = painterResource(
                            if (state.isMuted) R.drawable.volume_off
                            else R.drawable.volume_up
                        ),
                        contentDescription = if (state.isMuted) stringResource(R.string.wrapped_unmute)
                        else stringResource(R.string.wrapped_mute),
                    )
                }
            }
        }
    }
}
