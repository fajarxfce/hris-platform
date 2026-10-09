package dev.fajar.hris.communications.data.repositories

import dev.fajar.hris.communications.data.datasources.AnnouncementPublicationDataSource
import dev.fajar.hris.communications.data.mappers.toRecord
import dev.fajar.hris.communications.domain.entities.AnnouncementPublication
import dev.fajar.hris.communications.domain.repositories.AnnouncementPublicationRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Result
import java.util.UUID
import tools.jackson.databind.ObjectMapper

class StoredAnnouncementPublicationRepository(
    private val source: AnnouncementPublicationDataSource,
    private val json: ObjectMapper,
) : AnnouncementPublicationRepository {
    override fun publish(companyId: UUID, publication: AnnouncementPublication): Result<Unit> =
        safeDatabaseCall {
            source.insertPublication(publication.toRecord(companyId, json))
            check(source.insertInbox(companyId, publication.id) == publication.recipients.size)
        }
}
