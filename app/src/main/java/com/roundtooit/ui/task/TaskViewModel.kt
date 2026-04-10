package com.roundtooit.ui.task

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roundtooit.data.local.entity.TaskEntity
import com.roundtooit.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TaskUiState(
    val tasks: List<TaskEntity> = emptyList(),
    val newTitle: String = "",
    val newDescription: String = "",
    val showAddForm: Boolean = false,
)

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
) : ViewModel() {

    private val _formState = MutableStateFlow(TaskUiState())

    val uiState: StateFlow<TaskUiState> = combine(
        taskRepository.getAllTasks(),
        _formState,
    ) { tasks, form ->
        form.copy(tasks = tasks)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TaskUiState(),
    )

    fun onTitleChanged(value: String) {
        if (value.length <= 500) {
            _formState.value = _formState.value.copy(newTitle = value)
        }
    }

    fun onDescriptionChanged(value: String) {
        _formState.value = _formState.value.copy(newDescription = value)
    }

    fun toggleAddForm() {
        _formState.value = _formState.value.copy(
            showAddForm = !_formState.value.showAddForm,
            newTitle = "",
            newDescription = "",
        )
    }

    fun addTask() {
        val state = _formState.value
        if (state.newTitle.isBlank()) return

        viewModelScope.launch {
            taskRepository.addTask(state.newTitle.trim(), state.newDescription.trim())
            _formState.value = _formState.value.copy(
                newTitle = "",
                newDescription = "",
                showAddForm = false,
            )
        }
    }

    fun completeTask(task: TaskEntity) {
        viewModelScope.launch { taskRepository.completeTask(task) }
    }

    fun delayTask(task: TaskEntity) {
        viewModelScope.launch { taskRepository.delayTask(task) }
    }
}
