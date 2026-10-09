package dev.fajar.hris.communications.data.datasources

import dev.fajar.hris.communications.data.models.AnnouncementSummaryRow
import dev.fajar.hris.schema.tables.records.AnnouncementRevisionsRecord
import java.util.UUID

interface AnnouncementDataSource {
    fun lock(companyId: UUID, shared: Boolean)

    fun forJob(companyId: UUID, jobId: UUID): AnnouncementRevisionsRecord?

    fun count(companyId: UUID): Int

    fun find(companyId: UUID, id: UUID, revision: Long?): AnnouncementRevisionsRecord?

    fun list(companyId: UUID, after: UUID?, limit: Int): List<AnnouncementSummaryRow>

    fun history(companyId: UUID, id: UUID, after: Long?, limit: Int): List<AnnouncementSummaryRow>

    fun createHead(companyId: UUID, id: UUID)

    fun advanceHead(companyId: UUID, id: UUID, expectedVersion: Long): Boolean

    fun insertRevision(row: AnnouncementRevisionsRecord)
}
