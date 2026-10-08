package dev.fajar.hris.workforce.delivery.requests

import dev.fajar.hris.workforce.domain.entities.AttendanceKind
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class AttendanceCaptureRequest(
    val id: UUID,
    val workDate: LocalDate,
    val kind: AttendanceKind,
    val capturedAt: Instant,
    val deviceId: UUID,
    val windowId: UUID? = null,
    val offline: Boolean,
    val location: AttendanceLocationRequest? = null,
)
