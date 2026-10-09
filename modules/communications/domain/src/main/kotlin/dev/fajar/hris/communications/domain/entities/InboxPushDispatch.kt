package dev.fajar.hris.communications.domain.entities

import java.time.Instant
import java.util.UUID

data class InboxPushDispatch(
    val companyId: UUID,
    val inboxId: UUID,
    val accountId: UUID,
    val publishedAt: Instant,
    val enqueuedAt: Instant,
    val state: InboxPushState,
    val availableAt: Instant,
    val cursorSessionId: UUID?,
    val targetSessionId: UUID?,
    val processedCount: Int,
    val acceptedCount: Int,
    val rejectedCount: Int,
    val attempts: Int,
)
