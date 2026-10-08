package dev.fajar.hris.workforce.delivery.responses

import java.time.Instant
import java.util.UUID

data class AttendanceEntryResponse(
    val id: UUID,
    val kind: String,
    val capturedAt: Instant,
    val receivedAt: Instant,
    val offline: Boolean,
    val location: AttendanceLocationResponse?,
    val schedule: ScheduledDayResponse,
    val status: String,
    val initialStatus: String,
    val issues: Set<String>,
    val review: AttendanceReviewResponse?,
    val version: Long,
)
