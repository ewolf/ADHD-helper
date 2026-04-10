package com.roundtooit.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roundtooit.data.local.entity.CachedEmailEntity
import com.roundtooit.data.local.entity.CachedEventEntity
import com.roundtooit.data.local.entity.TaskEntity
import com.roundtooit.data.repository.CalendarRepository
import com.roundtooit.data.repository.EmailRepository
import com.roundtooit.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class PeriodEvents(
    val morning: List<String> = emptyList(),
    val afternoon: List<String> = emptyList(),
    val evening: List<String> = emptyList(),
)

data class UpcomingDayGroup(
    val label: String,
    val periods: PeriodEvents,
)

data class DashboardUiState(
    val todayEvents: List<CachedEventEntity> = emptyList(),
    val upcomingDays: List<UpcomingDayGroup> = emptyList(),
    val unreadEmails: List<CachedEmailEntity> = emptyList(),
    val topTasks: List<TaskEntity> = emptyList(),
    val isLoading: Boolean = false,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val calendarRepository: CalendarRepository,
    private val emailRepository: EmailRepository,
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)

    val uiState: StateFlow<DashboardUiState> = combine(
        calendarRepository.getTodayEvents(),
        calendarRepository.getNextFutureEvents(),
        emailRepository.getActiveEmails(),
        taskRepository.getTopTasks(),
        _isLoading,
    ) { todayEvents, futureEvents, emails, tasks, loading ->
        DashboardUiState(
            todayEvents = todayEvents,
            upcomingDays = groupByDay(futureEvents),
            unreadEmails = emails,
            topTasks = tasks,
            isLoading = loading,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(isLoading = true),
    )

    fun completeTask(task: TaskEntity) {
        viewModelScope.launch {
            taskRepository.completeTask(task)
        }
    }

    fun delayTask(task: TaskEntity) {
        viewModelScope.launch {
            taskRepository.delayTask(task)
        }
    }

    fun markEmailDone(email: CachedEmailEntity) {
        viewModelScope.launch {
            emailRepository.markDone(email.gmailMessageId)
        }
    }

    private fun groupByDay(events: List<CachedEventEntity>): List<UpcomingDayGroup> {
        val now = Calendar.getInstance()
        now.set(Calendar.HOUR_OF_DAY, 0)
        now.set(Calendar.MINUTE, 0)
        now.set(Calendar.SECOND, 0)
        now.set(Calendar.MILLISECOND, 0)
        val todayStart = now.timeInMillis

        data class DayKey(val year: Int, val dayOfYear: Int)

        val dayMap = linkedMapOf<DayKey, MutableList<CachedEventEntity>>()

        for (event in events) {
            val cal = Calendar.getInstance().apply { timeInMillis = event.startTime }
            val key = DayKey(cal.get(Calendar.YEAR), cal.get(Calendar.DAY_OF_YEAR))
            dayMap.getOrPut(key) { mutableListOf() }.add(event)
            if (dayMap.size >= 3) break
        }

        return dayMap.map { (key, dayEvents) ->
            val eventCal = Calendar.getInstance().apply {
                set(Calendar.YEAR, key.year)
                set(Calendar.DAY_OF_YEAR, key.dayOfYear)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val diffMs = eventCal.timeInMillis - todayStart
            val daysUntil = TimeUnit.MILLISECONDS.toDays(diffMs).toInt()

            val label = when {
                daysUntil == 1 -> "Tomorrow"
                daysUntil < 7 -> "In $daysUntil days"
                else -> {
                    val dow = eventCal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault())
                    val month = eventCal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault())
                    val day = eventCal.get(Calendar.DAY_OF_MONTH)
                    "$dow, $month $day"
                }
            }

            val morning = mutableListOf<String>()
            val afternoon = mutableListOf<String>()
            val evening = mutableListOf<String>()

            for (ev in dayEvents) {
                val hour = Calendar.getInstance().apply { timeInMillis = ev.startTime }.get(Calendar.HOUR_OF_DAY)
                when {
                    hour < 12 -> morning.add(ev.title)
                    hour < 17 -> afternoon.add(ev.title)
                    else -> evening.add(ev.title)
                }
            }

            UpcomingDayGroup(
                label = label,
                periods = PeriodEvents(morning, afternoon, evening),
            )
        }
    }
}
