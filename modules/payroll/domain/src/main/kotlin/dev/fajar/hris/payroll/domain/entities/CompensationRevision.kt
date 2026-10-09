package dev.fajar.hris.payroll.domain.entities

import java.time.Instant
import java.util.UUID

data class CompensationRevision(
    val compensation: EmployeeCompensation,
    val actorId: UUID,
    val reason: String,
    val recordedAt: Instant,
)
