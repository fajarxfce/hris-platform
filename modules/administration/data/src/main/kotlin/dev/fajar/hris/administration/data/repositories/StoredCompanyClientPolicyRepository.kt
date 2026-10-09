package dev.fajar.hris.administration.data.repositories

import dev.fajar.hris.administration.data.datasources.CompanyClientPolicyDataSource
import dev.fajar.hris.administration.data.mappers.*
import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.util.UUID

class StoredCompanyClientPolicyRepository(private val source: CompanyClientPolicyDataSource) :
    CompanyClientPolicyRepository {
    override fun lock(companyId: UUID, shared: Boolean) = safeDatabaseCall {
        source.lock(companyId, shared)
    }

    override fun find(companyId: UUID, revision: Long?) = safeDatabaseCall {
        source.find(companyId, revision)?.toClientPolicyRevision()
    }

    override fun effective(companyId: UUID, at: Instant) = safeDatabaseCall {
        val current = source.findEffective(companyId, at.atOffset(UTC))?.toClientPolicyRevision()
        EffectiveClientPolicy(
            current,
            source.nextActivation(companyId, current?.version ?: -1L, at.atOffset(UTC))?.toInstant(),
        )
    }

    override fun save(
        companyId: UUID,
        revision: CompanyClientPolicyRevision,
        expectedVersion: Long?,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                if (expectedVersion == null) source.createHead(companyId)
                else if (!source.advanceHead(companyId, expectedVersion))
                    return@safeDatabaseCall null
                source.insertRevision(revision.toRecord(companyId))
                MutationReceipt(companyId, revision.version)
            }
            .requireCurrentVersion()
}
