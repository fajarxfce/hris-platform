package dev.fajar.hris.leave.data.models

import java.util.UUID

data class LeaveCalendarEntryRow(
    val requestId: UUID,
    val employeeId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val typeCode: String,
    val typeName: String,
    val portionMask: Int,
    val status: String,
    val version: Long,
)
