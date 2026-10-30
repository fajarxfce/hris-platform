package dev.fajar.hris.communications.domain.entities

import java.time.Instant
import java.util.UUID

data class InboxEmailLease(
    val dispatch: InboxEmailDispatch,
    val owner: UUID,
    val token: UUID,
    val until: Instant,
)
