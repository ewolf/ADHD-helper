package com.roundtooit.data.repository

import com.roundtooit.data.local.dao.NoteDao
import com.roundtooit.data.local.entity.NoteEntity
import com.roundtooit.data.remote.YapiClient
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepository @Inject constructor(
    private val noteDao: NoteDao,
    private val yapiClient: YapiClient,
) {
    fun getAllNotes(): Flow<List<NoteEntity>> = noteDao.getAllNotes()

    fun searchNotes(query: String): Flow<List<NoteEntity>> = noteDao.searchNotes(query)

    suspend fun addNote(text: String) {
        val now = System.currentTimeMillis()
        val tempId = "local_${UUID.randomUUID()}"
        val note = NoteEntity(
            serverId = tempId,
            text = text,
            created = now,
            updated = now,
            pendingSync = true,
            pendingAction = "add",
        )
        noteDao.insert(note)
    }

    suspend fun editNote(note: NoteEntity, newText: String) {
        noteDao.update(
            note.copy(
                text = newText,
                updated = System.currentTimeMillis(),
                pendingSync = true,
                pendingAction = "edit",
            )
        )
    }

    suspend fun deleteNote(note: NoteEntity) {
        noteDao.delete(note)
    }

    suspend fun syncFromServer() {
        try {
            val response = yapiClient.call("sync")
            val objects = yapiClient.parseObjects(response)

            val notes = objects.values
                .filter { it.className.endsWith("Note") }
                .map { obj ->
                    NoteEntity(
                        serverId = obj.objId,
                        text = obj.fields["text"] ?: "",
                        created = parseTimestamp(obj.fields["created"]),
                        updated = parseTimestamp(obj.fields["updated"]),
                    )
                }

            if (notes.isNotEmpty()) {
                noteDao.deleteAll()
                noteDao.insertAll(notes)
            }
        } catch (e: Exception) {
            // Offline — keep local data
        }
    }

    private fun parseTimestamp(value: String?): Long {
        if (value == null) return System.currentTimeMillis()
        return try { value.toLong() } catch (e: NumberFormatException) { System.currentTimeMillis() }
    }
}
