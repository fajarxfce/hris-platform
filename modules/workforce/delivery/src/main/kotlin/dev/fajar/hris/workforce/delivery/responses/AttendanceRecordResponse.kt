package dev.fajar.hris.workforce.delivery.responses

import java.util.UUID

data class AttendanceRecordResponse(
    val id: UUID,
    val employeeId: UUID,
    val version: Long,
    val day: AttendanceDayResponse,
)
