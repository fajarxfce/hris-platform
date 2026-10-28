package dev.fajar.hris.reporting.delivery.responses

import java.time.Instant
import java.util.UUID

data class AttendanceClosingAdjustmentReferenceResponse(
    val id: UUID,
    val revision: Long,
    val recordedAt: Instant,
)
