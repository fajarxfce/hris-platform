package dev.fajar.hris.people.data.datasources

import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

interface PeopleDataSource {
    fun lock(companyId: UUID)

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
