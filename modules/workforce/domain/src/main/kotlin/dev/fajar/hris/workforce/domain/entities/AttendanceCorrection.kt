package dev.fajar.hris.workforce.domain.entities

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class AttendanceCorrection(
    val id: UUID,
    val employeeId: UUID,
    val workDate: LocalDate,
    val clockIn: Instant?,
    val clockOut: Instant?,
    val breakMinutes: Int,
    val schedule: ScheduledDay,
    val actorId: UUID,
    val recordedAt: Instant,
    val reason: String,
    val version: Long,
)
