package com.roundtooit.data.remote

import com.roundtooit.data.local.dao.CachedEventDao
import com.roundtooit.data.local.entity.CachedEventEntity
import com.google.api.client.util.DateTime
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles Google Calendar API integration.
 * Queries all visible calendars (including shared) with proper timezone handling.
 */
@Singleton
class GoogleCalendarRepository @Inject constructor(
    private val cachedEventDao: CachedEventDao,
) {
    /**
     * Fetch events from all visible calendars and cache them locally.
     * Fetches the next 30 days of events.
     */
    suspend fun syncEvents(calendarService: com.google.api.services.calendar.Calendar) {
        val now = Calendar.getInstance()
        val timeMin = DateTime(now.time)

        now.add(Calendar.DAY_OF_YEAR, 30)
        val timeMax = DateTime(now.time)

        // Get all visible calendars
        val calendarList = calendarService.calendarList().list().execute()
        val calendarIds = calendarList.items
            ?.filter { it.accessRole in listOf("owner", "writer", "reader") }
            ?.map { it.id }
            ?: listOf("primary")

        val allEvents = mutableListOf<CachedEventEntity>()
        val seenIds = mutableSetOf<String>()

        for (calId in calendarIds) {
            try {
                val events = calendarService.events()
                    .list(calId)
                    .setTimeMin(timeMin)
                    .setTimeMax(timeMax)
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .setMaxResults(100)
                    .execute()

                events.items?.forEach { event ->
                    if (event.id in seenIds) return@forEach
                    seenIds.add(event.id)

                    val start = event.start?.dateTime?.value
                        ?: event.start?.date?.value
                        ?: return@forEach

                    val end = event.end?.dateTime?.value
                        ?: event.end?.date?.value
                        ?: start

                    val existing = cachedEventDao.getById(event.id)

                    allEvents.add(
                        CachedEventEntity(
                            googleEventId = event.id,
                            title = event.summary ?: "(No title)",
                            description = event.description,
                            startTime = start,
                            endTime = end,
                            location = event.location,
                            reminderMode = existing?.reminderMode ?: "voice",
                            reminder1hEnabled = existing?.reminder1hEnabled ?: true,
                            reminder5mEnabled = existing?.reminder5mEnabled ?: true,
                        )
                    )
                }
            } catch (_: Exception) { }
        }

        cachedEventDao.insertAll(allEvents)

        val yesterday = System.currentTimeMillis() - 86_400_000L
        cachedEventDao.deletePastEvents(yesterday)
    }
}
