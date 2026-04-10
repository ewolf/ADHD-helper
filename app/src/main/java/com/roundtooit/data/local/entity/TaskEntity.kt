package com.roundtooit.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val serverId: String,
    val title: String,
    val description: String,
    val queuePosition: Int,
    val created: Long,
    val updated: Long,
    val pendingSync: Boolean = false,
    val pendingAction: String? = null, // "add", "complete", "delay"
)
