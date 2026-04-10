package com.roundtooit.data.local.dao

import androidx.room.*
import com.roundtooit.data.local.entity.CachedEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedEventDao {
    @Query("SELECT * FROM cached_events WHERE startTime >= :startOfDay AND startTime < :endOfDay ORDER BY startTime ASC")
    fun getTodayEvents(startOfDay: Long, endOfDay: Long): Flow<List<CachedEventEntity>>

    @Query("SELECT * FROM cached_events WHERE startTime >= :afterTime ORDER BY startTime ASC LIMIT 3")
    fun getNextFutureEvents(afterTime: Long): Flow<List<CachedEventEntity>>

    @Query("SELECT * FROM cached_events WHERE googleEventId = :eventId")
    suspend fun getById(eventId: String): CachedEventEntity?

    @Query("SELECT * FROM cached_events WHERE startTime >= :fromTime ORDER BY startTime ASC")
    suspend fun getUpcomingEvents(fromTime: Long): List<CachedEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<CachedEventEntity>)

    @Update
    suspend fun update(event: CachedEventEntity)

    @Query("DELETE FROM cached_events WHERE startTime < :beforeTime")
    suspend fun deletePastEvents(beforeTime: Long)

    @Query("DELETE FROM cached_events")
    suspend fun deleteAll()
}
