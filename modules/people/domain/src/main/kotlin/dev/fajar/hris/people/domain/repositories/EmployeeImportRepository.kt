package dev.fajar.hris.people.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.*
import java.util.UUID

interface EmployeeImportRepository {
    fun create(
        actor: Actor,
        batch: EmployeeImport,
        rows: List<EmployeeImportRow>,
    ): Result<MutationReceipt>

    fun find(companyId: UUID, id: UUID, lock: Boolean = false): Result<EmployeeImport?>

    fun forJob(companyId: UUID, jobId: UUID, lock: Boolean = false): Result<EmployeeImport?>

    fun list(companyId: UUID, after: UUID?, limit: Int): Result<Page<EmployeeImport>>

    fun rows(companyId: UUID, id: UUID, after: Int?, limit: Int): Result<Page<EmployeeImportRow>>

    fun nextRow(
        companyId: UUID,
        id: UUID,
        status: EmployeeImportRowStatus,
        after: Int,
    ): Result<EmployeeImportRow?>

    fun counts(companyId: UUID, id: UUID): Result<Map<EmployeeImportRowStatus, Int>>

    fun recordOutcome(
        companyId: UUID,
        id: UUID,
        rowNumber: Int,
        expectedStatus: EmployeeImportRowStatus,
        status: EmployeeImportRowStatus,
        issues: Map<String, String>,
        employeeId: UUID? = null,
    ): Result<Unit>

    fun transition(
        actor: Actor,
        batch: EmployeeImport,
        status: EmployeeImportStatus,
        attempt: EmployeeImportAttempt? = null,
    ): Result<MutationReceipt>

    fun attempts(
        companyId: UUID,
        id: UUID,
        after: UUID?,
        limit: Int,
    ): Result<Page<EmployeeImportAttempt>>
}
