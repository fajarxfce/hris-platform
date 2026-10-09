package dev.fajar.hris.communications.domain.repositories

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.util.UUID

interface AnnouncementRepository {
    fun lock(companyId: UUID, shared: Boolean = false): Result<Unit>

    fun forJob(companyId: UUID, jobId: UUID): Result<Announcement?>

    fun count(companyId: UUID): Result<Int>

    fun find(companyId: UUID, id: UUID, revision: Long? = null): Result<Announcement?>

    fun list(companyId: UUID, after: UUID?, limit: Int): Result<Page<AnnouncementSummary>>

    fun history(
        companyId: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): Result<Page<AnnouncementSummary>>

    fun save(
        companyId: UUID,
        snapshot: Announcement,
        expectedVersion: Long?,
    ): Result<MutationReceipt>
}
