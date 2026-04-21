package com.metrolist.innertube

interface InnertubeCache {
    suspend fun get(key: String): String?
    suspend fun insert(key: String, value: String)
}
