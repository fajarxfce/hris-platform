package dev.fajar.hris.approvals.data.datasources

import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.util.UUID

interface ApprovalPolicyDataSource {
    fun count(companyId: UUID, kind: String, activeOnly: Boolean, exceptId: UUID): Int

    fun list(
        companyId: UUID,
        kind: String,
        asOf: LocalDate,
        activeOnly: Boolean,
        after: UUID?,
        limit: Int,
    ): List<TemplateRow>

    fun find(companyId: UUID, id: UUID): TemplateRow?

    fun insert(row: ApprovalTemplatesRecord)

    fun update(row: ApprovalTemplatesRecord, expectedVersion: Long): Long?

    fun appendRevision(row: ApprovalTemplateRevisionsRecord)
}
