package dev.fajar.hris.people.data.datasources

import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

interface PeopleDataSource {
    fun employeeNumberExists(companyId: UUID, number: String): Boolean

    fun hasOpenEmploymentAtOrAfter(
        companyId: UUID,
        personId: UUID,
        exceptId: UUID?,
        from: LocalDate,
    ): Boolean

    fun hasReportingDependentsAtOrAfter(companyId: UUID, id: UUID, from: LocalDate): Boolean

    fun currentVersion(companyId: UUID, id: UUID): Long?

    fun findRevision(companyId: UUID, id: UUID, revision: Long): EmploymentRevisionsRecord?

    fun hasRevisionsAfter(companyId: UUID, id: UUID, date: LocalDate): Boolean

    fun cancellations(
        companyId: UUID,
        id: UUID,
        revisions: Set<Long>,
    ): List<EmploymentRevisionCancellationsRecord>

    fun insertCancellation(record: EmploymentRevisionCancellationsRecord)

    fun employeeIds(companyId: UUID, limit: Int): List<UUID>

    fun existingEmployeeIds(companyId: UUID, ids: Set<UUID>): List<UUID>

    fun employeeIdsForAccount(companyId: UUID, accountId: UUID, limit: Int): List<UUID>

    fun lock(companyId: UUID, shared: Boolean = false)

    fun accountForEmployee(companyId: UUID, employeeId: UUID): UUID?

    fun find(companyId: UUID, id: UUID, asOf: LocalDate): EmployeesAtRecord?

    fun findAtInstant(companyId: UUID, id: UUID, at: OffsetDateTime): EmployeesAtRecord?

    fun list(
        companyId: UUID,
        accountId: UUID,
        visibility: String,
        asOf: LocalDate,
        accessAt: OffsetDateTime,
        query: String,
        after: String?,
        limit: Int,
    ): List<EmployeesAtRecord>

    fun effectiveRevisions(
        companyId: UUID,
        id: UUID,
        from: LocalDate,
        until: LocalDate,
    ): List<EmploymentRevisionsRecord>

    fun history(
        companyId: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): List<EmploymentRevisionsRecord>

    fun reportingHistory(
        companyId: UUID,
        employeeId: UUID,
        managerId: UUID?,
    ): List<EmploymentRevisionsRecord>

    fun insertPerson(row: PersonsRecord)

    fun insertEmployment(row: EmploymentsRecord)

    fun insertRevision(row: EmploymentRevisionsRecord)

    fun advanceVersion(companyId: UUID, id: UUID, expectedVersion: Long): Long?
}
