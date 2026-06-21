package com.coinv.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "semantic_memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val layer: String,
    val embedding: String,
    val sourceType: String,
    val sourceId: Long?,
    val timestamp: Long,
    val importance: Float = 0.5f
)
