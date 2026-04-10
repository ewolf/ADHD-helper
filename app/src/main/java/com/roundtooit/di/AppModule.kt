package com.roundtooit.di

import android.content.Context
import androidx.room.Room
import com.roundtooit.data.local.AppDatabase
import com.roundtooit.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "roundtooit.db"
        ).build()
    }

    @Provides fun provideTaskDao(db: AppDatabase): TaskDao = db.taskDao()
    @Provides fun provideNoteDao(db: AppDatabase): NoteDao = db.noteDao()
    @Provides fun provideCachedEventDao(db: AppDatabase): CachedEventDao = db.cachedEventDao()
    @Provides fun provideCachedEmailDao(db: AppDatabase): CachedEmailDao = db.cachedEmailDao()
}
