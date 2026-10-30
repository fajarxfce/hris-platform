package dev.fajar.hris.approvals.domain.repositories

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.util.UUID

interface ApprovalHistoryRepository {
    fun decisions(companyId: UUID, id: UUID, limit: Int): Result<List<ApprovalDecisionEntry>>

    fun reassignments(
        companyId: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): Result<List<ApprovalReassignmentEntry>>
}
