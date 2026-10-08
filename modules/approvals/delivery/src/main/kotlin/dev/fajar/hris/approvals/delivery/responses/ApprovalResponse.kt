package dev.fajar.hris.approvals.delivery.responses

import java.time.Instant
import java.util.UUID

data class ApprovalResponse(
    val id: UUID,
    val kind: String,
    val resourceId: UUID,
    val authorId: UUID,
    val requesterId: UUID?,
    val templateId: UUID,
    val templateRevision: Long,
    val stages: List<ApprovalStageResponse>,
    val currentStep: Int,
    val status: String,
    val version: Long,
    val submittedAt: Instant,
)
