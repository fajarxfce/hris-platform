package dev.fajar.hris.people.domain.entities

import java.time.Instant
import java.util.UUID

data class EmployeeImportAttempt(
    val jobId: UUID,
    val phase: EmployeeImportPhase,
    val actorId: UUID,
    val createdAt: Instant,
)
