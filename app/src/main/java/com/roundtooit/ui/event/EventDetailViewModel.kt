package com.roundtooit.ui.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roundtooit.data.local.dao.CachedEventDao
import com.roundtooit.data.local.entity.CachedEventEntity
import com.roundtooit.data.repository.CalendarRepository
import com.roundtooit.service.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EventDetailUiState(
    val event: CachedEventEntity? = null,
    val reminderMode: String = "voice",
    val reminder1hEnabled: Boolean = true,
    val reminder5mEnabled: Boolean = true,
)

@HiltViewModel
class EventDetailViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val cachedEventDao: CachedEventDao,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EventDetailUiState())
    val uiState: StateFlow<EventDetailUiState> = _uiState

    fun loadEvent(eventId: String) {
        viewModelScope.launch {
            val event = cachedEventDao.getById(eventId) ?: return@launch
            _uiState.value = EventDetailUiState(
                event = event,
                reminderMode = event.reminderMode,
                reminder1hEnabled = event.reminder1hEnabled,
                reminder5mEnabled = event.reminder5mEnabled,
            )
        }
    }

    fun setReminderMode(mode: String) {
        _uiState.value = _uiState.value.copy(reminderMode = mode)
        saveAndReschedule()
    }

    fun setReminder1hEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(reminder1hEnabled = enabled)
        saveAndReschedule()
    }

    fun setReminder5mEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(reminder5mEnabled = enabled)
        saveAndReschedule()
    }

    private fun saveAndReschedule() {
        val state = _uiState.value
        val event = state.event ?: return

        viewModelScope.launch {
            calendarRepository.updateReminderSettings(
                eventId = event.googleEventId,
                reminderMode = state.reminderMode,
                reminder1hEnabled = state.reminder1hEnabled,
                reminder5mEnabled = state.reminder5mEnabled,
            )

            val updated = event.copy(
                reminderMode = state.reminderMode,
                reminder1hEnabled = state.reminder1hEnabled,
                reminder5mEnabled = state.reminder5mEnabled,
            )
            reminderScheduler.scheduleReminders(updated)
        }
    }
}
