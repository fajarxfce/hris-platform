package dev.fajar.hris.workforce.domain.entities

import java.util.UUID

data class AttendanceRecord(
    val id: UUID,
    val employeeId: UUID,
    val version: Long,
    val day: AttendanceDay,
)
