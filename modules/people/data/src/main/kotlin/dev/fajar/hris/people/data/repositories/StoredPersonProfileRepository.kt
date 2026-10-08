package dev.fajar.hris.people.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.data.datasources.PersonProfileDataSource
import dev.fajar.hris.people.data.mappers.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.repositories.PersonProfileRepository
import java.util.UUID

class StoredPersonProfileRepository(private val source: PersonProfileDataSource) :
    PersonProfileRepository {
    override fun bindAccount(
        actor: Actor,
        personId: UUID,
        accountId: UUID,
        expectedVersion: Long,
        reason: String,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                source.insertAccountLink(
                    dev.fajar.hris.schema.tables.records.PersonAccountLinksRecord().also {
                        it.personId = personId
                        it.companyId = requireNotNull(actor.companyId)
                        it.accountId = accountId
                        it.profileVersion = expectedVersion + 1
                        it.actorId = actor.accountId
                        it.reason = reason
                    }
                )
                source
                    .bindAccount(
                        requireNotNull(actor.companyId),
                        personId,
                        accountId,
                        expectedVersion,
                    )
                    ?.toManagedProfile()
            }
            .flatMap { updated ->
                if (updated == null) Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
                else
                    safeDatabaseCall {
                        source.insertRevision(
                            updated.profile.toProfileRevision(
                                updated.ownerCompanyId,
                                updated.version,
                                actor.accountId,
                                reason,
                            )
                        )
                        MutationReceipt(personId, updated.version)
                    }
            }

    override fun findForEmployee(companyId: UUID, employeeId: UUID): Result<ManagedPersonProfile?> =
        safeDatabaseCall {
            source.findForEmployee(companyId, employeeId)?.toManagedProfile()
        }

    override fun history(
        personId: UUID,
        after: Long?,
        limit: Int,
    ): Result<Page<PersonProfileRevision>> = safeDatabaseCall {
        val rows = source.history(personId, after, limit + 1)
        Page(
            rows.take(limit).map { it.toProfileRevision() },
            if (rows.size > limit) rows[limit - 1].revision.toString() else null,
        )
    }

    override fun save(
        actor: Actor,
        profile: PersonProfile,
        expectedVersion: Long,
        reason: String,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                source
                    .update(profile.toRow(requireNotNull(actor.companyId)), expectedVersion)
                    ?.toManagedProfile()
            }
            .flatMap { updated ->
                if (updated == null) Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
                else
                    safeDatabaseCall {
                        source.insertRevision(
                            updated.profile.toProfileRevision(
                                updated.ownerCompanyId,
                                updated.version,
                                actor.accountId,
                                reason,
                            )
                        )
                        MutationReceipt(updated.profile.id, updated.version)
                    }
            }
}
