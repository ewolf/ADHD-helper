package com.roundtooit.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_emails")
data class CachedEmailEntity(
    @PrimaryKey val gmailMessageId: String,
    val senderEmail: String,
    val senderName: String? = null,
    val subject: String,
    val snippet: String,
    val receivedAt: Long,
    val isUnread: Boolean = true,
    val isDone: Boolean = false,
)
