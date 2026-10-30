package dev.fajar.hris.approvals.delivery.mappers

import dev.fajar.hris.approvals.delivery.responses.*
import dev.fajar.hris.approvals.domain.entities.ApprovalHistory
import dev.fajar.hris.core.domain.Page

fun ApprovalHistory.toResponse() =
    ApprovalHistoryResponse(
        request.toResponse(),
        decisions.map {
            ApprovalDecisionEntryResponse(
                it.step,
                it.actorId,
                it.decidingFor,
                it.decision.name,
                it.reason,
                it.decidedAt,
            )
        },
        Page(
            reassignments.items.map {
                ApprovalReassignmentEntryResponse(
                    it.revision,
                    it.step,
                    it.assignees,
                    it.actorId,
                    it.reason,
                    it.recordedAt,
                )
            },
            reassignments.nextCursor,
        ),
    )
