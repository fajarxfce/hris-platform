package dev.fajar.hris.approvals.domain.entities

import java.time.Instant
import java.util.UUID

data class ApprovalRequest(
    val id: UUID,
    val kind: ApprovalKind,
    val resourceId: UUID,
    val authorId: UUID,
    val requesterId: UUID?,
    val templateId: UUID,
    val templateRevision: Long,
    val stages: List<ApprovalStage>,
    val currentStep: Int,
    val status: ApprovalStatus,
    val version: Long,
    val submittedAt: Instant,
    val excludedAccountIds: Set<UUID> = emptySet(),
)
