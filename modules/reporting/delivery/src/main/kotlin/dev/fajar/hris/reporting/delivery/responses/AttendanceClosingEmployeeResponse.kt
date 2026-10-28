package dev.fajar.hris.reporting.delivery.responses

import java.util.UUID

data class AttendanceClosingEmployeeResponse(
    val employeeId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val closing: AttendanceClosingReferenceResponse,
    val adjustment: AttendanceClosingAdjustmentReferenceResponse?,
    val counts: AttendanceClosingCountsResponse,
)
