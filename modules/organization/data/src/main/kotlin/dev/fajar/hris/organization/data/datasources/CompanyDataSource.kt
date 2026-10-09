package dev.fajar.hris.organization.data.datasources

import dev.fajar.hris.schema.tables.records.CompaniesRecord
import java.util.UUID

interface CompanyDataSource {
    fun lock(id: UUID, shared: Boolean = false)

    fun find(id: UUID): CompaniesRecord?

    fun insert(id: UUID, code: String, name: String, timezone: String): CompaniesRecord

    fun update(
        id: UUID,
        code: String,
        name: String,
        timezone: String,
        version: Long,
    ): CompaniesRecord?
}
