package com.roundtooit.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.roundtooit.data.local.dao.CachedEventDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var cachedEventDao: CachedEventDao
    @Inject lateinit var reminderScheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // Reschedule all upcoming reminders after reboot
        CoroutineScope(Dispatchers.IO).launch {
            val events = cachedEventDao.getUpcomingEvents(System.currentTimeMillis())
            reminderScheduler.scheduleAllReminders(events)
        }
    }
}
