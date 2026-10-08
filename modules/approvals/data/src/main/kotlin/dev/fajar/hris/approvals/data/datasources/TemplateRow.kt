package dev.fajar.hris.approvals.data.datasources

import dev.fajar.hris.schema.tables.records.ApprovalTemplateRevisionsRecord
import dev.fajar.hris.schema.tables.records.ApprovalTemplatesRecord

data class TemplateRow(
    val template: ApprovalTemplatesRecord,
    val revision: ApprovalTemplateRevisionsRecord,
)
