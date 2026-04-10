package com.roundtooit

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RoundTooitApplication : Application() {

    companion object {
        const val REMINDER_CHANNEL_ID = "reminders"
        const val SYNC_CHANNEL_ID = "sync"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val reminderChannel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            "Event Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Voice and notification reminders for calendar events"
        }

        val syncChannel = NotificationChannel(
            SYNC_CHANNEL_ID,
            "Sync",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background sync notifications"
        }

        manager.createNotificationChannel(reminderChannel)
        manager.createNotificationChannel(syncChannel)
    }
}
