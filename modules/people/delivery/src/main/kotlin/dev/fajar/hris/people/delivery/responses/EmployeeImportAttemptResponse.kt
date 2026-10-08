package dev.fajar.hris.people.delivery.responses

import java.time.Instant
import java.util.UUID

data class EmployeeImportAttemptResponse(
    val jobId: UUID,
    val phase: String,
    val actorId: UUID,
    val createdAt: Instant,
)
