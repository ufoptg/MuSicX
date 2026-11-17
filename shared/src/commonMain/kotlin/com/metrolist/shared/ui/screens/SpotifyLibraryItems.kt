/**
 * MuSicX Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.ui.screens

import com.metrolist.shared.model.PlaylistItem
import meow.nyx.spotlib.PlaylistMeta

/** Maps a Spotify playlist to a library tile that keeps its Spotify URI as the item id. */
fun PlaylistMeta.toPlaylistItem(): PlaylistItem =
    PlaylistItem(
        id = uri,
        title = name,
        thumbnail = image,
        author = owner,
        songCount = trackCount.takeIf { it > 0 }?.toString(),
        description = description.takeIf { it.isNotBlank() },
        isEditable = false,
    )
