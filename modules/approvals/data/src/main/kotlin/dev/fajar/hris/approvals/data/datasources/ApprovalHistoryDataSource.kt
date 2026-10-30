package dev.fajar.hris.approvals.data.datasources

import dev.fajar.hris.schema.tables.records.*
import java.util.UUID

interface ApprovalHistoryDataSource {
    fun decisions(companyId: UUID, id: UUID, limit: Int): List<ApprovalDecisionsRecord>

    fun reassignments(
        companyId: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): List<ApprovalAssignmentOverridesRecord>
}
