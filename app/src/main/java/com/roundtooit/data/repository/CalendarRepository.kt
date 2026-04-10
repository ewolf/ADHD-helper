package com.roundtooit.data.repository

import com.roundtooit.data.local.dao.CachedEventDao
import com.roundtooit.data.local.entity.CachedEventEntity
import kotlinx.coroutines.flow.Flow
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarRepository @Inject constructor(
    private val cachedEventDao: CachedEventDao,
) {
    fun getTodayEvents(): Flow<List<CachedEventEntity>> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis

        cal.add(Calendar.DAY_OF_YEAR, 1)
        val endOfDay = cal.timeInMillis

        return cachedEventDao.getTodayEvents(startOfDay, endOfDay, System.currentTimeMillis())
    }

    fun getNextFutureEvents(): Flow<List<CachedEventEntity>> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cachedEventDao.getNextFutureEvents(cal.timeInMillis)
    }

    suspend fun updateReminderSettings(
        eventId: String,
        reminderMode: String,
        reminder1hEnabled: Boolean,
        reminder5mEnabled: Boolean,
    ) {
        val event = cachedEventDao.getById(eventId) ?: return
        cachedEventDao.update(
            event.copy(
                reminderMode = reminderMode,
                reminder1hEnabled = reminder1hEnabled,
                reminder5mEnabled = reminder5mEnabled,
            )
        )
    }

    suspend fun getUpcomingEvents(): List<CachedEventEntity> {
        return cachedEventDao.getUpcomingEvents(System.currentTimeMillis())
    }

    suspend fun cacheEvents(events: List<CachedEventEntity>) {
        cachedEventDao.insertAll(events)
    }

    suspend fun clearOldEvents() {
        val yesterday = System.currentTimeMillis() - 86_400_000
        cachedEventDao.deletePastEvents(yesterday)
    }
}
