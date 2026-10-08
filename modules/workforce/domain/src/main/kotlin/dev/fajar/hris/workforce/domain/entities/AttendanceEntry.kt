package dev.fajar.hris.workforce.domain.entities

import java.time.Instant
import java.util.UUID

data class AttendanceEntry(
    val capture: AttendanceCapture,
    val accountId: UUID,
    val receivedAt: Instant,
    val schedule: ScheduledDay,
    val initial: AttendanceAssessment,
    val status: AttendanceStatus,
    val review: AttendanceReview?,
    val version: Long,
)
