package dev.fajar.hris.leave.domain.entities

import java.util.UUID

data class LeaveCalendarEntry(
    val requestId: UUID,
    val employeeId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val typeCode: String,
    val typeName: String,
    val portion: LeavePortion,
    val status: LeaveStatus,
    val version: Long,
)
