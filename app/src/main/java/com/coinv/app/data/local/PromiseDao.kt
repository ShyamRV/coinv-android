package com.coinv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.coinv.app.data.local.entity.PromiseEntity

@Dao
interface PromiseDao {
    @Insert
    suspend fun insert(promise: PromiseEntity): Long

    @Update
    suspend fun update(promise: PromiseEntity)

    @Query(
        """
        SELECT COUNT(*) FROM promises
        WHERE capturedAt >= :since AND status != 'expired'
        """
    )
    suspend fun countSince(since: Long): Int

    @Query(
        """
        SELECT * FROM promises
        WHERE status = 'pending' AND followUpAt <= :now
        ORDER BY followUpAt ASC
        LIMIT 1
        """
    )
    suspend fun oldestDueFollowUp(now: Long): PromiseEntity?

    @Query(
        """
        UPDATE promises SET status = 'expired'
        WHERE status = 'pending' AND followUpAt < :expireBefore
        """
    )
    suspend fun expireStale(expireBefore: Long)
}
