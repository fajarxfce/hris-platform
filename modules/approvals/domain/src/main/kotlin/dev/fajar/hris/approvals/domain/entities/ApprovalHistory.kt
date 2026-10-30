package dev.fajar.hris.approvals.domain.entities

import dev.fajar.hris.core.domain.Page
import java.time.Instant
import java.util.UUID

data class ApprovalDecisionEntry(
    val step: Int,
    val actorId: UUID,
    val decidingFor: UUID,
    val decision: ApprovalDecision,
    val reason: String,
    val decidedAt: Instant,
)

data class ApprovalReassignmentEntry(
    val revision: Long,
    val step: Int,
    val assignees: Set<UUID>,
    val actorId: UUID,
    val reason: String,
    val recordedAt: Instant,
)

data class ApprovalHistory(
    val request: ApprovalRequest,
    val decisions: List<ApprovalDecisionEntry>,
    val reassignments: Page<ApprovalReassignmentEntry>,
)
