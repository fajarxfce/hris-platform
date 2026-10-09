package dev.fajar.hris.communications.data.repositories

import dev.fajar.hris.communications.data.datasources.AnnouncementAudienceDataSource
import dev.fajar.hris.communications.data.mappers.*
import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.communications.domain.repositories.AnnouncementAudienceRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Result
import java.util.UUID

class StoredAnnouncementAudienceRepository(private val source: AnnouncementAudienceDataSource) :
    AnnouncementAudienceRepository {
    override fun recipients(
        companyId: UUID,
        selection: AnnouncementRecipientSelection,
        limit: Int,
    ): Result<List<AnnouncementRecipient>> = safeDatabaseCall {
        require(limit in 1..5001)
        source.recipients(selection.toQuery(companyId), limit).map { it.toRecipient() }
    }
}
