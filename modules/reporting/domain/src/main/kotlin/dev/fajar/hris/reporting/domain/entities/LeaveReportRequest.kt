package dev.fajar.hris.reporting.domain.entities

import dev.fajar.hris.leave.domain.entities.LeaveStatus
import java.time.Instant
import java.util.UUID

data class LeaveReportRequest(
    val id: UUID,
    val employeeId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val typeCode: String,
    val typeName: String,
    val status: LeaveStatus,
    val submittedAt: Instant,
    val version: Long,
    val halfDays: Long,
    val requestHalfDays: Long,
)
