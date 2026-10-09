package dev.fajar.hris.communications.data.datasources

import dev.fajar.hris.communications.data.models.*
import java.time.Instant
import java.util.UUID

interface InboxDataSource {
    fun find(companyId: UUID, accountId: UUID, id: UUID, lock: Boolean): InboxItemRow?

    fun list(companyId: UUID, accountId: UUID, after: UUID?, limit: Int): List<InboxSummaryRow>

    fun updateReadState(
        companyId: UUID,
        accountId: UUID,
        id: UUID,
        expectedVersion: Long,
        readAt: Instant?,
        acknowledgedAt: Instant?,
    ): Boolean

    fun withdraw(companyId: UUID, announcementId: UUID): Int
}
