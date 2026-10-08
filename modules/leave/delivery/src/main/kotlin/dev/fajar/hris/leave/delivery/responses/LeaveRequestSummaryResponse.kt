package dev.fajar.hris.leave.delivery.responses

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class LeaveRequestSummaryResponse(
    val id: UUID,
    val employeeId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val typeCode: String,
    val typeName: String,
    val from: LocalDate,
    val until: LocalDate,
    val chargedDays: String,
    val status: String,
    val submittedAt: Instant,
    val version: Long,
)
