package com.coinv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.coinv.app.data.local.entity.InterventionEntity

@Dao
interface InterventionDao {
    @Insert
    suspend fun insert(e: InterventionEntity): Long

    @Update
    suspend fun update(e: InterventionEntity)

    @Query("SELECT * FROM interventions WHERE type = :type ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recentByType(type: String, limit: Int): List<InterventionEntity>

    @Query("SELECT * FROM interventions WHERE type = :type ORDER BY timestamp DESC LIMIT 5")
    suspend fun lastFiveByType(type: String): List<InterventionEntity>

    @Query("SELECT * FROM interventions WHERE id = :id")
    suspend fun getById(id: Long): InterventionEntity?

    @Query(
        """
        SELECT COUNT(*) FROM interventions
        WHERE type = :type AND outcome IN ('shown', 'acted_on') AND timestamp >= :since
        """
    )
    suspend fun countShownSince(type: String, since: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM interventions
        WHERE type = :type AND outcome = 'dismissed' AND timestamp >= :since
        """
    )
    suspend fun countDismissedSince(type: String, since: Long): Int
}
