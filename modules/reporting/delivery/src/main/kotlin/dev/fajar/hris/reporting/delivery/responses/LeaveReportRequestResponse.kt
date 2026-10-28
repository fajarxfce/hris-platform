package dev.fajar.hris.reporting.delivery.responses

import java.time.Instant
import java.util.UUID

data class LeaveReportRequestResponse(
    val id: UUID,
    val employeeId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val typeCode: String,
    val typeName: String,
    val status: String,
    val submittedAt: Instant,
    val version: Long,
    val days: String,
    val requestDays: String,
)
