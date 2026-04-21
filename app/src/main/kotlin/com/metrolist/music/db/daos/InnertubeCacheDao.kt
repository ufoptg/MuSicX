package com.metrolist.music.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.metrolist.music.db.entities.InnertubeCacheEntity

@Dao
interface InnertubeCacheDao {
    @Query("SELECT * FROM innertube_cache WHERE `key` = :key")
    suspend fun get(key: String): InnertubeCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: InnertubeCacheEntity)

    @Query("DELETE FROM innertube_cache WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM innertube_cache WHERE timestamp < :threshold")
    suspend fun clearOld(threshold: Long)
}
