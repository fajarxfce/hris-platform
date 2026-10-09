package dev.fajar.hris.communications.domain.repositories

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.util.UUID

interface InboxRepository {
    fun find(companyId: UUID, accountId: UUID, id: UUID, lock: Boolean = false): Result<InboxItem?>

    fun list(companyId: UUID, accountId: UUID, after: UUID?, limit: Int): Result<Page<InboxSummary>>

    fun saveReadState(
        companyId: UUID,
        accountId: UUID,
        id: UUID,
        expectedVersion: Long,
        readAt: Instant?,
        acknowledgedAt: Instant?,
    ): Result<MutationReceipt?>

    fun withdraw(companyId: UUID, announcementId: UUID): Result<Int>
}
