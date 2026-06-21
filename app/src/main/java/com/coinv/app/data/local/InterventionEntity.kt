package com.coinv.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "interventions")
data class InterventionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val triggerContext: String,
    val content: String,
    val timestamp: Long,
    val outcome: String,
    val outcomeTimestamp: Long? = null
)
