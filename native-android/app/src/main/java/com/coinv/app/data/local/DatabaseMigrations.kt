package com.coinv.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS semantic_memories (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                content TEXT NOT NULL,
                layer TEXT NOT NULL,
                embedding TEXT NOT NULL,
                sourceType TEXT NOT NULL,
                sourceId INTEGER,
                timestamp INTEGER NOT NULL,
                importance REAL NOT NULL
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS interventions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                type TEXT NOT NULL,
                triggerContext TEXT NOT NULL,
                content TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                outcome TEXT NOT NULL,
                outcomeTimestamp INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS promises (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                text TEXT NOT NULL,
                capturedAt INTEGER NOT NULL,
                followUpAt INTEGER NOT NULL,
                status TEXT NOT NULL
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE promises ADD COLUMN interventionId INTEGER")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE decisions ADD COLUMN outcomeFollowUpAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE decisions ADD COLUMN outcomeAskedAt INTEGER")
        db.execSQL("ALTER TABLE decisions ADD COLUMN embedding TEXT")
        // Prior confidenceScore was Int 0–100; engine uses Float 0.0–1.0
        db.execSQL("UPDATE decisions SET confidenceScore = confidenceScore / 100.0 WHERE confidenceScore > 1")
        db.execSQL(
            """
            UPDATE decisions SET outcomeFollowUpAt = createdAt + 1814400000
            WHERE outcomeFollowUpAt = 0
            """.trimIndent()
        )
        db.execSQL(
            """
            UPDATE decisions SET status = 'pending_outcome'
            WHERE status IN ('pending', 'analyzed')
            """.trimIndent()
        )
        db.execSQL(
            """
            UPDATE decisions SET status = 'resolved_mixed'
            WHERE status = 'resolved'
            """.trimIndent()
        )
    }
}
