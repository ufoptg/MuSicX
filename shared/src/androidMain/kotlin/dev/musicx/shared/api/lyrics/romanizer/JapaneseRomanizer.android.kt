package dev.musicx.shared.api.lyrics.romanizer

import dev.musicx.shared.youtube.playback.AndroidContextProvider
import java.io.File

internal actual fun japaneseDictionaryStorageDirectory(): File =
    File(AndroidContextProvider.applicationContext.noBackupFilesDir, "japanese-dictionary")
