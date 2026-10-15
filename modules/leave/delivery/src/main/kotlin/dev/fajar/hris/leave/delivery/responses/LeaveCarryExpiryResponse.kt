package dev.fajar.hris.leave.delivery.responses

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class LeaveCarryExpiryResponse(
    val id: UUID,
    val sourceKind: String,
    val sourceId: UUID,
    val employeeId: UUID?,
    val typeId: UUID,
    val typeCode: String,
    val typeName: String,
    val year: Int,
    val timezone: String,
    val validThrough: LocalDate,
    val expiresAt: Instant,
    val actorId: UUID,
    val createdAt: Instant,
    val reason: String,
    val totalEmployees: Int,
    val jobId: UUID,
    val version: Long,
    val status: String,
    val completedEmployees: Int,
    val failureCode: String?,
)
