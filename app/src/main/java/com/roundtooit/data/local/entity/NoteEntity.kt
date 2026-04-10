package com.roundtooit.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val serverId: String,
    val text: String,
    val created: Long,
    val updated: Long,
    val pendingSync: Boolean = false,
    val pendingAction: String? = null, // "add", "edit", "delete"
)
