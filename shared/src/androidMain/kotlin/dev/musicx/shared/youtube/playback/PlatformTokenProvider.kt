package dev.musicx.shared.youtube.playback

import com.metrolist.innertubex.extraction.TokenProvider

@Suppress("FunctionName")
actual fun PlatformTokenProvider(): TokenProvider = AndroidTokenProvider(AndroidContextProvider.applicationContext)
