package dev.fajar.hris.leave.domain.entities

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class LeaveRequestSummary(
    val id: UUID,
    val employeeId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val typeCode: String,
    val typeName: String,
    val from: LocalDate,
    val until: LocalDate,
    val halfDays: Int,
    val status: LeaveStatus,
    val submittedAt: Instant,
    val version: Long,
)
