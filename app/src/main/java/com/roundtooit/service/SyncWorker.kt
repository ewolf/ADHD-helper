package com.roundtooit.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.roundtooit.data.remote.YapiClient
import com.roundtooit.data.repository.CalendarRepository
import com.roundtooit.data.repository.NoteRepository
import com.roundtooit.data.repository.TaskRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val yapiClient: YapiClient,
    private val taskRepository: TaskRepository,
    private val noteRepository: NoteRepository,
    private val calendarRepository: CalendarRepository,
    private val reminderScheduler: ReminderScheduler,
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "roundtooit_sync"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(10, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            if (!yapiClient.isLoggedIn) return Result.success()

            // Sync tasks and notes with YAPI server
            taskRepository.syncFromServer()
            noteRepository.syncFromServer()

            // Reschedule reminders based on updated calendar
            val events = calendarRepository.getUpcomingEvents()
            reminderScheduler.scheduleAllReminders(events)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
