package dev.fajar.hris.payroll.delivery.responses

import java.time.*
import java.util.UUID

data class PayrollReviewApprovalResponse(
    val id: UUID,
    val status: String,
    val version: Long,
    val currentStep: Int,
    val templateId: UUID,
    val templateRevision: Long,
    val stages: List<Set<UUID>>,
    val authorId: UUID,
    val excludedAccountIds: Set<UUID>,
)
