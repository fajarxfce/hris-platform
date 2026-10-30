package dev.fajar.hris.communications.domain.entities

import java.time.Instant
import java.util.UUID

data class InboxEmailDispatch(
    val companyId: UUID,
    val inboxId: UUID,
    val accountId: UUID,
    val publishedAt: Instant,
    val enqueuedAt: Instant,
    val state: InboxEmailState,
    val availableAt: Instant,
    val attempts: Int,
)
