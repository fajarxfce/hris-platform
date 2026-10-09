package dev.fajar.hris.communications.domain.entities

import java.time.Instant
import java.util.UUID

data class InboxPushLease(
    val dispatch: InboxPushDispatch,
    val owner: UUID,
    val token: UUID,
    val until: Instant,
)
