package dev.musicx.shared.api.lyrics.romanizer

import dev.musicx.shared.utils.XdgUtils
import java.io.File

internal actual fun japaneseDictionaryStorageDirectory(): File = File(XdgUtils.getXdgDataHome(), "metrolist/japanese-dictionary")
