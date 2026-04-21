package com.metrolist.music.utils

import com.metrolist.innertube.InnertubeCache
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.InnertubeCacheEntity

class RoomInnertubeCache(
    private val database: MusicDatabase
) : InnertubeCache {
    override suspend fun get(key: String): String? {
        return database.innertubeCacheDao.get(key)?.blob
    }

    override suspend fun insert(key: String, value: String) {
        database.innertubeCacheDao.insert(
            InnertubeCacheEntity(
                key = key,
                blob = value,
                timestamp = System.currentTimeMillis()
            )
        )
    }
}
