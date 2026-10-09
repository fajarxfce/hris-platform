package dev.fajar.hris.communications.domain.repositories

import dev.fajar.hris.communications.domain.entities.AnnouncementPublication
import dev.fajar.hris.core.domain.Result
import java.util.UUID

interface AnnouncementPublicationRepository {
    /**
     * Stores the immutable publication and its complete inbox in the caller's fenced transaction.
     */
    fun publish(companyId: UUID, publication: AnnouncementPublication): Result<Unit>
}
