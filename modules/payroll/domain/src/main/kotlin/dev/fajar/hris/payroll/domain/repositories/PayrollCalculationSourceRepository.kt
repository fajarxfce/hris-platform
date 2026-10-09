package dev.fajar.hris.payroll.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import java.time.*
import java.util.UUID

/** Payroll read projections; originating modules retain write ownership. */
interface PayrollCalculationSourceRepository {
    fun workPeriod(company: UUID, month: YearMonth, lock: Boolean): Result<PayrollWorkPeriod?>

    fun targets(company: UUID, period: UUID, month: YearMonth): Result<List<PayrollRunTarget>>

    fun pendingLeave(company: UUID, period: UUID, month: YearMonth): Result<Boolean>

    fun workDays(
        company: UUID,
        job: UUID,
        employee: UUID,
        month: YearMonth,
    ): Result<List<PayrollWorkDay>?>

    fun leaveDays(company: UUID, employee: UUID, month: YearMonth): Result<List<PayrollLeaveDay>>
}
