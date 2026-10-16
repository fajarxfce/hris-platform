package dev.fajar.hris.workforce.domain.entities

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class AttendanceAdjustmentSummary(
    val id: UUID,
    val revision: Long,
    val workDate: LocalDate,
    val correctionId: UUID,
    val actorId: UUID,
    val recordedAt: Instant,
    val reason: String,
)
