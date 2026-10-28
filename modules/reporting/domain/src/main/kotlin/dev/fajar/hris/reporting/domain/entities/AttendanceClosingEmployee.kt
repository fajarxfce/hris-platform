package dev.fajar.hris.reporting.domain.entities

import java.util.UUID

data class AttendanceClosingEmployee(
    val employeeId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val closing: AttendanceClosingReference,
    val adjustment: AttendanceClosingAdjustmentReference?,
    val counts: AttendanceClosingCounts,
)
