package dev.fajar.hris.people.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.data.datasources.PeopleDataSource
import dev.fajar.hris.people.data.datasources.PersonProfileDataSource
import dev.fajar.hris.people.data.mappers.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.schema.tables.records.EmploymentRevisionCancellationsRecord
import dev.fajar.hris.schema.tables.records.EmploymentsRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class StoredPeopleRepository(
    private val source: PeopleDataSource,
    private val profiles: PersonProfileDataSource,
) : PeopleRepository {
    override fun currentVersion(companyId: UUID, id: UUID): Result<Long?> = safeDatabaseCall {
        source.currentVersion(companyId, id)
    }

    override fun findRevision(
        companyId: UUID,
        id: UUID,
        revision: Long,
    ): Result<EmploymentRevision?> = safeDatabaseCall {
        source
            .findRevision(companyId, id, revision)
            ?.toRevision(source.cancellations(companyId, id, setOf(revision)).singleOrNull())
    }

    override fun hasRevisionsAfter(companyId: UUID, id: UUID, date: LocalDate): Result<Boolean> =
        safeDatabaseCall {
            source.hasRevisionsAfter(companyId, id, date)
        }

    override fun cancelRevision(
        actor: Actor,
        id: UUID,
        expectedVersion: Long,
        revision: Long,
        reason: String,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                source.advanceVersion(requireNotNull(actor.companyId), id, expectedVersion)
            }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    source.insertCancellation(
                        EmploymentRevisionCancellationsRecord().also {
                            it.companyId = actor.companyId
                            it.employmentId = id
                            it.revision = revision
                            it.actorId = actor.accountId
                            it.reason = reason
                        }
                    )
                    MutationReceipt(id, version)
                }
            }

    override fun employeeIds(companyId: UUID, limit: Int): Result<List<UUID>> = safeDatabaseCall {
        source.employeeIds(companyId, limit)
    }

    override fun lockReportingLines(companyId: UUID): Result<Unit> = safeDatabaseCall {
        source.lock(companyId)
    }

    override fun find(companyId: UUID, id: UUID, asOf: LocalDate): Result<Employee?> =
        safeDatabaseCall {
            source.find(companyId, id, asOf)?.toEmployee()
        }

    override fun findAtInstant(companyId: UUID, id: UUID, at: Instant): Result<Employee?> =
        safeDatabaseCall {
            source.findAtInstant(companyId, id, at.atOffset(ZoneOffset.UTC))?.toEmployee()
        }

    override fun list(
        companyId: UUID,
        accountId: UUID,
        visibility: EmployeeVisibility,
        asOf: LocalDate,
        accessAt: Instant,
        query: String,
        after: String?,
        limit: Int,
    ): Result<Page<Employee>> = safeDatabaseCall {
        val rows =
            source.list(
                companyId,
                accountId,
                visibility.name,
                asOf,
                accessAt.atOffset(ZoneOffset.UTC),
                query,
                after,
                limit + 1,
            )
        Page(
            rows.take(limit).map { it.toEmployee() },
            if (rows.size > limit) rows[limit - 1].employeeNumber else null,
        )
    }

    override fun effectiveRevisions(
        companyId: UUID,
        id: UUID,
        from: LocalDate,
        until: LocalDate,
    ): Result<List<EmploymentRevision>> = safeDatabaseCall {
        source.effectiveRevisions(companyId, id, from, until).map { it.toRevision() }
    }

    override fun history(
        companyId: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): Result<Page<EmploymentRevision>> = safeDatabaseCall {
        val rows = source.history(companyId, id, after, limit + 1)
        val cancellations =
            source
                .cancellations(companyId, id, rows.take(limit).map { it.revision }.toSet())
                .associateBy { it.revision }
        Page(
            rows.take(limit).map { it.toRevision(cancellations[it.revision]) },
            if (rows.size > limit) rows[limit - 1].revision.toString() else null,
        )
    }

    override fun reportingHistory(
        companyId: UUID,
        employeeId: UUID,
        managerId: UUID?,
    ): Result<List<ReportingAssignment>> = safeDatabaseCall {
        source.reportingHistory(companyId, employeeId, managerId).map {
            ReportingAssignment(it.employmentId, it.managerId, it.effectiveFrom, it.revision)
        }
    }

    override fun create(
        actor: Actor,
        id: UUID,
        employeeNumber: String,
        person: PersonProfile,
        terms: EmploymentTerms,
        reason: String,
    ): Result<MutationReceipt> = safeDatabaseCall {
        val company = requireNotNull(actor.companyId)
        source.insertPerson(person.toRow(company))
        profiles.insertRevision(person.toProfileRevision(company, 0, actor.accountId, reason))
        source.insertEmployment(
            EmploymentsRecord().also {
                it.companyId = company
                it.id = id
                it.personId = person.id
                it.employeeNumber = employeeNumber
                it.version = 0
            }
        )
        source.insertRevision(terms.toRow(company, id, 0, actor.accountId, reason))
        MutationReceipt(id, 0)
    }

    override fun revise(
        actor: Actor,
        id: UUID,
        expectedVersion: Long,
        terms: EmploymentTerms,
        reason: String,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                source.advanceVersion(requireNotNull(actor.companyId), id, expectedVersion)
            }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    source.insertRevision(
                        terms.toRow(
                            requireNotNull(actor.companyId),
                            id,
                            version,
                            actor.accountId,
                            reason,
                        )
                    )
                    MutationReceipt(id, version)
                }
            }
}
