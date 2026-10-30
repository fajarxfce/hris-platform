package dev.fajar.hris.approvals.delivery.responses

import dev.fajar.hris.core.domain.Page
import java.time.Instant
import java.util.UUID

data class ApprovalDecisionEntryResponse(
    val step: Int,
    val actorId: UUID,
    val decidingFor: UUID,
    val decision: String,
    val reason: String,
    val decidedAt: Instant,
)

data class ApprovalReassignmentEntryResponse(
    val revision: Long,
    val step: Int,
    val assignees: Set<UUID>,
    val actorId: UUID,
    val reason: String,
    val recordedAt: Instant,
)

data class ApprovalHistoryResponse(
    val request: ApprovalResponse,
    val decisions: List<ApprovalDecisionEntryResponse>,
    val reassignments: Page<ApprovalReassignmentEntryResponse>,
)
