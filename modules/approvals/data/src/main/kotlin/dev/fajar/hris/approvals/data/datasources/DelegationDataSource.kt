package dev.fajar.hris.approvals.data.datasources

import dev.fajar.hris.schema.tables.records.ApprovalDelegationsRecord
import java.time.OffsetDateTime
import java.util.UUID

interface DelegationDataSource {
    fun find(companyId: UUID, id: UUID): ApprovalDelegationsRecord?

    fun forAccount(
        companyId: UUID,
        accountId: UUID,
        at: OffsetDateTime,
    ): List<ApprovalDelegationsRecord>

    fun insert(row: ApprovalDelegationsRecord)

    fun update(row: ApprovalDelegationsRecord, expectedVersion: Long): Long?
}
