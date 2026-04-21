package com.metrolist.music.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "innertube_cache")
data class InnertubeCacheEntity(
    @PrimaryKey val key: String,
    val blob: String,
    val timestamp: Long = System.currentTimeMillis()
)
