package dev.fajar.hris.communications.domain.repositories

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.util.UUID

interface AnnouncementDeliveryRepository {
    fun counts(companyId: UUID, announcementId: UUID): Result<AnnouncementDeliveryCounts>

    fun emailCounts(companyId: UUID, announcementId: UUID): Result<AnnouncementEmailDeliveryCounts>
}
