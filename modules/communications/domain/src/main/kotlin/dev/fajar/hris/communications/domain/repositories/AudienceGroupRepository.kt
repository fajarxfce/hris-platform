package dev.fajar.hris.communications.domain.repositories

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.util.UUID

interface AudienceGroupRepository {
    fun lock(companyId: UUID, shared: Boolean = false): Result<Unit>

    fun count(companyId: UUID): Result<Int>

    fun find(companyId: UUID, id: UUID, revision: Long? = null): Result<AudienceGroup?>

    fun references(companyId: UUID, ids: Set<UUID>): Result<List<AudienceGroupSummary>>

    fun list(companyId: UUID, after: UUID?, limit: Int): Result<Page<AudienceGroupSummary>>

    fun save(
        companyId: UUID,
        snapshot: AudienceGroup,
        expectedVersion: Long?,
    ): Result<MutationReceipt>
}
