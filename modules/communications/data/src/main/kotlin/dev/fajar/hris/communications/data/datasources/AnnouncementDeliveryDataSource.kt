package dev.fajar.hris.communications.data.datasources

import dev.fajar.hris.communications.data.models.AnnouncementDeliveryRow
import java.util.UUID

interface AnnouncementDeliveryDataSource {
    fun counts(companyId: UUID, announcementId: UUID): AnnouncementDeliveryRow
}
