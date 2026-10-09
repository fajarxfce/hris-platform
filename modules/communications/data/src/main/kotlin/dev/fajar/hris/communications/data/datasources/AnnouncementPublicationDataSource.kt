package dev.fajar.hris.communications.data.datasources

import dev.fajar.hris.schema.tables.records.AnnouncementPublicationsRecord
import java.util.UUID

interface AnnouncementPublicationDataSource {
    fun insertPublication(row: AnnouncementPublicationsRecord)

    fun insertInbox(companyId: UUID, publicationId: UUID): Int
}
