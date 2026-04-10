package com.roundtooit.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.roundtooit.data.local.entity.CachedEventEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_EVENT_TITLE = "event_title"
        const val EXTRA_REMINDER_MODE = "reminder_mode"
        const val EXTRA_MINUTES_BEFORE = "minutes_before"

        private const val ONE_HOUR_MS = 60 * 60 * 1000L
        private const val FIVE_MINUTES_MS = 5 * 60 * 1000L
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleReminders(event: CachedEventEntity) {
        cancelReminders(event.googleEventId)

        if (event.reminderMode == "none") return

        val now = System.currentTimeMillis()

        if (event.reminder1hEnabled) {
            val triggerTime = event.startTime - ONE_HOUR_MS
            if (triggerTime > now) {
                scheduleAlarm(event, triggerTime, 60)
            }
        }

        if (event.reminder5mEnabled) {
            val triggerTime = event.startTime - FIVE_MINUTES_MS
            if (triggerTime > now) {
                scheduleAlarm(event, triggerTime, 5)
            }
        }
    }

    fun cancelReminders(eventId: String) {
        cancelAlarm(eventId, 60)
        cancelAlarm(eventId, 5)
    }

    fun scheduleAllReminders(events: List<CachedEventEntity>) {
        for (event in events) {
            scheduleReminders(event)
        }
    }

    private fun scheduleAlarm(event: CachedEventEntity, triggerTime: Long, minutesBefore: Int) {
        val intent = createIntent(event, minutesBefore)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(event.googleEventId, minutesBefore),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            pendingIntent,
        )
    }

    private fun cancelAlarm(eventId: String, minutesBefore: Int) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(eventId, minutesBefore),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        pendingIntent?.let { alarmManager.cancel(it) }
    }

    private fun createIntent(event: CachedEventEntity, minutesBefore: Int): Intent {
        return Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_EVENT_ID, event.googleEventId)
            putExtra(EXTRA_EVENT_TITLE, event.title)
            putExtra(EXTRA_REMINDER_MODE, event.reminderMode)
            putExtra(EXTRA_MINUTES_BEFORE, minutesBefore)
        }
    }

    private fun requestCode(eventId: String, minutesBefore: Int): Int {
        return (eventId.hashCode() * 31 + minutesBefore)
    }
}
