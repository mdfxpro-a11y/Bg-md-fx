package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "processed_history")
data class ProcessedItemEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val mediaType: String, // "IMAGE" or "VIDEO"
    val timestamp: Long,
    val imagePath: String, // Local cached file path
    val thumbnailPath: String,
    val bgType: String,
    val filterName: String,
    val resolution: String,
    val frameCount: Int = 1
)
