package dev.fajar.hris.payroll.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import java.util.UUID

interface PayrollAmendmentRepository {
    fun summary(company: UUID, id: UUID): Result<PayrollAmendmentSummary?>

    fun find(company: UUID, id: UUID): Result<PayrollAmendment?>

    /** At most 201 distinct actors; callers reject the sentinel beyond the approval bound. */
    fun preparers(company: UUID, id: UUID): Result<Set<UUID>>

    fun revision(company: UUID, id: UUID, version: Long): Result<PayrollAmendment?>

    fun history(
        company: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): Result<Page<PayrollAmendmentSummary>>

    fun list(
        company: UUID,
        year: Int,
        employeeId: UUID?,
        status: PayrollAmendmentStatus?,
        query: String,
        excludedAccountId: UUID,
        after: UUID?,
        limit: Int,
    ): Result<Page<PayrollAmendmentSummary>>

    fun save(
        company: UUID,
        amendment: PayrollAmendment,
        expectedVersion: Long?,
    ): Result<MutationReceipt>

    fun calculationSummary(
        company: UUID,
        amendmentId: UUID,
        calculationId: UUID,
    ): Result<PayrollAmendmentCalculationSummary?>

    fun calculation(
        company: UUID,
        amendmentId: UUID,
        calculationId: UUID,
    ): Result<PayrollAmendmentCalculationAttempt?>

    fun recordCalculation(company: UUID, attempt: PayrollAmendmentCalculationAttempt): Result<Unit>
}
