package com.roundtooit.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_events")
data class CachedEventEntity(
    @PrimaryKey val googleEventId: String,
    val title: String,
    val description: String? = null,
    val startTime: Long,
    val endTime: Long,
    val location: String? = null,
    val reminderMode: String = "voice", // voice, chime, buzz, both, none
    val reminder1hEnabled: Boolean = true,
    val reminder5mEnabled: Boolean = true,
)
