package com.roundtooit.data.local.dao

import androidx.room.*
import com.roundtooit.data.local.entity.CachedEmailEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedEmailDao {
    @Query("SELECT * FROM cached_emails WHERE isDone = 0 ORDER BY receivedAt DESC")
    fun getActiveEmails(): Flow<List<CachedEmailEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(emails: List<CachedEmailEntity>)

    @Query("UPDATE cached_emails SET isDone = 1 WHERE gmailMessageId = :messageId")
    suspend fun markDone(messageId: String)

    @Query("SELECT gmailMessageId FROM cached_emails WHERE isDone = 1")
    suspend fun getDoneEmailIds(): List<String>

    @Query("DELETE FROM cached_emails")
    suspend fun deleteAll()
}
