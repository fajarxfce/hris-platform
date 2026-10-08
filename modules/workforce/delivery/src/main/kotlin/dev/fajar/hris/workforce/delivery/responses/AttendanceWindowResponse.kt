package dev.fajar.hris.workforce.delivery.responses

import java.time.Instant
import java.util.UUID

data class AttendanceWindowResponse(
    val id: UUID,
    val employeeId: UUID,
    val deviceId: UUID,
    val issuedAt: Instant,
    val expiresAt: Instant,
)
