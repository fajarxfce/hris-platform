package dev.fajar.hris.leave.delivery.responses

import java.util.UUID

data class LeaveCalendarEntryResponse(
    val requestId: UUID,
    val employeeId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val typeCode: String,
    val typeName: String,
    val portion: String,
    val status: String,
    val version: Long,
)
