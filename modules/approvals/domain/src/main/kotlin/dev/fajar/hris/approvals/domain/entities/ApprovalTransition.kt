package dev.fajar.hris.approvals.domain.entities

import java.util.UUID

data class ApprovalTransition(
    val status: ApprovalStatus,
    val currentStep: Int,
    val decision: ApprovalDecision,
    val decidingFor: UUID,
)
