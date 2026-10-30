package dev.fajar.hris.approvals.data.repositories

import dev.fajar.hris.approvals.data.datasources.ApprovalHistoryDataSource
import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.repositories.ApprovalHistoryRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import java.util.UUID

class StoredApprovalHistoryRepository(private val source: ApprovalHistoryDataSource) :
    ApprovalHistoryRepository {
    override fun decisions(companyId: UUID, id: UUID, limit: Int) = safeDatabaseCall {
        source.decisions(companyId, id, limit).map { row ->
            require(row.step in 0..7 && row.reason.length <= 1000)
            ApprovalDecisionEntry(
                row.step,
                row.actorId,
                row.decidingFor,
                ApprovalDecision.valueOf(row.decision),
                row.reason,
                row.decidedAt.toInstant(),
            )
        }
    }

    override fun reassignments(companyId: UUID, id: UUID, after: Long?, limit: Int) =
        safeDatabaseCall {
            source.reassignments(companyId, id, after, limit).map { row ->
                require(
                    row.revision > 0 &&
                        row.step in 0..7 &&
                        row.assignees.size in 1..25 &&
                        row.reason.length <= 1000
                )
                val assignees = row.assignees.toSet()
                require(assignees.size == row.assignees.size)
                ApprovalReassignmentEntry(
                    row.revision,
                    row.step,
                    assignees,
                    row.actorId,
                    row.reason,
                    row.recordedAt.toInstant(),
                )
            }
        }
}
