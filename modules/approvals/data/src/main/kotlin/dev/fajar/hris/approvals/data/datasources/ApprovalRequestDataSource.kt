package dev.fajar.hris.approvals.data.datasources

import dev.fajar.hris.schema.tables.records.*
import java.time.OffsetDateTime
import java.util.UUID

interface ApprovalRequestDataSource {
    fun insert(row: ApprovalRequestsRecord)

    fun find(companyId: UUID, id: UUID): ApprovalRequestsRecord?

    fun inbox(
        companyId: UUID,
        accountId: UUID,
        includeBlocked: Boolean,
        at: OffsetDateTime,
        after: UUID?,
        limit: Int,
    ): List<ApprovalRequestsRecord>

    fun latestAssignments(companyId: UUID, ids: Set<UUID>): List<ApprovalAssignmentOverridesRecord>

    fun update(companyId: UUID, id: UUID, version: Long, status: String, step: Int?): Long?

    fun appendDecision(row: ApprovalDecisionsRecord)

    fun appendAssignment(row: ApprovalAssignmentOverridesRecord)
}
