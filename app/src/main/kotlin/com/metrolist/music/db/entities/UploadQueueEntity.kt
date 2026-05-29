/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Immutable
@Entity(tableName = "upload_queue")
data class UploadQueueEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val state: UploadState = UploadState.PENDING,
    val progress: Float = 0f,
    val attempts: Int = 0,
    val errorMessage: String? = null,
    val enqueuedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
)

enum class UploadState { PENDING, RUNNING, SUCCESS, FAILED, CANCELLED }
