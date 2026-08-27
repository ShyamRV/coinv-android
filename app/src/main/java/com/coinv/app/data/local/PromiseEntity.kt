package com.coinv.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "promises")
data class PromiseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val capturedAt: Long,
    val followUpAt: Long,
    val status: String,
    /** Intervention ledger row written at capture time; follow-up updates this same row. */
    val interventionId: Long? = null
)
