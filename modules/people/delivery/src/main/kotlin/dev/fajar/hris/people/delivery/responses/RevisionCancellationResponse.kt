package dev.fajar.hris.people.delivery.responses

import java.time.Instant
import java.util.UUID

data class RevisionCancellationResponse(
    val actorId: UUID,
    val reason: String,
    val recordedAt: Instant,
)
