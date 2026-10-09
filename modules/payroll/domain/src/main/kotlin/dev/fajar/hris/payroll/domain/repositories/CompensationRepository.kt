package dev.fajar.hris.payroll.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import java.time.YearMonth
import java.util.UUID

interface CompensationRepository {
    fun current(companyId: UUID, employeeId: UUID): Result<EmployeeCompensation?>

    fun effective(
        companyId: UUID,
        employeeId: UUID,
        month: YearMonth,
    ): Result<EmployeeCompensation?>

    fun list(
        companyId: UUID,
        month: YearMonth,
        after: UUID?,
        limit: Int,
    ): Result<Page<EmployeeCompensation>>

    fun history(
        companyId: UUID,
        employeeId: UUID,
        after: Long?,
        limit: Int,
    ): Result<Page<CompensationRevision>>

    fun save(
        actor: Actor,
        compensation: EmployeeCompensation,
        expectedVersion: Long?,
        reason: String,
    ): Result<MutationReceipt>
}
