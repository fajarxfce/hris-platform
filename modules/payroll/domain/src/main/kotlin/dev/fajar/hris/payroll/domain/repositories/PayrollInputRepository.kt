package dev.fajar.hris.payroll.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import java.time.YearMonth
import java.util.UUID

interface PayrollInputRepository {
    fun find(company: UUID, employee: UUID, month: YearMonth): Result<PayrollInput?>

    fun revision(
        company: UUID,
        employee: UUID,
        month: YearMonth,
        revision: Long,
    ): Result<PayrollInput?>

    fun history(
        company: UUID,
        employee: UUID,
        month: YearMonth,
        after: Long?,
        limit: Int,
    ): Result<Page<PayrollInputSummary>>

    fun list(
        company: UUID,
        month: YearMonth,
        status: PayrollInputStatus?,
        after: UUID?,
        limit: Int,
    ): Result<Page<PayrollInputSummary>>

    fun authors(company: UUID, id: UUID): Result<Set<UUID>>

    fun save(company: UUID, input: PayrollInput, expectedVersion: Long?): Result<MutationReceipt>
}
