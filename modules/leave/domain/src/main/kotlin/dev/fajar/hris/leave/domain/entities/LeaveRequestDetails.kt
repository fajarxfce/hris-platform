package dev.fajar.hris.leave.domain.entities

import dev.fajar.hris.approvals.domain.entities.ApprovalRequest
import dev.fajar.hris.core.domain.Page

data class LeaveRequestDetails(
    val request: LeaveRequest,
    val approval: ApprovalRequest,
    val cancellation: ApprovalRequest?,
    val history: Page<LeaveRequestChange>,
    val availableActions: Set<LeaveAction>,
)
