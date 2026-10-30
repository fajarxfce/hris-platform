package dev.fajar.hris.communications.data.datasources

import dev.fajar.hris.communications.data.models.*
import java.util.UUID

interface AnnouncementDeliveryDataSource {
    fun counts(companyId: UUID, announcementId: UUID): AnnouncementDeliveryRow

    fun emailCounts(companyId: UUID, announcementId: UUID): AnnouncementEmailDeliveryRow
}
