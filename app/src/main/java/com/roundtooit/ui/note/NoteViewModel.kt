package com.roundtooit.ui.note

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roundtooit.data.local.entity.NoteEntity
import com.roundtooit.data.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NoteUiState(
    val notes: List<NoteEntity> = emptyList(),
    val searchQuery: String = "",
    val newNoteText: String = "",
    val showAddForm: Boolean = false,
    val editingNote: NoteEntity? = null,
    val editText: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NoteViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
) : ViewModel() {

    private val _formState = MutableStateFlow(NoteUiState())

    val uiState: StateFlow<NoteUiState> = _formState
        .flatMapLatest { form ->
            val notesFlow = if (form.searchQuery.isBlank()) {
                noteRepository.getAllNotes()
            } else {
                noteRepository.searchNotes(form.searchQuery)
            }
            notesFlow.map { notes -> form.copy(notes = notes) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NoteUiState(),
        )

    fun onSearchQueryChanged(value: String) {
        _formState.value = _formState.value.copy(searchQuery = value)
    }

    fun onNewNoteTextChanged(value: String) {
        _formState.value = _formState.value.copy(newNoteText = value)
    }

    fun toggleAddForm() {
        _formState.value = _formState.value.copy(
            showAddForm = !_formState.value.showAddForm,
            newNoteText = "",
        )
    }

    fun addNote() {
        val text = _formState.value.newNoteText.trim()
        if (text.isBlank()) return

        viewModelScope.launch {
            noteRepository.addNote(text)
            _formState.value = _formState.value.copy(
                newNoteText = "",
                showAddForm = false,
            )
        }
    }

    fun startEditing(note: NoteEntity) {
        _formState.value = _formState.value.copy(
            editingNote = note,
            editText = note.text,
        )
    }

    fun onEditTextChanged(value: String) {
        _formState.value = _formState.value.copy(editText = value)
    }

    fun saveEdit() {
        val state = _formState.value
        val note = state.editingNote ?: return
        if (state.editText.isBlank()) return

        viewModelScope.launch {
            noteRepository.editNote(note, state.editText.trim())
            _formState.value = _formState.value.copy(editingNote = null, editText = "")
        }
    }

    fun cancelEdit() {
        _formState.value = _formState.value.copy(editingNote = null, editText = "")
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            noteRepository.deleteNote(note)
        }
    }
}
