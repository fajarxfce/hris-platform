package dev.fajar.hris.reporting.data.dto

import java.time.OffsetDateTime
import java.util.UUID

data class LeaveReportRequestRow(
    val id: UUID,
    val employeeId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val typeCode: String,
    val typeName: String,
    val status: String,
    val submittedAt: OffsetDateTime,
    val version: Long,
    val halfDays: Long,
    val requestHalfDays: Long,
    val invalid: Boolean,
)
