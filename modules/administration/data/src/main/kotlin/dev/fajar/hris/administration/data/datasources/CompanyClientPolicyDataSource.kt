package dev.fajar.hris.administration.data.datasources

import dev.fajar.hris.schema.tables.records.CompanyClientPolicyRevisionsRecord
import java.time.OffsetDateTime
import java.util.UUID

interface CompanyClientPolicyDataSource {
    fun lock(companyId: UUID, shared: Boolean)

    fun find(companyId: UUID, version: Long?): CompanyClientPolicyRevisionsRecord?

    fun findEffective(companyId: UUID, at: OffsetDateTime): CompanyClientPolicyRevisionsRecord?

    fun nextActivation(companyId: UUID, afterVersion: Long, after: OffsetDateTime): OffsetDateTime?

    fun createHead(companyId: UUID)

    fun advanceHead(companyId: UUID, expectedVersion: Long): Boolean

    fun insertRevision(row: CompanyClientPolicyRevisionsRecord)
}
