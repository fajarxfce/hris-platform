package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

/** Immutable company DM intent. Neither a provider room nor a content-access capability. */
data class CollaborationDirectConversation(
    val id: UUID,
    val companyId: UUID,
    val firstAccountId: UUID,
    val secondAccountId: UUID,
    val createdBy: UUID,
    val createdAt: Instant,
    val version: Long = 0,
)
