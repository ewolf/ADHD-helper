package com.roundtooit.data.local.dao

import androidx.room.*
import com.roundtooit.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY queuePosition ASC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY queuePosition ASC LIMIT :count")
    fun getTopTasks(count: Int = 3): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE pendingSync = 1")
    suspend fun getPendingSyncTasks(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE serverId = :serverId")
    suspend fun getById(serverId: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity)

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    @Query("SELECT COALESCE(MAX(queuePosition), 0) FROM tasks")
    suspend fun getMaxQueuePosition(): Int
}
