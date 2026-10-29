package dev.fajar.hris.communications.data.repositories

import dev.fajar.hris.communications.data.datasources.AnnouncementDeliveryDataSource
import dev.fajar.hris.communications.domain.entities.AnnouncementDeliveryCounts
import dev.fajar.hris.communications.domain.repositories.AnnouncementDeliveryRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import java.util.UUID

class StoredAnnouncementDeliveryRepository(private val source: AnnouncementDeliveryDataSource) :
    AnnouncementDeliveryRepository {
    override fun counts(companyId: UUID, announcementId: UUID) = safeDatabaseCall {
        val row = source.counts(companyId, announcementId)
        val states = listOf(row.pending, row.processing, row.completed, row.failed, row.superseded)
        require(states.all { it in 0..5000 } && states.sum() <= 5000)
        val processedLimit = states.sum() * 500
        require(
            row.processed in 0..processedLimit &&
                row.accepted in 0..row.processed &&
                row.rejected in 0..row.processed
        )
        require(row.accepted + row.rejected <= row.processed)
        AnnouncementDeliveryCounts(
            row.pending,
            row.processing,
            row.completed,
            row.failed,
            row.superseded,
            row.processed,
            row.accepted,
            row.rejected,
        )
    }
}
