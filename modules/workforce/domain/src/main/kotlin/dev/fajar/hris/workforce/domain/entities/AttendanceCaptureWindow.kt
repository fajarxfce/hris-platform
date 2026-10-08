package dev.fajar.hris.workforce.domain.entities

import java.time.Instant
import java.util.UUID

data class AttendanceCaptureWindow(
    val id: UUID,
    val employeeId: UUID,
    val accountId: UUID,
    val deviceId: UUID,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val consumedBy: UUID? = null,
)
