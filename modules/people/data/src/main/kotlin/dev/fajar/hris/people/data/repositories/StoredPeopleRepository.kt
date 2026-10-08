package dev.fajar.hris.people.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.data.datasources.PeopleDataSource
import dev.fajar.hris.people.data.mappers.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.schema.tables.records.EmploymentsRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class StoredPeopleRepository(private val source: PeopleDataSource) : PeopleRepository {
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

    override fun history(
        companyId: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): Result<Page<EmploymentRevision>> = safeDatabaseCall {
        val rows = source.history(companyId, id, after, limit + 1)
        Page(
            rows.take(limit).map { it.toRevision() },
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
