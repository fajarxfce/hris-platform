package dev.fajar.hris.communications.data.datasources

import dev.fajar.hris.communications.data.models.AudienceGroupSummaryRow
import dev.fajar.hris.schema.tables.records.AudienceGroupRevisionsRecord
import java.util.UUID

interface AudienceGroupDataSource {
    fun lock(companyId: UUID, shared: Boolean)

    fun count(companyId: UUID): Int

    fun find(companyId: UUID, id: UUID, revision: Long?): AudienceGroupRevisionsRecord?

    fun references(companyId: UUID, ids: Set<UUID>): List<AudienceGroupSummaryRow>

    fun list(companyId: UUID, after: UUID?, limit: Int): List<AudienceGroupSummaryRow>

    fun createHead(companyId: UUID, id: UUID)

    fun advanceHead(companyId: UUID, id: UUID, expectedVersion: Long): Boolean

    fun insertRevision(row: AudienceGroupRevisionsRecord)
}
