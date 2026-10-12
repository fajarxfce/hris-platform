package dev.fajar.hris.workforce.data.models

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class AttendanceExceptionRow(
    val employeeId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val workDate: LocalDate,
    val pendingCount: Int,
    val acceptedClockIns: Int,
    val acceptedClockOuts: Int,
    val correctionVersion: Long?,
    val lastReceivedAt: OffsetDateTime,
)
