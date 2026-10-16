package dev.fajar.hris.workforce.delivery.responses

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class AttendanceAdjustmentSummaryResponse(
    val id: UUID,
    val revision: Long,
    val workDate: LocalDate,
    val correctionId: UUID,
    val actorId: UUID,
    val recordedAt: Instant,
    val reason: String,
)
