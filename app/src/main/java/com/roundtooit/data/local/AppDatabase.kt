package com.roundtooit.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.roundtooit.data.local.dao.*
import com.roundtooit.data.local.entity.*

@Database(
    entities = [
        TaskEntity::class,
        NoteEntity::class,
        CachedEventEntity::class,
        CachedEmailEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun noteDao(): NoteDao
    abstract fun cachedEventDao(): CachedEventDao
    abstract fun cachedEmailDao(): CachedEmailDao
}
