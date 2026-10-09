package dev.fajar.hris.administration.domain.repositories

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.util.UUID

interface CompanyClientPolicyRepository {
    fun lock(companyId: UUID, shared: Boolean = false): Result<Unit>

    fun find(companyId: UUID, revision: Long? = null): Result<CompanyClientPolicyRevision?>

    fun effective(companyId: UUID, at: Instant): Result<EffectiveClientPolicy>

    fun save(
        companyId: UUID,
        revision: CompanyClientPolicyRevision,
        expectedVersion: Long?,
    ): Result<MutationReceipt>
}
