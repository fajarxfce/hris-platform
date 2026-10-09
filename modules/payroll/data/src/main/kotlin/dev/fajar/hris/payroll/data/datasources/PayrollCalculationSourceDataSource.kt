package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.models.*
import java.time.*
import java.util.UUID

interface PayrollCalculationSourceDataSource {
    fun workPeriod(
        company: UUID,
        month: LocalDate,
        lock: Boolean,
    ): dev.fajar.hris.schema.tables.records.WorkPeriodsRecord?

    fun targets(company: UUID, period: UUID, month: LocalDate): List<PayrollRunTargetRow>

    fun pendingLeave(company: UUID, period: UUID, month: LocalDate): Boolean

    fun workSnapshot(company: UUID, job: UUID, employee: UUID): String?

    fun leaveDays(
        company: UUID,
        employee: UUID,
        from: LocalDate,
        until: LocalDate,
    ): List<PayrollLeaveDayRow>
}
