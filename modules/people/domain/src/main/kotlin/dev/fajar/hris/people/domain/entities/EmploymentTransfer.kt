package dev.fajar.hris.people.domain.entities

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class EmploymentTransfer(
    val id: UUID,
    val sourceCompanyId: UUID,
    val sourceEmploymentId: UUID,
    val targetCompanyId: UUID,
    val targetEmploymentId: UUID,
    val personId: UUID,
    val sourceVersion: Long,
    val effectiveDate: LocalDate,
    val actorId: UUID,
    val reason: String,
    val recordedAt: Instant,
)
