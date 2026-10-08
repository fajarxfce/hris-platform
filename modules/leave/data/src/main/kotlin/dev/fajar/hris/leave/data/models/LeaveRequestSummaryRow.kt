package dev.fajar.hris.leave.data.models

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class LeaveRequestSummaryRow(
    val id: UUID,
    val employeeId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val typeCode: String,
    val typeName: String,
    val from: LocalDate,
    val until: LocalDate,
    val halfDays: Int,
    val status: String,
    val submittedAt: OffsetDateTime,
    val version: Long,
)
