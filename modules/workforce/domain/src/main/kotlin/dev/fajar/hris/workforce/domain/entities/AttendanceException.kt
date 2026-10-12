package dev.fajar.hris.workforce.domain.entities

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class AttendanceException(
    val employeeId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val workDate: LocalDate,
    val pendingCount: Int,
    val incomplete: Boolean,
    val correctionVersion: Long?,
    val lastReceivedAt: Instant,
)
