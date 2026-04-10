package com.roundtooit.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.roundtooit.R
import com.roundtooit.RoundTooitApplication

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventTitle = intent.getStringExtra(ReminderScheduler.EXTRA_EVENT_TITLE) ?: return
        val reminderMode = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_MODE) ?: "voice"
        val minutesBefore = intent.getIntExtra(ReminderScheduler.EXTRA_MINUTES_BEFORE, 0)
        val eventId = intent.getStringExtra(ReminderScheduler.EXTRA_EVENT_ID) ?: return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Always post status bar notification
        val notifText = if (minutesBefore == 60) {
            "$eventTitle in 1 hour"
        } else {
            "$eventTitle in $minutesBefore minutes"
        }

        val notification = NotificationCompat.Builder(context, RoundTooitApplication.REMINDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Upcoming Event")
            .setContentText(notifText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notifId = (eventId.hashCode() * 31 + minutesBefore)
        notificationManager.notify(notifId, notification)

        // Honor DND and silent mode
        val ringerMode = audioManager.ringerMode
        val isDnd = notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        if (isDnd || ringerMode == AudioManager.RINGER_MODE_SILENT) return

        when (reminderMode) {
            "voice" -> {
                val voiceIntent = Intent(context, VoiceReminderService::class.java).apply {
                    putExtra(VoiceReminderService.EXTRA_TEXT, notifText)
                }
                context.startService(voiceIntent)
            }
            "chime" -> {
                // Use default notification sound
                val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = android.media.RingtoneManager.getRingtone(context, uri)
                ringtone?.play()
            }
            "buzz" -> {
                vibrate(context)
            }
            "both" -> {
                val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = android.media.RingtoneManager.getRingtone(context, uri)
                ringtone?.play()
                vibrate(context)
            }
        }
    }

    private fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
