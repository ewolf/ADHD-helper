package com.roundtooit.data.repository

import com.roundtooit.data.local.dao.TaskDao
import com.roundtooit.data.local.entity.TaskEntity
import com.roundtooit.data.remote.YapiClient
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val yapiClient: YapiClient,
) {
    fun getTopTasks(count: Int = 3): Flow<List<TaskEntity>> = taskDao.getTopTasks(count)

    fun getAllTasks(): Flow<List<TaskEntity>> = taskDao.getAllTasks()

    suspend fun addTask(title: String, description: String) {
        val position = taskDao.getMaxQueuePosition() + 1
        val now = System.currentTimeMillis()
        val tempId = "local_${UUID.randomUUID()}"

        val task = TaskEntity(
            serverId = tempId,
            title = title,
            description = description,
            queuePosition = position,
            created = now,
            updated = now,
            pendingSync = true,
            pendingAction = "add",
        )
        taskDao.insert(task)
    }

    suspend fun completeTask(task: TaskEntity) {
        taskDao.delete(task)
        // Re-number remaining tasks
        val all = taskDao.getPendingSyncTasks() // trigger re-query
        if (!task.serverId.startsWith("local_")) {
            // Mark for server-side completion
            taskDao.insert(task.copy(pendingSync = true, pendingAction = "complete"))
            taskDao.delete(task.copy(pendingSync = true, pendingAction = "complete"))
        }
    }

    suspend fun delayTask(task: TaskEntity) {
        val maxPos = taskDao.getMaxQueuePosition()
        taskDao.update(
            task.copy(
                queuePosition = maxPos + 1,
                updated = System.currentTimeMillis(),
                pendingSync = true,
                pendingAction = "delay",
            )
        )
    }

    suspend fun syncFromServer() {
        try {
            val response = yapiClient.call("sync")
            val objects = yapiClient.parseObjects(response)

            // Parse tasks from response
            val respObj = response.resp
            // Server returns {tasks: [...], notes: [...], prefs: {...}, data_version: N}
            // Tasks are returned as object references that we resolve from the objects map
            val tasks = parseTasksFromResponse(response, objects)
            if (tasks != null) {
                taskDao.deleteAll()
                taskDao.insertAll(tasks)
            }
        } catch (e: Exception) {
            // Offline — keep local data
        }
    }

    private fun parseTasksFromResponse(
        response: com.roundtooit.data.remote.YapiResponse,
        objects: Map<String, com.roundtooit.data.remote.YapiObject>,
    ): List<TaskEntity>? {
        val taskObjects = objects.values
            .filter { it.className.endsWith("Task") }
            .mapIndexed { index, obj ->
                TaskEntity(
                    serverId = obj.objId,
                    title = obj.fields["title"] ?: "",
                    description = obj.fields["description"] ?: "",
                    queuePosition = index,
                    created = parseTimestamp(obj.fields["created"]),
                    updated = parseTimestamp(obj.fields["updated"]),
                )
            }
        return taskObjects.ifEmpty { null }
    }

    private fun parseTimestamp(value: String?): Long {
        if (value == null) return System.currentTimeMillis()
        return try {
            value.toLong()
        } catch (e: NumberFormatException) {
            System.currentTimeMillis()
        }
    }
}
