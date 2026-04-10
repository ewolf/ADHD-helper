package com.roundtooit.data.remote

import com.roundtooit.data.local.dao.CachedEmailDao
import com.roundtooit.data.local.entity.CachedEmailEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles Gmail API integration.
 *
 * Requires a configured Google API credential with Gmail scopes:
 * - https://www.googleapis.com/auth/gmail.readonly
 * - https://www.googleapis.com/auth/gmail.labels
 * - https://www.googleapis.com/auth/gmail.modify
 *
 * The actual Google API service instance should be provided after
 * the user completes Google OAuth sign-in.
 */
@Singleton
class GmailRepository @Inject constructor(
    private val cachedEmailDao: CachedEmailDao,
) {
    private var knownSenders: Set<String> = emptySet()
    private var adhdLabelId: String? = null

    /**
     * Build the set of email addresses the user has ever sent to.
     * Call this once after Google auth, then periodically.
     *
     * Implementation: Query Gmail API for messages in SENT folder,
     * extract "To" addresses, and cache them.
     */
    suspend fun refreshKnownSenders(gmailService: com.google.api.services.gmail.Gmail) {
        val sentMessages = gmailService.users().messages()
            .list("me")
            .setLabelIds(listOf("SENT"))
            .setMaxResults(500)
            .execute()

        val senders = mutableSetOf<String>()
        sentMessages.messages?.forEach { msg ->
            try {
                val full = gmailService.users().messages()
                    .get("me", msg.id)
                    .setFormat("metadata")
                    .setMetadataHeaders(listOf("To", "Cc"))
                    .execute()

                full.payload?.headers?.forEach { header ->
                    if (header.name.equals("To", ignoreCase = true) ||
                        header.name.equals("Cc", ignoreCase = true)
                    ) {
                        extractEmails(header.value).forEach { senders.add(it.lowercase()) }
                    }
                }
            } catch (_: Exception) { }
        }
        knownSenders = senders
    }

    /**
     * Poll for unread emails from known senders and cache them.
     * Also applies the ADHD label.
     */
    suspend fun pollUnreadFromKnownSenders(gmailService: com.google.api.services.gmail.Gmail) {
        ensureAdhdLabel(gmailService)

        val unread = gmailService.users().messages()
            .list("me")
            .setQ("is:unread in:inbox")
            .setMaxResults(50)
            .execute()

        val emails = mutableListOf<CachedEmailEntity>()

        unread.messages?.forEach { msg ->
            try {
                val full = gmailService.users().messages()
                    .get("me", msg.id)
                    .setFormat("metadata")
                    .setMetadataHeaders(listOf("From", "Subject"))
                    .execute()

                val fromHeader = full.payload?.headers
                    ?.firstOrNull { it.name.equals("From", ignoreCase = true) }
                    ?.value ?: return@forEach

                val fromEmail = extractEmails(fromHeader).firstOrNull()?.lowercase() ?: return@forEach

                // Only include emails from known senders
                if (fromEmail !in knownSenders) return@forEach

                val subject = full.payload?.headers
                    ?.firstOrNull { it.name.equals("Subject", ignoreCase = true) }
                    ?.value ?: "(No subject)"

                val senderName = extractName(fromHeader)

                emails.add(
                    CachedEmailEntity(
                        gmailMessageId = msg.id,
                        senderEmail = fromEmail,
                        senderName = senderName,
                        subject = subject,
                        snippet = full.snippet ?: "",
                        receivedAt = full.internalDate ?: System.currentTimeMillis(),
                    )
                )

                // Apply ADHD label
                adhdLabelId?.let { labelId ->
                    gmailService.users().messages()
                        .modify(
                            "me", msg.id,
                            com.google.api.services.gmail.model.ModifyMessageRequest()
                                .setAddLabelIds(listOf(labelId))
                        )
                        .execute()
                }
            } catch (_: Exception) { }
        }

        if (emails.isNotEmpty()) {
            cachedEmailDao.insertAll(emails)
        }
    }

    private suspend fun ensureAdhdLabel(gmailService: com.google.api.services.gmail.Gmail) {
        if (adhdLabelId != null) return

        val labels = gmailService.users().labels().list("me").execute()
        val existing = labels.labels?.firstOrNull { it.name == "ADHD" }

        adhdLabelId = if (existing != null) {
            existing.id
        } else {
            val newLabel = com.google.api.services.gmail.model.Label()
                .setName("ADHD")
                .setLabelListVisibility("labelShow")
                .setMessageListVisibility("show")
            val created = gmailService.users().labels().create("me", newLabel).execute()
            created.id
        }
    }

    private fun extractEmails(header: String): List<String> {
        val emailRegex = Regex("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}")
        return emailRegex.findAll(header).map { it.value }.toList()
    }

    private fun extractName(fromHeader: String): String? {
        val match = Regex("^\"?([^\"<]+)\"?\\s*<").find(fromHeader)
        return match?.groupValues?.get(1)?.trim()
    }
}
