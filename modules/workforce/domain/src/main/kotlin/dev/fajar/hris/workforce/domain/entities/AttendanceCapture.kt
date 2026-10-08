package dev.fajar.hris.workforce.domain.entities

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class AttendanceCapture(
    val id: UUID,
    val employeeId: UUID,
    val workDate: LocalDate,
    val kind: AttendanceKind,
    val capturedAt: Instant,
    val deviceId: UUID,
    val windowId: UUID?,
    val offline: Boolean,
    val location: AttendanceLocation?,
)
