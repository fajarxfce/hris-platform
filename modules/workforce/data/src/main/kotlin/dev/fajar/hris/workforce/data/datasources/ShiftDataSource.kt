package dev.fajar.hris.workforce.data.datasources

import dev.fajar.hris.schema.tables.records.*
import java.util.UUID

interface ShiftDataSource {
    fun find(companyId: UUID, id: UUID): ShiftTemplatesRecord?

    fun list(companyId: UUID, query: String, after: String?, limit: Int): List<ShiftTemplatesRecord>

    fun insert(row: ShiftTemplatesRecord)

    fun update(row: ShiftTemplatesRecord, expectedVersion: Long): Long?

    fun append(row: ShiftRevisionsRecord)
}
