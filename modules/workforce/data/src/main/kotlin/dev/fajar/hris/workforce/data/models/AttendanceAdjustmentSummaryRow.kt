package dev.fajar.hris.workforce.data.models

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class AttendanceAdjustmentSummaryRow(
    val id: UUID,
    val revision: Long,
    val workDate: LocalDate,
    val correctionId: UUID,
    val actorId: UUID,
    val recordedAt: OffsetDateTime,
    val reason: String,
)
