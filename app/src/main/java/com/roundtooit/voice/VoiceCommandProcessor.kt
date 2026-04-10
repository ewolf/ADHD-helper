package com.roundtooit.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class VoiceCommand {
    data class AddTask(val title: String) : VoiceCommand()
    data class AddNote(val text: String) : VoiceCommand()
    data class SearchNotes(val query: String) : VoiceCommand()
    data object CompleteTask : VoiceCommand()
    data object DelayTask : VoiceCommand()
    data class Unknown(val text: String) : VoiceCommand()
}

data class VoiceState(
    val isListening: Boolean = false,
    val lastCommand: VoiceCommand? = null,
    val error: String? = null,
)

@Singleton
class VoiceCommandProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _state = MutableStateFlow(VoiceState())
    val state: StateFlow<VoiceState> = _state

    private var speechRecognizer: SpeechRecognizer? = null

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = _state.value.copy(error = "Speech recognition not available")
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _state.value = _state.value.copy(isListening = true, error = null)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    val command = parseCommand(text)
                    _state.value = VoiceState(isListening = false, lastCommand = command)
                }

                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that"
                        SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        else -> "Recognition error"
                    }
                    _state.value = VoiceState(isListening = false, error = msg)
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        _state.value = _state.value.copy(isListening = false)
    }

    fun clearCommand() {
        _state.value = _state.value.copy(lastCommand = null, error = null)
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun parseCommand(text: String): VoiceCommand {
        val lower = text.lowercase().trim()

        return when {
            lower.startsWith("add task ") ->
                VoiceCommand.AddTask(text.substringAfter("add task ").substringAfter("Add task ").trim())

            lower.startsWith("new task ") ->
                VoiceCommand.AddTask(text.substringAfter("new task ").substringAfter("New task ").trim())

            lower.startsWith("add note ") ->
                VoiceCommand.AddNote(text.substringAfter("add note ").substringAfter("Add note ").trim())

            lower.startsWith("new note ") ->
                VoiceCommand.AddNote(text.substringAfter("new note ").substringAfter("New note ").trim())

            lower.startsWith("search notes ") || lower.startsWith("find note ") ->
                VoiceCommand.SearchNotes(
                    text.substringAfter("search notes ").substringAfter("find note ").trim()
                )

            lower == "complete task" || lower == "done" || lower == "finish task" ->
                VoiceCommand.CompleteTask

            lower == "delay task" || lower == "skip task" || lower == "later" ->
                VoiceCommand.DelayTask

            else -> VoiceCommand.Unknown(text)
        }
    }
}
