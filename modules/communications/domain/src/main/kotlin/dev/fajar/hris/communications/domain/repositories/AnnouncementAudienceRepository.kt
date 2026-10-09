package dev.fajar.hris.communications.domain.repositories

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.util.UUID

interface AnnouncementAudienceRepository {
    /**
     * Distinct accounts with a deterministic representative employment, bounded before
     * materialization.
     */
    fun recipients(
        companyId: UUID,
        selection: AnnouncementRecipientSelection,
        limit: Int,
    ): Result<List<AnnouncementRecipient>>
}
