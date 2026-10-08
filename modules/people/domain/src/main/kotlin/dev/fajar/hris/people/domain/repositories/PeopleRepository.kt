package dev.fajar.hris.people.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

interface PeopleRepository {
    fun currentVersion(companyId: UUID, id: UUID): Result<Long?>

    fun findRevision(companyId: UUID, id: UUID, revision: Long): Result<EmploymentRevision?>

    fun hasRevisionsAfter(companyId: UUID, id: UUID, date: LocalDate): Result<Boolean>

    fun cancelRevision(
        actor: Actor,
        id: UUID,
        expectedVersion: Long,
        revision: Long,
        reason: String,
    ): Result<MutationReceipt>

    fun employeeIds(companyId: UUID, limit: Int): Result<List<UUID>>

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

    fun effectiveRevisions(
        companyId: UUID,
        id: UUID,
        from: LocalDate,
        until: LocalDate,
    ): Result<List<EmploymentRevision>>

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
