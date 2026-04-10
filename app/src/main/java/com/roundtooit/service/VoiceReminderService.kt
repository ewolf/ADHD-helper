package com.roundtooit.service

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.os.IBinder
import android.speech.tts.TextToSpeech
import java.util.*

class VoiceReminderService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val EXTRA_TEXT = "text"
    }

    private var tts: TextToSpeech? = null
    private var pendingText: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT)
        if (text != null) {
            pendingText = text
            tts?.let { speakIfReady(it) }
        }
        return START_NOT_STICKY
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()

            // Set audio attributes to play on all active channels
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts?.setAudioAttributes(attrs)

            tts?.let { speakIfReady(it) }
        }
    }

    private fun speakIfReady(engine: TextToSpeech) {
        val text = pendingText ?: return
        pendingText = null

        engine.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                stopSelf()
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                stopSelf()
            }
        })

        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reminder")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
