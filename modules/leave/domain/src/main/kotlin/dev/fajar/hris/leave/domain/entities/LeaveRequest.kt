package dev.fajar.hris.leave.domain.entities

import java.time.Instant
import java.util.UUID

data class LeaveRequest(
    val id: UUID,
    val employeeId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val ownerAccountId: UUID?,
    val authorId: UUID,
    val submittedAt: Instant,
    val policy: LeavePolicySnapshot,
    val days: List<LeaveDay>,
    val reason: String,
    val status: LeaveStatus,
    val approvalId: UUID,
    val cancellationApprovalId: UUID?,
    val version: Long,
)
