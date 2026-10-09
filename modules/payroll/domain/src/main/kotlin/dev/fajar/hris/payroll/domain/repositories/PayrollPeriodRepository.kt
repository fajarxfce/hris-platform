package dev.fajar.hris.payroll.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import java.time.*
import java.util.UUID

interface PayrollPeriodRepository {
    fun transition(
        company: UUID,
        period: PayrollPeriod,
        status: PayrollPeriodStatus,
        runId: UUID?,
        actor: UUID,
        at: Instant,
        reason: String,
    ): Result<MutationReceipt>

    fun find(company: UUID, id: UUID): Result<PayrollPeriod?>

    fun active(company: UUID, month: YearMonth): Result<Boolean>

    fun count(company: UUID, month: YearMonth): Result<Int>

    fun create(
        company: UUID,
        period: PayrollPeriod,
        employees: List<UUID>,
        reason: String,
    ): Result<MutationReceipt>

    fun cancel(
        company: UUID,
        period: PayrollPeriod,
        actor: UUID,
        at: Instant,
        reason: String,
    ): Result<MutationReceipt>

    fun members(
        company: UUID,
        id: UUID,
        after: UUID?,
        limit: Int,
    ): Result<Page<PayrollPeriodMember>>

    fun history(
        company: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): Result<Page<PayrollPeriodChange>>

    fun list(
        company: UUID,
        from: YearMonth,
        until: YearMonth,
        status: PayrollPeriodStatus?,
        after: UUID?,
        limit: Int,
    ): Result<Page<PayrollPeriod>>
}
