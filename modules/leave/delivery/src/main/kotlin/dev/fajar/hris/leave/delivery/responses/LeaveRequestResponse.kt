package dev.fajar.hris.leave.delivery.responses

import dev.fajar.hris.core.domain.Page
import java.time.Instant
import java.util.UUID

data class LeaveRequestResponse(
    val id: UUID,
    val employeeId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val ownerAccountId: UUID?,
    val authorId: UUID,
    val submittedAt: Instant,
    val policy: LeavePolicySnapshotResponse,
    val days: List<LeaveDayResponse>,
    val chargedDays: String,
    val reason: String,
    val status: String,
    val version: Long,
    val approval: LeaveWorkflowResponse,
    val cancellation: LeaveWorkflowResponse?,
    val history: Page<LeaveRequestChangeResponse>,
    val availableActions: Set<String>,
    val attachments: List<LeaveAttachmentResponse>,
)
