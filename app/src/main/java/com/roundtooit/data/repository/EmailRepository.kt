package com.roundtooit.data.repository

import com.roundtooit.data.local.dao.CachedEmailDao
import com.roundtooit.data.local.entity.CachedEmailEntity
import com.roundtooit.data.remote.YapiClient
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmailRepository @Inject constructor(
    private val cachedEmailDao: CachedEmailDao,
    private val yapiClient: YapiClient,
) {
    fun getActiveEmails(): Flow<List<CachedEmailEntity>> = cachedEmailDao.getActiveEmails()

    suspend fun cacheEmails(emails: List<CachedEmailEntity>) {
        cachedEmailDao.insertAll(emails)
    }

    suspend fun markDone(messageId: String) {
        cachedEmailDao.markDone(messageId)
        try {
            yapiClient.call("markEmailDone", mapOf("gmail_id" to messageId))
        } catch (_: Exception) { }
    }

    suspend fun markAllDone() {
        val ids = cachedEmailDao.getActiveEmailIds()
        if (ids.isEmpty()) return
        cachedEmailDao.markAllDone()
        try {
            yapiClient.call("markEmailDone", mapOf("gmail_ids" to ids))
        } catch (_: Exception) { }
    }
}
