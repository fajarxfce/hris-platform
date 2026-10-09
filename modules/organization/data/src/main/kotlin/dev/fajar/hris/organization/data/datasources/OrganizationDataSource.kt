package dev.fajar.hris.organization.data.datasources

import dev.fajar.hris.schema.tables.records.OrganizationUnitsRecord
import java.util.UUID

interface OrganizationDataSource {
    fun lock(companyId: UUID, shared: Boolean = false)

    fun find(companyId: UUID, id: UUID): OrganizationUnitsRecord?

    fun ancestors(companyId: UUID, parentId: UUID): List<OrganizationUnitsRecord>

    fun list(
        companyId: UUID,
        kind: String?,
        after: String?,
        limit: Int,
    ): List<OrganizationUnitsRecord>

    fun insert(row: OrganizationUnitsRecord): OrganizationUnitsRecord

    fun update(row: OrganizationUnitsRecord, expectedVersion: Long): OrganizationUnitsRecord?
}
