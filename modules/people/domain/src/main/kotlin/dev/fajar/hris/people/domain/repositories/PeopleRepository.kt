package dev.fajar.hris.people.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

interface PeopleRepository {
    fun lockReportingLines(companyId: UUID): Result<Unit>

    fun find(companyId: UUID, id: UUID, asOf: LocalDate): Result<Employee?>

    fun findAtInstant(companyId: UUID, id: UUID, at: Instant): Result<Employee?>

    fun list(
        companyId: UUID,
        accountId: UUID,
        visibility: EmployeeVisibility,
        asOf: LocalDate,
        accessAt: Instant,
        query: String,
        after: String?,
        limit: Int,
    ): Result<Page<Employee>>

    fun history(
        companyId: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): Result<Page<EmploymentRevision>>

    fun reportingHistory(
        companyId: UUID,
        employeeId: UUID,
        managerId: UUID?,
    ): Result<List<ReportingAssignment>>

    fun create(
        actor: Actor,
        id: UUID,
        employeeNumber: String,
        person: PersonProfile,
        terms: EmploymentTerms,
        reason: String,
    ): Result<MutationReceipt>

    fun revise(
        actor: Actor,
        id: UUID,
        expectedVersion: Long,
        terms: EmploymentTerms,
        reason: String,
    ): Result<MutationReceipt>
}
